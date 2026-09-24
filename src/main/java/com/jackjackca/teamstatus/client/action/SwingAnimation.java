package com.jackjackca.teamstatus.client.action;

/**
 * Pure swing-angle math for the held-item animation. No state, no rendering.
 * <p>
 * Angles are in degrees, sign chosen for the 2D HUD (y-down): positive Z rotation swings the
 * item head to the right around the lower-handle pivot.
 */
public final class SwingAnimation {

    /** Pivot inside the 16x16 item slot: horizontally centered, at the lower handle. */
    public static final float PIVOT_X = 8.0F;
    public static final float PIVOT_Y = 15.0F;

    /** Peak swing angle to the right. */
    private static final float MINING_AMPLITUDE_DEG = 30.0F;
    private static final float ATTACK_AMPLITUDE_DEG = 45.0F;

    /**
     * Base arm-swing duration in client ticks, vanilla {@code LivingEntity#getCurrentSwingDuration}.
     * Haste shortens it to {@code 6 - (1 + amplifier)} and Mining Fatigue lengthens it to
     * {@code 6 + (1 + amplifier) * 2}.
     */
    public static final int BASE_SWING_DURATION_TICKS = 6;

    /** Chew bob peak in GUI pixels: vanilla translates 0.1 block, mapped onto the 16px slot. */
    private static final float CHEW_BOB_PX = 1.6F;
    /**
     * Rise toward the mouth in GUI pixels (vanilla moves 0.5 block in first person;
     * scaled down so the 16px icon stays inside the panel).
     */
    private static final float USE_RAISE_PX = 3.0F;
    /** Tilt toward the mouth in degrees, vanilla {@code f3 * 30} on the Z axis; negative
     *  because this HUD's positive Z swing points right and the food tilts left (up-left). */
    private static final float USE_TILT_DEG = -30.0F;
    /** Vanilla only starts chewing once 20% of the use time has elapsed. */
    private static final float CHEW_START_PROGRESS = 0.2F;
    /** Chew peaks (and crumb spawns) recur every 4 ticks, vanilla {@code f / 4 * PI}. */
    public static final int CHEW_PERIOD_TICKS = 4;

    /** One-shot strike duration, in client ticks. */
    public static final int ATTACK_DURATION_TICKS = 7;

    private SwingAnimation() {
    }

    /**
     * Vanilla swing duration for the given dig-effect amplifiers (1.21.1
     * {@code LivingEntity#getCurrentSwingDuration}).
     *
     * @param hasteAmplifier    Haste amplifier, or -1 when absent (takes priority)
     * @param fatigueAmplifier  Mining Fatigue amplifier, or -1 when absent
     */
    public static int swingDurationTicks(int hasteAmplifier, int fatigueAmplifier) {
        if (hasteAmplifier >= 0) {
            return Math.max(1, BASE_SWING_DURATION_TICKS - (1 + hasteAmplifier));
        }
        if (fatigueAmplifier >= 0) {
            return BASE_SWING_DURATION_TICKS + (1 + fatigueAmplifier) * 2;
        }
        return BASE_SWING_DURATION_TICKS;
    }

    /**
     * Continuous mining chop timed exactly like the vanilla held-item arm. While the break
     * button is held the game calls {@code swing()} every tick, and {@code LivingEntity#swing}
     * restarts a swing as soon as {@code swingTime >= duration / 2} (integer division), so
     * mining repeatedly plays the first half of a swing: {@code duration/2} ticks striking
     * down plus a single restart tick. The angle follows the vanilla item transform
     * {@code sin(sqrt(progress) * PI)} ({@code ItemInHandRenderer#applyItemArmAttackTransform});
     * on the restart tick the render interpolation wraps progress from {@code 0.5} back toward
     * {@code 1.0}, which runs the same sine curve to zero — the fast retract.
     *
     * @param ageTicks           elapsed mining time including the frame's partial tick
     * @param swingDurationTicks current vanilla swing duration (see {@link #swingDurationTicks})
     */
    public static float miningAngleDeg(float ageTicks, int swingDurationTicks) {
        int duration = Math.max(1, swingDurationTicks);
        int half = duration / 2;
        float cycleTicks = half + 1.0F;
        float age = ageTicks % cycleTicks;
        float progress;
        if (age <= half) {
            // Strike: progress climbs 0 -> half/duration.
            progress = age / duration;
        } else {
            // Restart/retract tick: wrapped interpolation slides half/duration -> 1.0.
            float start = half / (float) duration;
            progress = start + (1.0F - start) * (age - half);
        }
        return (float) Math.sin(Math.sqrt(progress) * Math.PI) * MINING_AMPLITUDE_DEG;
    }

    /**
     * Vanilla remaining-use float ({@code applyEatTransform}): the remaining duration minus
     * the interpolated tick plus one, i.e. the phase of the chew cosine.
     */
    public static float useRemainingTicks(float ageTicks, int totalTicks) {
        return Math.max(1, totalTicks) - ageTicks + 1.0F;
    }

    /**
     * Chew bob while eating/drinking, timed exactly like the vanilla first-person hand.
     * Chewing starts only after {@link #CHEW_START_PROGRESS} of the use time; each chew peak
     * recurs every 4 ticks ({@code |cos(f/4 * PI)|} with f the remaining ticks). The offset
     * is positive at a peak, so the caller subtracts it to move the item up.
     *
     * @param remainingTicks {@link #useRemainingTicks}
     * @param progress       0..1 overall use progress
     */
    public static float chewBobPx(float remainingTicks, float progress) {
        if (progress <= CHEW_START_PROGRESS) {
            return 0.0F;
        }
        return Math.abs((float) Math.cos(remainingTicks / 4.0F * Math.PI)) * CHEW_BOB_PX;
    }

    /**
     * Vanilla "raise to mouth" envelope: {@code 1 - (1-progress)^27}. It leaves 0 on the
     * first tick and reaches ~0.97 within the first ~12% of the use, so the item snaps up
     * to the mouth immediately and stays raised throughout the chewing.
     */
    public static float useRaiseEnvelope(float progress) {
        float remaining = Math.max(0.0F, 1.0F - progress);
        return 1.0F - (float) Math.pow(remaining, 27.0);
    }

    /** Signed upward offset of the final raise, in GUI pixels (negative = up). */
    public static float useRaisePx(float progress) {
        return -useRaiseEnvelope(progress) * USE_RAISE_PX;
    }

    /** Z tilt of the final raise in degrees (vanilla peaks at 30). */
    public static float useTiltDeg(float progress) {
        return useRaiseEnvelope(progress) * USE_TILT_DEG;
    }

    /**
     * One-shot attack swing: 0 at start, peak halfway, back to 0 at {@code progress = 1}.
     *
     * @param progress 0..1 strike progress
     */
    public static float attackAngleDeg(float progress) {
        if (progress <= 0.0F || progress >= 1.0F) {
            return 0.0F;
        }
        return (float) Math.sin(progress * Math.PI) * ATTACK_AMPLITUDE_DEG;
    }
}
