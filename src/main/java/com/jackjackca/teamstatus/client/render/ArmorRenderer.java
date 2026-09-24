package com.jackjackca.teamstatus.client.render;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Pure paint layer for the armor row. Receives a resolved list of armor icons (one per
 * vanilla slot, up to 10) and places them on the vanilla 8px-step grid. When the wearer has
 * any armor the unfilled slots are shown with the {@code hud/armor_empty} container, exactly
 * like the vanilla armor bar; the list is empty when the wearer has no armor at all.
 */
public final class ArmorRenderer {

    public static final int ICON_SIZE = 9;
    public static final int ICON_STEP = 8;
    public static final int ROW_HEIGHT = 9;

    private static final ResourceLocation EMPTY_SPRITE =
            ResourceLocation.withDefaultNamespace("hud/armor_empty");
    private static final ResourceLocation HALF_SPRITE =
            ResourceLocation.withDefaultNamespace("hud/armor_half");
    private static final ResourceLocation FULL_SPRITE =
            ResourceLocation.withDefaultNamespace("hud/armor_full");

    /** One armor slot: empty container, half or full armor point. */
    public enum Kind {
        EMPTY(EMPTY_SPRITE),
        HALF(HALF_SPRITE),
        FULL(FULL_SPRITE);

        private final ResourceLocation sprite;

        Kind(ResourceLocation sprite) {
            this.sprite = sprite;
        }
    }

    public record ArmorIcon(Kind kind) {
    }

    private ArmorRenderer() {
    }

    public static void draw(GuiGraphics gui, int x, int y, List<ArmorIcon> icons) {
        for (int i = 0; i < icons.size(); i++) {
            int iconX = x + i * ICON_STEP;
            gui.blitSprite(icons.get(i).kind().sprite, iconX, y, ICON_SIZE, ICON_SIZE);
        }
    }
}
