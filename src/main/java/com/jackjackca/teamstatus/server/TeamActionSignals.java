package com.jackjackca.teamstatus.server;

import java.util.UUID;

import com.jackjackca.teamstatus.common.network.AttackActionPacket;
import com.jackjackca.teamstatus.common.network.ItemUseActionPacket;
import com.jackjackca.teamstatus.common.network.MiningActionPacket;
import com.jackjackca.teamstatus.common.state.ItemState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Edge-triggered, visibility-scoped action signals. Unlike {@link TeamStateCollector} this
 * class never polls and never carries continuous state: it forwards one packet per discrete
 * game event (mining start/stop/abort, a landed hit, item use) to every online player who
 * currently sees the actor (per the self-hosted {@link DisplayListStore}), excluding the
 * actor themselves, and sends nothing while nothing happens (lazy sync).
 */
public final class TeamActionSignals {

    private final TeamStateBroadcaster broadcaster;

    public TeamActionSignals(TeamStateBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    /**
     * Forwards a mining lifecycle edge. At START the target block and the per-tick destroy
     * rate are resolved server-side so receivers in other dimensions can render and predict
     * without having the block loaded.
     */
    public void onMiningAction(ServerPlayer player, MiningActionPacket.Action action, BlockPos pos) {
        BlockState blockState = Blocks.AIR.defaultBlockState();
        float speedPerTick = 0.0F;
        if (action == MiningActionPacket.Action.START) {
            // Creative mode destroys instantly and START fires before the creative branch;
            // unbreakable blocks produce no useful progress either.
            if (player.isCreative()) {
                return;
            }
            blockState = player.level().getBlockState(pos);
            if (blockState.isAir()) {
                return;
            }
            speedPerTick = blockState.getDestroyProgress(player, player.level(), pos);
            if (speedPerTick <= 0.0F) {
                return;
            }
        }

        dispatch(player, new MiningActionPacket(player.getUUID(), action, pos, speedPerTick, blockState));
    }

    /**
     * Forwards one landed hit dealt by a party member. {@code victim} is the damaged living
     * entity (a player or a mob); only flat identity data is sent, the HUD renders an icon.
     */
    public void onAttack(ServerPlayer attacker, LivingEntity victim) {
        AttackActionPacket packet;
        if (victim instanceof Player playerVictim) {
            packet = new AttackActionPacket(attacker.getUUID(), AttackActionPacket.TargetKind.PLAYER,
                    playerVictim.getUUID(), playerVictim.getGameProfile().getName(), null);
        } else {
            packet = new AttackActionPacket(attacker.getUUID(), AttackActionPacket.TargetKind.MOB,
                    null, null, BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()));
        }
        dispatch(attacker, packet);
    }

    /**
     * Forwards an item-use lifecycle edge (eating/drinking). START carries the used stack and
     * the total duration so receivers predict progress without equipment-snapshot ordering
     * assumptions; STOP/FINISH carry no item.
     */
    public void onItemUse(ServerPlayer player, ItemUseActionPacket.Action action,
                          ItemStack stack, int durationTicks) {
        ItemState item = action == ItemUseActionPacket.Action.START ? ItemState.of(stack) : ItemState.EMPTY;
        dispatch(player, new ItemUseActionPacket(player.getUUID(), action, item, durationTicks));
    }

    /** Sends an edge packet to every online player that sees the actor, except the actor. */
    private void dispatch(ServerPlayer actor, CustomPacketPayload packet) {
        MinecraftServer server = actor.server;
        DisplayListStore displayLists = DisplayListStore.get(server);
        for (ServerPlayer receiver : server.getPlayerList().getPlayers()) {
            if (!receiver.getUUID().equals(actor.getUUID())
                    && displayLists.isVisible(receiver.getUUID(), actor.getUUID())) {
                broadcaster.sendToPlayer(receiver, packet);
            }
        }
    }
}
