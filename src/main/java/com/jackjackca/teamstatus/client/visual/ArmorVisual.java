package com.jackjackca.teamstatus.client.visual;

import java.util.ArrayList;
import java.util.List;

import com.jackjackca.teamstatus.client.render.ArmorRenderer;
import com.jackjackca.teamstatus.client.render.ArmorRenderer.ArmorIcon;
import com.jackjackca.teamstatus.client.render.ArmorRenderer.Kind;

import net.minecraft.client.gui.GuiGraphics;

/**
 * State &rarr; armor icon descriptors. When the member has any armor this produces all 10
 * vanilla slots — full, half and empty containers — matching the vanilla armor bar; with no
 * armor the list is empty and the row takes no space. List built at gui-tick granularity and
 * cached.
 */
public final class ArmorVisual {

    private static final int MAX_ICONS = 10;

    private ArmorVisual() {
    }

    /** Rebuilds the armor icon list (cache miss path only). */
    public static List<ArmorIcon> build(MemberVisualContext context) {
        int armorValue = context.member().armorValue();
        if (armorValue <= 0) {
            return List.of();
        }
        List<ArmorIcon> icons = new ArrayList<>(MAX_ICONS);
        for (int i = 0; i < MAX_ICONS; i++) {
            Kind kind;
            if (i * 2 + 1 < armorValue) {
                kind = Kind.FULL;
            } else if (i * 2 + 1 == armorValue) {
                kind = Kind.HALF;
            } else {
                kind = Kind.EMPTY;
            }
            icons.add(new ArmorIcon(kind));
        }
        return icons;
    }

    public static void render(GuiGraphics gui, int x, int y, List<ArmorIcon> icons) {
        if (!icons.isEmpty()) {
            ArmorRenderer.draw(gui, x, y, icons);
        }
    }

    public static int height(List<ArmorIcon> icons) {
        return icons.isEmpty() ? 0 : ArmorRenderer.ROW_HEIGHT;
    }
}
