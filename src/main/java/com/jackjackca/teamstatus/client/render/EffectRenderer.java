package com.jackjackca.teamstatus.client.render;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Pure paint layer for status effects. Receives resolved {@link EffectEntry} descriptors
 * (icon sprite + label text produced by the upstream visual layer) and owns the static row
 * wrapping layout: icon, label, gap, wrapping at the panel width.
 */
public final class EffectRenderer {

    /** Effect icons occupy a 16x16 slot (the native 18px sprite is blit-scaled to fit). */
    public static final int ICON_SIZE = 16;
    public static final int ROW_HEIGHT = 16;
    private static final int LABEL_GAP = 2;
    private static final int ENTRY_GAP = 6;
    /** 9px font vertically centered inside the 16px icon: (16 - 9) / 2 ≈ 4. */
    private static final int LABEL_Y_INSET = 4;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    /** Vanilla renders beacon/ambient effect icons and labels at reduced opacity. */
    private static final float AMBIENT_ALPHA = 0.55F;

    /** One resolved effect icon with its duration/amplifier label. Beacon/ambient effects
     *  carry a translucent icon, mirroring the vanilla inventory effect rendering. */
    public record EffectEntry(TextureAtlasSprite sprite, String label, boolean ambient) {
    }

    private EffectRenderer() {
    }

    public static int rows(List<EffectEntry> entries, int width, Font font) {
        int rows = 1;
        int cursorX = 0;
        for (EffectEntry entry : entries) {
            int entryWidth = ICON_SIZE + LABEL_GAP + font.width(entry.label());
            if (cursorX > 0 && cursorX + entryWidth > width) {
                cursorX = 0;
                rows++;
            }
            cursorX += entryWidth + ENTRY_GAP;
        }
        return rows;
    }

    public static int height(List<EffectEntry> entries, int width, Font font) {
        return entries.isEmpty() ? 0 : rows(entries, width, font) * ROW_HEIGHT;
    }

    public static void draw(GuiGraphics gui, int x, int y, int width, List<EffectEntry> entries) {
        Font font = Minecraft.getInstance().font;
        int cursorX = 0;
        int row = 0;
        for (EffectEntry entry : entries) {
            int entryWidth = ICON_SIZE + LABEL_GAP + font.width(entry.label());
            if (cursorX > 0 && cursorX + entryWidth > width) {
                cursorX = 0;
                row++;
            }

            int iconX = x + cursorX;
            int iconY = y + row * ROW_HEIGHT;
            float iconAlpha = entry.ambient() ? AMBIENT_ALPHA : 1.0F;
            gui.blit(iconX, iconY, 0, ICON_SIZE, ICON_SIZE, entry.sprite(),
                    1.0F, 1.0F, 1.0F, iconAlpha);
            int textAlpha = entry.ambient() ? (int) (AMBIENT_ALPHA * 255.0F) << 24 : TEXT_COLOR;
            gui.drawString(font, entry.label(), iconX + ICON_SIZE + LABEL_GAP,
                    iconY + LABEL_Y_INSET, textAlpha, true);

            cursorX += entryWidth + ENTRY_GAP;
        }
    }
}
