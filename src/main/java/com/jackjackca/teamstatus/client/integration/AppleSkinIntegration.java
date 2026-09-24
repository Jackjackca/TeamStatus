package com.jackjackca.teamstatus.client.integration;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/**
 * Optional AppleSkin integration, exposed as low-level paint primitives only.
 * <p>
 * This class knows nothing about food/saturation numbers: the upstream visual layer decides
 * which saturation level (u offset) or exhaustion strip width to paint and where. Here we only
 * blit the correct region of AppleSkin's sprite sheet {@code appleskin:textures/icons.png}
 * (256x256), matching {@code HUDOverlayHandler} exactly. Every call is a no-op unless
 * AppleSkin is loaded, so the mod never crashes without it.
 */
public final class AppleSkinIntegration {

    public static final String APPLESKIN_MOD_ID = "appleskin";

    private static final ResourceLocation MOD_ICONS =
            ResourceLocation.fromNamespaceAndPath(APPLESKIN_MOD_ID, "textures/icons.png");

    private static final int ICON_SIZE = 9;
    private static final int EXHAUSTION_V = 2 * ICON_SIZE;
    private static final int EXHAUSTION_STRIP_WIDTH = 81;
    private static final float EXHAUSTION_ALPHA = 0.75F;

    private AppleSkinIntegration() {
    }

    public static boolean isActive() {
        return ModList.get() != null && ModList.get().isLoaded(APPLESKIN_MOD_ID);
    }

    /**
     * Paints one saturation drumstick icon at level 0..3 (u = level * 9 on the v=0 row).
     * Level 0 paints nothing.
     */
    public static void paintSaturationIcon(GuiGraphics gui, int x, int y, int level) {
        if (!isActive() || level <= 0) {
            return;
        }
        gui.blit(MOD_ICONS, x, y, level * ICON_SIZE, 0, ICON_SIZE, ICON_SIZE);
    }

    /**
     * Paints the dark exhaustion strip growing left from {@code rowRightEdge}.
     *
     * @param width strip width in pixels (0..81), computed by the visual layer
     */
    public static void paintExhaustion(GuiGraphics gui, int rowRightEdge, int y, int width) {
        if (!isActive() || width <= 0) {
            return;
        }
        gui.setColor(1.0F, 1.0F, 1.0F, EXHAUSTION_ALPHA);
        RenderSystem.enableBlend();
        gui.blit(MOD_ICONS, rowRightEdge - width, y, EXHAUSTION_STRIP_WIDTH - width, EXHAUSTION_V,
                width, ICON_SIZE);
        RenderSystem.disableBlend();
        gui.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Full width of the exhaustion strip texture region. */
    public static int exhaustionStripWidth() {
        return EXHAUSTION_STRIP_WIDTH;
    }
}
