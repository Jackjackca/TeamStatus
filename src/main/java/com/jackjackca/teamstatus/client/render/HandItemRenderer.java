package com.jackjackca.teamstatus.client.render;

import com.jackjackca.teamstatus.client.action.SwingAnimation;
import com.jackjackca.teamstatus.common.state.ItemState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Pure paint layer for the main-hand item with the swing rotation applied around the lower
 * handle pivot. Empty stacks paint nothing.
 * <p>
 * The frame origin is first moved to the slot's panel position; afterwards all pivot math is
 * local (the vanilla {@code renderItem} itself adds the (8,8) center), so the item is drawn at
 * local (0,0) and the rotation never double-translates it off the panel.
 * <p>
 * The durability bar and stack count are rendered in a second, rotation-free pose: they stay
 * upright in the slot and only follow the vertical eat motion, decoupled from the swing. The
 * use-progress bar sits immediately below the 16px slot (local y 16..18, flush with the
 * item's underside) so it never covers the stack count; food/drink items never show their own
 * durability bar.
 */
public final class HandItemRenderer {

    public static final int SLOT_SIZE = 16;
    /** Use-progress bar: flush against the underside of the 16px item slot. */
    private static final int BAR_X = 2;
    private static final int BAR_Y = SLOT_SIZE;
    private static final int BAR_WIDTH = 13;
    private static final int BAR_TRACK_COLOR = 0xFF000000;
    private static final int BAR_FILL_COLOR = 0xFFFFFFFF;

    static Font font = Minecraft.getInstance().font;

    private HandItemRenderer() {
    }

    /**
     * @param x            slot left in the panel coordinate space
     * @param y            slot top in the panel coordinate space
     * @param angleDeg     rotation around the lower-handle pivot (positive swings head right)
     * @param bobY         vertical offset in GUI pixels applied before the rotation
     *                     (negative = up); used for the eating/drinking motion
     * @param useProgress  0..1 eat/drink progress; 0 hides the durability-style progress bar
     */
    public static void draw(GuiGraphicsExtractor gui, ItemState item, int x, int y, float angleDeg,
                            float bobY, float useProgress) {
        if (item == null || item.isEmpty()) {
            return;
        }
        var pose = gui.pose();
        // Item model: full swing transform around the lower-handle pivot.
        pose.pushMatrix();
        // Local frame: origin at the slot corner, shifted vertically for the eat motion, then
        // rotate around the lower-handle pivot.
        pose.translate(x, y + bobY);
        pose.translate(SwingAnimation.PIVOT_X, SwingAnimation.PIVOT_Y);
        pose.rotate((float) Math.toRadians(angleDeg));
        pose.translate(-SwingAnimation.PIVOT_X, -SwingAnimation.PIVOT_Y);
        gui.item(item.stack(), 0, 0);
        pose.popMatrix();

        // Durability bar / stack count / use progress are drawn in a separate upright pose:
        // they share the slot position and the eat motion but never inherit the swing
        // rotation, so text and bars stay horizontal while the tool chops. Draw order (the
        // element is submitted after the item in the same GUI tree) keeps it above the item.
        pose.pushMatrix();
        pose.translate(x, y + bobY);
        gui.itemDecorations(font, item.stack(), 0, 0);
        if (useProgress > 0.0F) {
            gui.fill(RenderPipelines.GUI, BAR_X, BAR_Y, BAR_X + BAR_WIDTH, BAR_Y + 2,
                    BAR_TRACK_COLOR);
            int fillWidth = Math.round(BAR_WIDTH * Math.min(1.0F, useProgress));
            if (fillWidth > 0) {
                gui.fill(RenderPipelines.GUI, BAR_X, BAR_Y, BAR_X + fillWidth, BAR_Y + 1,
                        BAR_FILL_COLOR);
            }
        }
        pose.popMatrix();
    }
}
