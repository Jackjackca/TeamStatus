package com.jackjackca.teamstatus.client.render;

import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Pure paint layer for hearts. It knows nothing about effects, freezing or health numbers:
 * the upstream visual layer resolves every slot into a {@link HeartSlot} descriptor (normal
 * fill, optional blinking fill, half/full, per-slot y offset), and this class only places
 * them on the vanilla heart grid.
 * <p>
 * All sprites, including the red {@code _blinking} damage/heal variants, are resolved through
 * the vanilla {@link Gui.HeartType#getSprite(boolean, boolean, boolean)} enum API, so the
 * mod never hardcodes heart texture paths and automatically stays in sync with resource
 * packs and NeoForge heart-type extensions.
 * <p>
 * Static layout (identical to vanilla {@code Gui}): 9px sprites every 8px, 10 hearts per row,
 * rows pitched 10px (1px gap). Absorption rows are drawn ABOVE the blood-heart rows, matching
 * the vanilla ordering where absorption overlays sit on top of the health bar.
 */
public final class HealthRenderer {

    public static final int SPRITE_SIZE = 9;
    public static final int SPRITE_STEP = 8;
    public static final int HEARTS_PER_ROW = 10;
    /** Vertical pitch of heart rows: 9px sprite plus a 1px gap, like vanilla. */
    public static final int ROW_STEP = 10;

    /**
     * One heart position on the grid. Paint order mirrors vanilla {@code Gui#renderHearts}:
     * container first, then the blinking overlay, then the normal fill on top, so hearts
     * covered only by the blinking layer are exactly the slots lost since the last change.
     *
     * @param fill         normal fill painted last (over the blink layer), {@code null} for none
     * @param fillHalf     whether the normal fill is a half heart
     * @param blinkFill    blinking fill painted under the normal fill, {@code null} for none
     * @param blinkHalf    whether the blinking fill is a half heart
     * @param containerBlink whether the container uses the {@code container_blinking} sprite
     * @param hardcore     whether to use hardcore sprite variants
     * @param offsetY      dynamic per-heart vertical offset supplied by the visual layer
     *                     (low-health shake, regeneration pulse); this class never computes it
     */
    public record HeartSlot(Gui.HeartType fill, boolean fillHalf,
                            Gui.HeartType blinkFill, boolean blinkHalf,
                            boolean containerBlink, boolean hardcore, int offsetY) {

        /** Empty container with no fills. */
        public static HeartSlot empty(boolean hardcore, int offsetY, boolean containerBlink) {
            return new HeartSlot(null, false, null, false, containerBlink, hardcore, offsetY);
        }
    }

    private HealthRenderer() {
    }

    public static int rows(int slotCount) {
        return Math.max(1, (int) Math.ceil(slotCount / (double) HEARTS_PER_ROW));
    }

    /** Total painted height for the two groups; the blood group always occupies at least one row. */
    public static int height(int bloodCount, int absorptionCount) {
        int groupRows = rows(bloodCount) + Math.max(0, (int) Math.ceil(absorptionCount / (double) HEARTS_PER_ROW));
        return (groupRows - 1) * ROW_STEP + SPRITE_SIZE;
    }

    /**
     * Draws absorption hearts first (top rows) and blood hearts below them, each group
     * restarting at column 0 and wrapping every {@link #HEARTS_PER_ROW} hearts.
     */
    public static void draw(GuiGraphics gui, int x, int y,
                            List<HeartSlot> blood, List<HeartSlot> absorption) {
        int absorptionRows = (int) Math.ceil(absorption.size() / (double) HEARTS_PER_ROW);
        drawGroup(gui, x, y, absorption);
        drawGroup(gui, x, y + absorptionRows * ROW_STEP, blood);
    }

    private static void drawGroup(GuiGraphics gui, int x, int groupTopY, List<HeartSlot> slots) {
        for (int i = 0; i < slots.size(); i++) {
            HeartSlot slot = slots.get(i);
            int row = i / HEARTS_PER_ROW;
            int column = i % HEARTS_PER_ROW;
            int iconX = x + column * SPRITE_STEP;
            int iconY = groupTopY + row * ROW_STEP + slot.offsetY();

            ResourceLocation container =
                    Gui.HeartType.CONTAINER.getSprite(slot.hardcore(), false, slot.containerBlink());
            gui.blitSprite(container, iconX, iconY, SPRITE_SIZE, SPRITE_SIZE);

            if (slot.blinkFill() != null) {
                ResourceLocation blinkSprite =
                        slot.blinkFill().getSprite(slot.hardcore(), slot.blinkHalf(), true);
                gui.blitSprite(blinkSprite, iconX, iconY, SPRITE_SIZE, SPRITE_SIZE);
            }

            if (slot.fill() != null) {
                ResourceLocation sprite =
                        slot.fill().getSprite(slot.hardcore(), slot.fillHalf(), false);
                gui.blitSprite(sprite, iconX, iconY, SPRITE_SIZE, SPRITE_SIZE);
            }
        }
    }
}
