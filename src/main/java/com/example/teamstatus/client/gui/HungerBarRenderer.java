package com.example.teamstatus.client.gui;

import com.example.teamstatus.TeamMember;
import com.example.teamstatus.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 食物条渲染器 — 数据与渲染完全解耦。
 * <p>
 * computeState() 在 20Hz tick 阶段预计算所有参数（纹理选择、抖动偏移、饱和度 UV）。
 * render() 退化为纯读取 + blitSprite/blit 调用。
 */
@SuppressWarnings("null")
public class HungerBarRenderer {

    /**
     * 20Hz 预计算：确定纹理集、疲劳覆盖层、每颗食物的填充和偏移、饱和度 UV。
     */
    public static void computeState(TeamMember member, HungerRenderState state, int guiTicks) {
        int hunger = member.getHunger();
        float saturation = member.getSaturation();
        float exhaustion = member.getExhaustion();
        boolean hasHungerEffect = member.hasHungerEffect();

        // 纹理集选择
        state.emptyTex = hasHungerEffect ? HungerTextures.EMPTY_HUNGER : HungerTextures.EMPTY;
        state.fullTex  = hasHungerEffect ? HungerTextures.FULL_HUNGER  : HungerTextures.FULL;
        state.halfTex  = hasHungerEffect ? HungerTextures.HALF_HUNGER  : HungerTextures.HALF;

        // 疲劳覆盖层
        if (exhaustion > 0) {
            float ratio = Math.min(1.0F, Math.max(0.0F, exhaustion / 4.0F));
            state.exhaustionWidth = (int) (ratio * 81);
            state.showExhaustion = true;
        } else {
            state.showExhaustion = false;
        }

        // 饱和度为 0 时的周期性抖动（与原版一致：guiTicks % (hunger * 3 + 1) == 0）
        boolean wobble = saturation <= 0.0F && guiTicks % (hunger * 3 + 1) == 0;

        // 10 个食物图标
        for (int i = 0; i < HungerRenderState.MAX_FOOD; i++) {
            int hungerIndex = i * 2;
            if (hunger > hungerIndex + 1) {
                state.foodFill[i] = state.fullTex;
            } else if (hunger == hungerIndex + 1) {
                state.foodFill[i] = state.halfTex;
            } else {
                state.foodFill[i] = null;
            }

            state.foodYOffset[i] = wobble ? (Util.getRandom().nextInt(3) - 1) : 0;
        }

        // 饱和度覆盖层
        state.satBars = saturation > 0
                ? (int) Math.min(Math.ceil(saturation / 2.0F), 10)
                : 0;

        for (int i = 0; i < state.satBars; i++) {
            state.satOffsetX[i] = 72 - (i * 8);

            float fillValue = saturation - (i * 2.0F);
            if (fillValue >= 2.0F)      state.satU[i] = 27;
            else if (fillValue >= 1.5F) state.satU[i] = 18;
            else if (fillValue >= 1.0F) state.satU[i] = 9;
            else                        state.satU[i] = 0;
        }
    }

    /**
     * 极简渲染 — 读取预计算状态，执行 blit。
     */
    public static void render(GuiGraphics guiGraphics, TeamMember member, int x, int y) {
        HungerRenderState state = member.getHungerState();

        // 疲劳覆盖层
        if (state.showExhaustion) {
            int w = state.exhaustionWidth;
            guiGraphics.blit(HungerTextures.EXHAUSTION,
                    x + 82 - w, y,
                    81 - w, 18,
                    w, 9,
                    256, 256);
        }

        // 10 个食物图标
        for (int i = 0; i < HungerRenderState.MAX_FOOD; i++) {
            int iconX = x + 72 - (i * 8);
            int iconY = y + state.foodYOffset[i];

            guiGraphics.blitSprite(state.emptyTex, iconX, iconY,
                    HudLayout.ICON_SIZE, HudLayout.ICON_SIZE);

            ResourceLocation fill = state.foodFill[i];
            if (fill != null) {
                guiGraphics.blitSprite(fill, iconX, iconY,
                        HudLayout.ICON_SIZE, HudLayout.ICON_SIZE);
            }
        }

        // 饱和度覆盖层
        for (int i = 0; i < state.satBars; i++) {
            guiGraphics.blit(HungerTextures.SATURATION,
                    x + state.satOffsetX[i], y,
                    state.satU[i], 0,
                    9, 9,
                    256, 256);
        }
    }
}
