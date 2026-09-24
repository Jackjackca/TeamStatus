package com.jackjackca.teamstatus.client.visual;

import com.jackjackca.teamstatus.Config;
import com.jackjackca.teamstatus.client.action.BlockBreakParticles;
import com.jackjackca.teamstatus.client.action.HandActionStates;
import com.jackjackca.teamstatus.client.action.HandActionStates.View;
import com.jackjackca.teamstatus.client.action.SwingAnimation;
import com.jackjackca.teamstatus.client.action.UseActionParticles;
import com.jackjackca.teamstatus.client.render.BlockTargetRenderer;
import com.jackjackca.teamstatus.client.render.HandItemRenderer;
import com.jackjackca.teamstatus.client.render.HitIconRenderer;
import com.jackjackca.teamstatus.common.state.EffectState;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;

/**
 * Composition of the hand action zone, immediately right of the heart/food/armor rows:
 * main-hand item, then the mining block or struck-target icon. The x offsets are relative to
 * the content origin (right of the avatar column); vertically both slots are centered on the
 * same center line as the player avatar — the midpoint of the health, food and armor rows.
 * This layer is animated per frame but performs only math and direct draws (no descriptor
 * allocation).
 */
public final class HandActionVisual {

    /** Bars span x = 0..81 from the content origin; item starts after a 2px gap (ends at 99). */
    public static final int ITEM_X = 83;
    /** Target slot starts 1px right of the item, 14px wide (ends at 114). */
    public static final int TARGET_X = 100;
    public static final int TARGET_SIZE = 14;

    private static final ResourceLocation HASTE_ID =
            BuiltInRegistries.MOB_EFFECT.getKey(MobEffects.DIG_SPEED.value());
    private static final ResourceLocation FATIGUE_ID =
            BuiltInRegistries.MOB_EFFECT.getKey(MobEffects.DIG_SLOWDOWN.value());

    private HandActionVisual() {
    }

    /**
     * @param originX x of the content column (avatar column excluded)
     * @param centerY vertical center line shared with the avatar (midpoint of health, food
     *                and armor rows)
     */
    public static void render(GuiGraphics gui, MemberVisualContext context, int originX,
                              float centerY) {
        View view = HandActionStates.getView(context.member().id(), context.partialTick(),
                swingDurationTicks(context));

        int itemX = originX + ITEM_X;
        int targetX = originX + TARGET_X;
        int itemY = Math.round(centerY - HandItemRenderer.SLOT_SIZE / 2.0F);
        int targetY = Math.round(centerY - TARGET_SIZE / 2.0F);
        float targetCenterX = targetX + TARGET_SIZE / 2.0F;

        // 3D cube first: writes real depth, then everything else layers above it.
        if (view != null && view.mining) {
            BlockTargetRenderer.draw(gui, view.blockState, targetCenterX, centerY,
                    view.crackStage);
            BlockBreakParticles.INSTANCE.render(gui, context.member().id(),
                    targetCenterX, centerY, context.partialTick());
        }

        if (Config.SHOW_HANDS.get()) {
            // While eating/drinking the packet carries the used stack (may be the off hand);
            // otherwise the animated item is the current main hand. The use-progress bar is
            // painted at the durability-bar position inside HandItemRenderer.
            var shownItem = view != null && view.using ? view.usingItem : context.member().mainHand();
            HandItemRenderer.draw(gui, shownItem, itemX, itemY,
                    view == null ? 0.0F : view.swingAngleDeg,
                    view == null ? 0.0F : view.usingBobY,
                    view == null || !view.using ? 0.0F : view.usingProgress);
        }

        // Eat/drink crumbs around the item slot, same 2D particle treatment as mining debris.
        if (view != null && view.using) {
            UseActionParticles.INSTANCE.render(gui, context.member().id(),
                    itemX + HandItemRenderer.SLOT_SIZE / 2.0F,
                    itemY + HandItemRenderer.SLOT_SIZE / 2.0F,
                    context.partialTick());
        }

        if (view != null && view.hitVisible) {
            switch (view.hitKind) {
                case PLAYER -> {
                    if (view.hitId != null) {
                        HitIconRenderer.drawPlayerFace(gui, view.hitId, view.hitName,
                                targetX, targetY);
                    }
                }
                case MOB -> {
                    if (view.hitType != null
                            && !HitIconRenderer.drawMobHead(gui, view.hitType, targetX, targetY)) {
                        HitIconRenderer.drawMobEggFallback(gui, view.hitType, targetX, targetY);
                    }
                }
            }
            HitIconRenderer.drawHitFlash(gui, targetX, targetY, view.hitFlash);
        }
    }

    /**
     * Resolves the vanilla arm-swing duration from the member's active Haste / Mining Fatigue
     * effects, so the mining chop speeds up and slows down exactly like the first-person arm.
     */
    private static int swingDurationTicks(MemberVisualContext context) {
        int haste = -1;
        int fatigue = -1;
        for (EffectState effect : context.member().effects()) {
            if (HASTE_ID.equals(effect.effectId())) {
                haste = Math.max(haste, effect.amplifier());
            } else if (FATIGUE_ID.equals(effect.effectId())) {
                fatigue = Math.max(fatigue, effect.amplifier());
            }
        }
        return SwingAnimation.swingDurationTicks(haste, fatigue);
    }
}
