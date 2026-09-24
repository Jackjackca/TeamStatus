package com.jackjackca.teamstatus.client.render;

import java.util.List;

import com.jackjackca.teamstatus.client.integration.AppleSkinIntegration;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Pure paint layer for the hunger row. The upstream visual layer resolves each of the 10
 * drumstick positions into a {@link Drumstick} descriptor (rotten/normal, fill state,
 * AppleSkin saturation level 0..3, per-icon y offset) plus the exhaustion strip width; this
 * class only places them on the vanilla 8px-step grid and blits sprites.
 * <p>
 * Index 0 is the rightmost icon and the bar fills right-to-left, exactly like vanilla
 * {@code Gui#renderFood} ({@code iconX = rightEdge - i * 8}); depletion therefore sweeps
 * left-to-right and remaining drumsticks stay right-aligned.
 */
public final class FoodRenderer {

    public static final int ICON_SIZE = 9;
    public static final int ICON_STEP = 8;
    public static final int ICON_COUNT = 10;
    public static final int ROW_HEIGHT = 9;
    // Vanilla row spans one icon plus nine 8px steps: 9 + 9*8 = 81px.
    public static final int ROW_WIDTH = ICON_SIZE + (ICON_COUNT - 1) * ICON_STEP;

    public enum Fill {
        EMPTY, HALF, FULL
    }

    /**
     * @param rotten           use the green "hunger" sprite variants
     * @param fill             empty / half / full drumstick
     * @param saturationLevel  AppleSkin saturation icon level: 0 = none, 1/2/3 increasing fill
     * @param offsetY          dynamic per-icon vertical offset (vanilla zero-saturation shake)
     */
    public record Drumstick(boolean rotten, Fill fill, int saturationLevel, int offsetY) {
    }

    private static final ResourceLocation[][] SPRITES = {
            // empty, half, full — normal and rotten (hunger effect)
            {sprite("hud/food_empty"), sprite("hud/food_half"), sprite("hud/food_full")},
            {sprite("hud/food_empty_hunger"), sprite("hud/food_half_hunger"), sprite("hud/food_full_hunger")}
    };

    private FoodRenderer() {
    }

    private static ResourceLocation sprite(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    public static void draw(GuiGraphics gui, int x, int y, List<Drumstick> drumsticks, int exhaustionWidth) {
        // Layer contract, mirrors AppleSkin's registerBelow/registerAbove(FOOD_LEVEL):
        // exhaustion underlay (bottom) -> drumsticks -> saturation overlay (top).
        AppleSkinIntegration.paintExhaustion(gui, x + ROW_WIDTH, y, exhaustionWidth);

        for (int i = 0; i < drumsticks.size(); i++) {
            Drumstick drumstick = drumsticks.get(i);
            // Right-aligned like vanilla: index 0 (first filled) sits at the right edge.
            int iconX = x + ROW_WIDTH - ICON_SIZE - i * ICON_STEP;
            int iconY = y + drumstick.offsetY();

            gui.blitSprite(SPRITES[drumstick.rotten() ? 1 : 0][Fill.EMPTY.ordinal()],
                    iconX, iconY, ICON_SIZE, ICON_SIZE);
            if (drumstick.fill() != Fill.EMPTY) {
                gui.blitSprite(SPRITES[drumstick.rotten() ? 1 : 0][drumstick.fill().ordinal()],
                        iconX, iconY, ICON_SIZE, ICON_SIZE);
            }

            // Saturation is an overlay, painted on top of the drumstick.
            AppleSkinIntegration.paintSaturationIcon(gui, iconX, iconY, drumstick.saturationLevel());
        }
    }
}
