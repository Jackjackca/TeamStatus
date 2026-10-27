package com.jackjackca.teamstatus.client.integration;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
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

    private static final Identifier MOD_ICONS =
            Identifier.fromNamespaceAndPath(APPLESKIN_MOD_ID, "textures/icons.png");

    private static final int ICON_SIZE = 9;
    private static final int EXHAUSTION_V = 2 * ICON_SIZE;
    private static final int EXHAUSTION_STRIP_WIDTH = 81;
    private static final float EXHAUSTION_ALPHA = 0.75F;
    private static final int TEXTURE_SIZE = 256;

    private AppleSkinIntegration() {
    }

    public static boolean isActive() {
        return ModList.get() != null && ModList.get().isLoaded(APPLESKIN_MOD_ID);
    }

    /**
     * Paints one saturation drumstick icon at level 0..3 (u = level * 9 on the v=0 row).
     * Level 0 paints nothing.
     */
    public static void paintSaturationIcon(GuiGraphicsExtractor gui, int x, int y, int level) {
        if (!isActive() || level <= 0) {
            return;
        }
        gui.blit(RenderPipelines.GUI_TEXTURED, MOD_ICONS, x, y,
                level * ICON_SIZE, 0, ICON_SIZE, ICON_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    /**
     * Paints the dark exhaustion strip growing left from {@code rowRightEdge}.
     *
     * @param width strip width in pixels (0..81), computed by the visual layer
     */
    public static void paintExhaustion(GuiGraphicsExtractor gui, int rowRightEdge, int y, int width) {
        if (!isActive() || width <= 0) {
            return;
        }
        int alpha = (int) (EXHAUSTION_ALPHA * 255.0F) << 24;
        int tintColor = alpha | 0x00FFFFFF;
        gui.blit(RenderPipelines.GUI_TEXTURED, MOD_ICONS, rowRightEdge - width, y,
                EXHAUSTION_STRIP_WIDTH - width, EXHAUSTION_V,
                width, ICON_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, tintColor);
    }

    /** Full width of the exhaustion strip texture region. */
    public static int exhaustionStripWidth() {
        return EXHAUSTION_STRIP_WIDTH;
    }
}
