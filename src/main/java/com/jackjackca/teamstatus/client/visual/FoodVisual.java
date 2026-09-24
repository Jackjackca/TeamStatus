package com.jackjackca.teamstatus.client.visual;

import java.util.ArrayList;
import java.util.List;

import com.jackjackca.teamstatus.client.integration.AppleSkinIntegration;
import com.jackjackca.teamstatus.client.render.FoodRenderer;
import com.jackjackca.teamstatus.client.render.FoodRenderer.Drumstick;
import com.jackjackca.teamstatus.client.render.FoodRenderer.Fill;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;

/**
 * State &rarr; hunger row descriptors (rotten fills, AppleSkin saturation levels, exhaustion
 * strip width, zero-saturation jitter). List built at gui-tick granularity and cached.
 */
public final class FoodVisual {

    private static final ResourceLocation HUNGER_ID =
            BuiltInRegistries.MOB_EFFECT.getKey(MobEffects.HUNGER.value());

    private static final int MAX_FOOD = 20;
    private static final float EXHAUSTION_PER_HUNGER_POINT = 4.0F;

    /** Shared out-parameter slot; build runs sequentially on the main thread at tick rate. */
    private static final int[] SCRATCH_WIDTH = new int[1];

    private FoodVisual() {
    }

    /** Rebuilds the drumstick descriptors (cache miss path only). */
    public static List<Drumstick> build(MemberVisualContext context) {
        return build(context, SCRATCH_WIDTH);
    }

    /**
     * Rebuilds the drumstick descriptors and writes the exhaustion strip width into
     * {@code exhaustionWidthOut[0]} (cache miss path only).
     */
    public static List<Drumstick> build(MemberVisualContext context, int[] exhaustionWidthOut) {
        int foodLevel = Math.max(0, Math.min(MAX_FOOD, context.member().foodLevel()));
        float saturation = context.member().saturationLevel();
        float exhaustion = context.member().exhaustionLevel();
        boolean rotten = context.member().effects().stream().anyMatch(e -> e.effectId().equals(HUNGER_ID));
        boolean jittering = saturation <= 0.0F;
        int guiTicks = context.guiTicks();

        List<Drumstick> drumsticks = new ArrayList<>(FoodRenderer.ICON_COUNT);
        for (int i = 0; i < FoodRenderer.ICON_COUNT; i++) {
            Fill fill;
            if (i * 2 + 1 < foodLevel) {
                fill = Fill.FULL;
            } else if (i * 2 + 1 == foodLevel) {
                fill = Fill.HALF;
            } else {
                fill = Fill.EMPTY;
            }

            drumsticks.add(new Drumstick(
                    rotten,
                    fill,
                    saturationLevel(i, saturation),
                    jittering && jitterActive(guiTicks, foodLevel)
                            ? zeroSaturationOffset(guiTicks, i) : 0));
        }

        exhaustionWidthOut[0] = exhaustionWidth(exhaustion);
        return drumsticks;
    }

    public static void render(GuiGraphics gui, int x, int y, List<Drumstick> drumsticks, int exhaustionWidth) {
        FoodRenderer.draw(gui, x, y, drumsticks, exhaustionWidth);
    }

    /** AppleSkin thresholds on the v=0 icon row. */
    private static int saturationLevel(int drumstickIndex, float saturation) {
        float effective = saturation / 2.0F - drumstickIndex;
        if (effective >= 1.0F) {
            return 3;
        }
        if (effective > 0.5F) {
            return 2;
        }
        if (effective > 0.25F) {
            return 1;
        }
        return 0;
    }

    /** Vanilla rule: {@code tickCount % (foodLevel * 3 + 1) == 0}. */
    private static boolean jitterActive(int guiTicks, int foodLevel) {
        return guiTicks % (foodLevel * 3 + 1) == 0;
    }

    /** Vanilla offset for the active jitter tick: {@code random.nextInt(3) - 1}. */
    private static int zeroSaturationOffset(int guiTicks, int drumstickIndex) {
        return RandomSource.create(guiTicks * 312871L + 100L + drumstickIndex).nextInt(3) - 1;
    }

    private static int exhaustionWidth(float exhaustion) {
        float ratio = Math.min(1.0F, Math.max(0.0F, exhaustion / EXHAUSTION_PER_HUNGER_POINT));
        return (int) (ratio * AppleSkinIntegration.exhaustionStripWidth());
    }
}
