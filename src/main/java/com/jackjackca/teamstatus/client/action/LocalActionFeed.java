package com.jackjackca.teamstatus.client.action;

import com.jackjackca.teamstatus.Config;
import com.jackjackca.teamstatus.common.network.AttackActionPacket;
import com.jackjackca.teamstatus.common.network.MiningActionPacket;
import com.jackjackca.teamstatus.common.state.ItemState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Feeds the local player's own actions into {@link HandActionStates} from purely client-side
 * sources, so the optional self panel shows mining/eating/attack animations with zero
 * network traffic: the server never sends action edges to the actor.
 * <ul>
 *   <li>using: polled from the player's authoritative {@code isUsingItem} / remaining ticks;</li>
 *   <li>attack: rising edge of the arm swing while a living entity is under the crosshair
 *       (swinging at air shows nothing, matching the teammates' hit-only semantics);</li>
 *   <li>mining: the client-side {@link PlayerInteractEvent.LeftClickBlock} edges, with the
 *       block state and per-tick rate resolved from the local world.</li>
 * </ul>
 * Client main thread only.
 */
public final class LocalActionFeed {

    private static boolean wasSwinging;

    // Local mining lifecycle. The client game mode fires LeftClickBlock only on START; both
    // release (ABORT) and completion (STOP) go through stopDestroyBlock() with NO event, so
    // the end is detected by polling gameMode.isDestroying().
    private static boolean localMining;
    private static BlockPos miningPos = BlockPos.ZERO;
    private static BlockState miningState;

    // Local eat/drink lifecycle, so the transition out can distinguish FINISH (consumed,
    // plays the crumb burst) from STOP (interrupted).
    private static boolean localUsing;
    private static int localUseElapsed;
    private static int localUseTotal;

    private LocalActionFeed() {
    }

    /** Called every client tick after {@link HandActionStates#clientTick()} so authoritative
     *  local values override the generic timer advancement in the same tick. */
    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !Config.HUD_SHOW_SELF.get()) {
            wasSwinging = false;
            localMining = false;
            localUsing = false;
            return;
        }

        // ---- Mining: poll the authoritative destroying flag for the end edge ----
        if (localMining && !mc.gameMode.isDestroying()) {
            // Block still present and unchanged => released button (ABORT); otherwise the
            // client-predicted break already removed/replaced it => completed (STOP).
            BlockState now = mc.level.getBlockState(miningPos);
            MiningActionPacket.Action endAction = (!now.isAir() && now.equals(miningState))
                    ? MiningActionPacket.Action.ABORT
                    : MiningActionPacket.Action.STOP;
            HandActionStates.onMining(new MiningActionPacket(
                    player.getUUID(), endAction, miningPos, 0.0F, miningState));
            localMining = false;
        }

        // ---- Eating / drinking: authoritative local fields ----
        ItemStack useStack = player.getUseItem();
        if (player.isUsingItem() && isConsumable(useStack)) {
            int total = useStack.getUseDuration(player);
            int elapsed = total - player.getUseItemRemainingTicks();
            HandActionStates.syncLocalUsing(player.getUUID(), ItemState.of(useStack), elapsed, total);
            localUsing = true;
            localUseElapsed = elapsed;
            localUseTotal = total;
        } else {
            // Reaching the end of the duration means the item was consumed (burst); anything
            // earlier is an interrupted use (no burst).
            if (localUsing) {
                if (localUseElapsed >= localUseTotal - 1) {
                    HandActionStates.finishLocalUsing(player.getUUID());
                } else {
                    HandActionStates.stopLocalUsing(player.getUUID());
                }
            }
            localUsing = false;
        }

        // ---- Attack: swing rising edge with a living crosshair target ----
        // Match the server-authoritative semantics teammates see: ignore mining swings,
        // and require a fully charged attack (cooldown not ready deals no damage).
        boolean swinging = player.swinging;
        if (swinging && !wasSwinging && !mc.gameMode.isDestroying()
                && player.getAttackStrengthScale(0.0F) >= 1.0F
                && mc.crosshairPickEntity instanceof LivingEntity victim && victim != player) {
            AttackActionPacket packet;
            if (victim instanceof Player playerVictim) {
                packet = new AttackActionPacket(player.getUUID(), AttackActionPacket.TargetKind.PLAYER,
                        playerVictim.getUUID(), playerVictim.getGameProfile().getName(), null);
            } else {
                packet = new AttackActionPacket(player.getUUID(), AttackActionPacket.TargetKind.MOB,
                        null, null, BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()));
            }
            HandActionStates.onAttack(packet);
        }
        wasSwinging = swinging;
    }

    /** Client-side mining lifecycle edges (LocalPlayer; the server handler ignores these). */
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Minecraft mc = Minecraft.getInstance();
        if (!Config.HUD_SHOW_SELF.get() || mc.level == null
                || !(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }
        MiningActionPacket.Action action = switch (event.getAction()) {
            case START -> MiningActionPacket.Action.START;
            case STOP -> MiningActionPacket.Action.STOP;
            case ABORT -> MiningActionPacket.Action.ABORT;
            // CLIENT_HOLD is continuous mouse-held noise, not an edge.
            case CLIENT_HOLD -> null;
        };
        if (action == null) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = mc.level.getBlockState(pos);
        float speed = 0.0F;
        if (action == MiningActionPacket.Action.START) {
            // Same guards as the server signal: instant creative break, air, unbreakable.
            if (player.isCreative() || state.isAir()) {
                return;
            }
            // While holding, continueDestroyBlock re-fires START every swing cycle on the
            // same block; treat it as idempotent so the predicted progress does not reset.
            if (localMining && pos.equals(miningPos)) {
                return;
            }
            speed = state.getDestroyProgress(player, mc.level, pos);
            if (speed <= 0.0F) {
                return;
            }
            // Arm the polling watcher; the end edge never arrives as an event on the client.
            localMining = true;
            miningPos = pos.immutable();
            miningState = state;
        } else {
            // Defensive: if an end event ever does arrive, stop watching for it.
            localMining = false;
        }
        HandActionStates.onMining(new MiningActionPacket(player.getUUID(), action, pos, speed, state));
    }

    private static boolean isConsumable(ItemStack stack) {
        UseAnim anim = stack.getUseAnimation();
        return anim == UseAnim.EAT || anim == UseAnim.DRINK;
    }
}
