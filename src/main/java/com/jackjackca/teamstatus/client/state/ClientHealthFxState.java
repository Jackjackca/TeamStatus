package com.jackjackca.teamstatus.client.state;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import com.jackjackca.teamstatus.common.state.TeamMemberState;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Client-side per-member memory of recent health changes, mirroring the vanilla
 * {@code Gui.renderHealthLevel} bookkeeping so teammates' hearts reproduce the vanilla
 * damage/heal flash:
 * <ul>
 *   <li>a health drop schedules a 20-tick blinking window, a rise schedules 10 ticks;</li>
 *   <li>{@code displayHealth} keeps the pre-change health for 1000&nbsp;ms (20 ticks), so the
 *       hearts between the old and new value paint the red {@code _blinking} sprites;</li>
 *   <li>the visible blink toggles every 3 ticks, exactly like
 *       {@code (blinkUntil - tickCount) / 3 % 2 == 1}.</li>
 * </ul>
 * Time is measured with the client gui tick counter (20&nbsp;Hz), the same clock the vanilla
 * GUI uses. All access happens on the client main thread.
 */
public final class ClientHealthFxState {

    /** Vanilla damage flash window, in client ticks (Gui#healthBlinkTime = tickCount + 20). */
    private static final long DAMAGE_BLINK_TICKS = 20;
    /** Vanilla heal flash window (tickCount + 10). */
    private static final long HEAL_BLINK_TICKS = 10;
    /** Vanilla 1000 ms catch-up delay, expressed in 20 Hz ticks. */
    private static final long DISPLAY_CATCHUP_TICKS = 20;

    private record Fx(int lastHealth, int displayHealth, long lastChangeTick, long blinkUntilTick) {
    }

    private static final Map<UUID, Fx> FX = new HashMap<>();

    private ClientHealthFxState() {
    }

    /** Updates the memory from a fresh team snapshot, already dispatched to the main thread. */
    public static void update(Collection<TeamMemberState> snapshot) {
        long now = Minecraft.getInstance().gui.getGuiTicks();
        for (TeamMemberState member : snapshot) {
            if (!member.online()) {
                FX.remove(member.id());
                continue;
            }
            int current = Mth.ceil(member.health());
            Fx fx = FX.get(member.id());
            if (fx == null) {
                FX.put(member.id(), new Fx(current, current, now, Long.MIN_VALUE));
                continue;
            }

            long lastChangeTick = fx.lastChangeTick();
            long blinkUntilTick = fx.blinkUntilTick();
            int displayHealth = fx.displayHealth();

            // Vanilla runs this catch-up every frame BEFORE evaluating the new health: once
            // 1000 ms passed since the last change, displayHealth is pinned to the last known
            // health. Doing this first is what makes a later hit after a long idle gap flash
            // only the hearts lost by that hit — the timer reset below must not be able to
            // keep a stale displayHealth alive.
            if (now - lastChangeTick >= DISPLAY_CATCHUP_TICKS) {
                displayHealth = fx.lastHealth();
            }

            // Mirrors the two invulnerableTime branches in Gui#renderHealthLevel.
            if (current != fx.lastHealth()) {
                lastChangeTick = now;
                blinkUntilTick = now + (current < fx.lastHealth()
                        ? DAMAGE_BLINK_TICKS : HEAL_BLINK_TICKS);
            }

            FX.put(member.id(), new Fx(current, displayHealth, lastChangeTick, blinkUntilTick));
        }
    }

    /**
     * @return the delayed health used for the blinking layer, or {@code currentHealth} when
     *         the member has no recorded change. Only consumed while {@link #isBlinking} is
     *         true, inside which the stored value is always the pre-change health.
     */
    public static int displayHealth(UUID memberId, int currentHealth) {
        Fx fx = FX.get(memberId);
        return fx == null ? currentHealth : fx.displayHealth();
    }

    /** @return whether hearts blink on this gui tick, identical to the vanilla parity check */
    public static boolean isBlinking(UUID memberId, long guiTicks) {
        Fx fx = FX.get(memberId);
        return fx != null && fx.blinkUntilTick() > guiTicks
                && (fx.blinkUntilTick() - guiTicks) / 3L % 2L == 1L;
    }

    /** Evicts state of members no longer present in the team snapshot. */
    public static void retainOnly(Collection<UUID> presentMemberIds) {
        FX.keySet().retainAll(new HashSet<>(presentMemberIds));
    }

    public static void clear() {
        FX.clear();
    }
}
