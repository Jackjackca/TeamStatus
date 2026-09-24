package com.jackjackca.teamstatus.client.action;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.jackjackca.teamstatus.common.network.AttackActionPacket;
import com.jackjackca.teamstatus.common.network.ItemUseActionPacket;
import com.jackjackca.teamstatus.common.network.MiningActionPacket;
import com.jackjackca.teamstatus.common.state.ItemState;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Per-team-member hand action state machine (client main thread only).
 * <p>
 * Phases {@link Phase#IDLE} / {@link Phase#MINING} are driven exclusively by mining edge
 * packets; a strike is an overlay pulse ({@code attackStartTick} + target identity + hit
 * flash) that does not change the phase. Render frames only call {@link #getView}, never
 * mutate anything. Continuous break progress stays a client prediction; only edges arrive
 * from the network.
 */
public final class HandActionStates {

    /** Ticks after predicted completion to keep waiting for an authoritative STOP. */
    private static final int COMPLETION_GRACE_TICKS = 20;
    /** Hit-flash decay per client tick (0.6 added per strike). */
    private static final float HIT_FLASH_DECAY = 0.15F;
    private static final float HIT_FLASH_PER_STRIKE = 0.6F;

    public enum Phase {
        IDLE, MINING, USING
    }

    /**
     * Mutable per-frame view consumed immediately by the visual layer. A single shared
     * instance is filled per member on request (members render sequentially and the view is
     * never retained past one member's draw), so the steady-state frame path allocates
     * nothing.
     */
    public static final class View {
        public float swingAngleDeg;
        public boolean mining;
        public BlockState blockState;
        public float progress;
        public int crackStage;
        public boolean using;
        public ItemState usingItem;
        public float usingProgress;
        /** Vertical item offset in pixels while eating/drinking (chew bob + final raise); 0 otherwise. */
        public float usingBobY;
        public boolean hitVisible;
        @Nullable public AttackActionPacket.TargetKind hitKind;
        @Nullable public UUID hitId;
        @Nullable public String hitName;
        @Nullable public ResourceLocation hitType;
        public float hitFlash;
    }

    private static final View SCRATCH_VIEW = new View();

    private static final class State {
        private Phase phase = Phase.IDLE;
        private BlockPos pos = BlockPos.ZERO;
        private BlockState blockState = Blocks.AIR.defaultBlockState();
        private float speed;
        private int elapsed;
        private int lastStage = -1;

        private int attackStartTick = -1;
        private AttackActionPacket.TargetKind hitKind;
        private UUID hitId;
        private String hitName;
        private ResourceLocation hitType;
        private float hitFlash;

        // Item use (eating/drinking) prediction.
        private ItemState usingItem = ItemState.EMPTY;
        private int usingTotal;

        private void startMining(BlockState state, BlockPos pos, float speed) {
            this.phase = Phase.MINING;
            this.blockState = state;
            this.pos = pos;
            this.speed = speed;
            this.elapsed = 0;
            this.lastStage = -1;
        }

        private void stopMining() {
            this.phase = Phase.IDLE;
            this.blockState = Blocks.AIR.defaultBlockState();
            this.speed = 0.0F;
            this.elapsed = 0;
            this.lastStage = -1;
        }

        private void startUsing(ItemState item, int totalTicks) {
            this.phase = Phase.USING;
            this.usingItem = item;
            this.usingTotal = Math.max(1, totalTicks);
            this.elapsed = 0;
        }

        private void stopUsing() {
            this.phase = Phase.IDLE;
            this.usingItem = ItemState.EMPTY;
            this.usingTotal = 0;
            this.elapsed = 0;
        }

        private void registerStrike(int now, AttackActionPacket packet) {
            this.attackStartTick = now;
            this.hitKind = packet.kind();
            this.hitId = packet.targetId();
            this.hitName = packet.targetName();
            this.hitType = packet.entityType();
            this.hitFlash = Math.min(1.0F, this.hitFlash + HIT_FLASH_PER_STRIKE);
        }

        private boolean isEmptyIdle(int now) {
            boolean strikeOver = attackStartTick < 0
                    || now - attackStartTick >= SwingAnimation.ATTACK_DURATION_TICKS;
            return phase == Phase.IDLE && strikeOver && hitFlash <= 0.0F;
        }
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static int tick;

    private HandActionStates() {
    }

    // ---- Edge inputs (packet handler, main thread) ----

    public static void onMining(MiningActionPacket packet) {
        switch (packet.action()) {
            case START -> STATES.computeIfAbsent(packet.memberId(), k -> new State())
                    .startMining(packet.blockState(), packet.pos(), packet.speedPerTick());
            case STOP -> {
                State state = STATES.get(packet.memberId());
                if (state != null && state.phase == Phase.MINING) {
                    BlockBreakParticles.INSTANCE.breakBurst(packet.memberId(), state.blockState);
                    state.stopMining();
                }
            }
            case ABORT -> {
                State state = STATES.get(packet.memberId());
                if (state != null) {
                    state.stopMining();
                }
            }
        }
    }

    public static void onAttack(AttackActionPacket packet) {
        STATES.computeIfAbsent(packet.memberId(), k -> new State()).registerStrike(tick, packet);
    }

    // ---- Local authoritative feed for the local player's own panel (no network) ----

    /**
     * Sets the local player's eat/drink progress from the player's own authoritative fields.
     * Unlike the packet-driven timer this sets the elapsed count directly, so it stays exact
     * even if a tick edge would otherwise be missed.
     */
    public static void syncLocalUsing(UUID memberId, ItemState item, int elapsed, int totalTicks) {
        State state = STATES.computeIfAbsent(memberId, k -> new State());
        if (state.phase != Phase.USING || state.usingTotal != totalTicks
                || !net.minecraft.world.item.ItemStack.matches(state.usingItem.stack(), item.stack())) {
            state.startUsing(item, totalTicks);
        }
        state.elapsed = Math.max(0, Math.min(elapsed, totalTicks));
    }

    public static void stopLocalUsing(UUID memberId) {
        State state = STATES.get(memberId);
        if (state != null && state.phase == Phase.USING) {
            state.stopUsing();
        }
    }

    /** Local use completed (item consumed): emit the finish burst like the remote FINISH edge. */
    public static void finishLocalUsing(UUID memberId) {
        State state = STATES.get(memberId);
        if (state != null && state.phase == Phase.USING) {
            UseActionParticles.INSTANCE.finishBurst(memberId, state.usingItem.stack());
            state.stopUsing();
        }
    }

    public static void onItemUse(ItemUseActionPacket packet) {
        State state = STATES.computeIfAbsent(packet.memberId(), k -> new State());
        switch (packet.action()) {
            case START -> state.startUsing(packet.item(), packet.durationTicks());
            // FINISH (consumed) plays the crumb burst; STOP (interrupted) ends silently.
            case FINISH -> {
                if (state.phase == Phase.USING) {
                    UseActionParticles.INSTANCE.finishBurst(packet.memberId(),
                            state.usingItem.stack());
                    state.stopUsing();
                }
            }
            case STOP -> {
                if (state.phase == Phase.USING) {
                    state.stopUsing();
                }
            }
        }
    }

    // ---- Tick advancement ----

    public static void clientTick() {
        tick++;
        Iterator<Map.Entry<UUID, State>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> entry = it.next();
            State state = entry.getValue();

            if (state.phase == Phase.MINING) {
                state.elapsed++;
                float progress = Math.min(1.0F, state.speed * state.elapsed);
                int stage = Math.min(9, (int) (progress * 10.0F));
                if (stage > state.lastStage) {
                    if (stage > 0) {
                        BlockBreakParticles.INSTANCE.stageChips(entry.getKey(), state.blockState);
                    }
                    state.lastStage = stage;
                }
                int predictedTicks = (int) Math.ceil(1.0F / state.speed);
                if (state.elapsed > predictedTicks + COMPLETION_GRACE_TICKS) {
                    state.stopMining();
                }
            } else if (state.phase == Phase.USING) {
                state.elapsed++;
                // One crumb per chew tick (vanilla chew period), once chewing has begun.
                if (state.elapsed % SwingAnimation.CHEW_PERIOD_TICKS == 0
                        && (float) state.elapsed / state.usingTotal > 0.2F) {
                    UseActionParticles.INSTANCE.crumb(entry.getKey(), state.usingItem.stack());
                }
                // Auto-recover if the authoritative FINISH/STOP edge was lost.
                if (state.elapsed > state.usingTotal + COMPLETION_GRACE_TICKS) {
                    state.stopUsing();
                }
            }

            if (state.hitFlash > 0.0F) {
                state.hitFlash = Math.max(0.0F, state.hitFlash - HIT_FLASH_DECAY);
            }

            if (state.isEmptyIdle(tick)) {
                it.remove();
            }
        }
        BlockBreakParticles.INSTANCE.clientTick();
        UseActionParticles.INSTANCE.clientTick();
    }

    // ---- Read-only frame view ----

    /**
     * Fills and returns the shared {@link View} for this member this frame; returns
     * {@code null} when the member has no action state at all. The returned instance is
     * reused on the next call, so consumers must not retain it.
     *
     * @param swingDurationTicks vanilla arm-swing duration for this member (Haste/Mining
     *                           Fatigue aware), see {@link SwingAnimation#swingDurationTicks}
     */
    @Nullable
    public static View getView(UUID memberId, float partialTick, int swingDurationTicks) {
        State state = STATES.get(memberId);
        if (state == null) {
            return null;
        }

        View view = SCRATCH_VIEW;
        float useAge = state.elapsed + partialTick;
        boolean using = state.phase == Phase.USING;
        float useProgress = using
                ? Math.max(0.0F, Math.min(1.0F, useAge / state.usingTotal))
                : 0.0F;
        float angle;
        boolean strikeVisible;
        if (state.attackStartTick >= 0
                && tick - state.attackStartTick < SwingAnimation.ATTACK_DURATION_TICKS) {
            float progress = (tick - state.attackStartTick + partialTick)
                    / SwingAnimation.ATTACK_DURATION_TICKS;
            angle = SwingAnimation.attackAngleDeg(Math.max(0.0F, Math.min(1.0F, progress)));
            strikeVisible = true;
        } else if (using) {
            // Chew bob is a Y translation; the vanilla raise to the mouth also tilts the
            // item (Z) — the tilt snaps in at the start and holds while chewing.
            angle = SwingAnimation.useTiltDeg(useProgress);
            strikeVisible = false;
        } else if (state.phase == Phase.MINING) {
            angle = SwingAnimation.miningAngleDeg(state.elapsed + partialTick, swingDurationTicks);
            strikeVisible = false;
        } else {
            angle = 0.0F;
            strikeVisible = false;
        }

        boolean mining = state.phase == Phase.MINING;
        float progress = mining ? Math.min(1.0F, state.speed * (state.elapsed + partialTick)) : 0.0F;

        view.swingAngleDeg = angle;
        view.mining = mining;
        view.blockState = state.blockState;
        view.progress = progress;
        view.crackStage = mining ? Math.min(9, (int) (progress * 10.0F)) : 0;
        view.using = using;
        view.usingItem = state.usingItem;
        view.usingProgress = useProgress;
        float chew = using
                ? SwingAnimation.chewBobPx(
                        SwingAnimation.useRemainingTicks(useAge, state.usingTotal), useProgress)
                : 0.0F;
        view.usingBobY = using ? SwingAnimation.useRaisePx(useProgress) - chew : 0.0F;
        view.hitVisible = strikeVisible || state.hitFlash > 0.0F;
        view.hitKind = state.hitKind;
        view.hitId = state.hitId;
        view.hitName = state.hitName;
        view.hitType = state.hitType;
        view.hitFlash = state.hitFlash;
        return view;
    }

    public static void retainOnly(Collection<UUID> presentMemberIds) {
        STATES.keySet().retainAll(new HashSet<>(presentMemberIds));
        BlockBreakParticles.INSTANCE.retainOnly(presentMemberIds);
        UseActionParticles.INSTANCE.retainOnly(presentMemberIds);
    }

    public static void clear() {
        STATES.clear();
        BlockBreakParticles.INSTANCE.clear();
        UseActionParticles.INSTANCE.clear();
    }
}
