package com.example.teamstatus.client.gui;

import net.minecraft.resources.ResourceLocation;

/**
 * 食物条的预计算渲染参数 — render 阶段只做读取 + blit。
 */
public class HungerRenderState {
    public static final int MAX_FOOD = 10;

    // 纹理集（根据饥饿效果状态选择）
    public ResourceLocation emptyTex = HungerTextures.EMPTY;
    public ResourceLocation fullTex = HungerTextures.FULL;
    public ResourceLocation halfTex = HungerTextures.HALF;

    // 疲劳覆盖层
    public boolean showExhaustion;
    public int exhaustionWidth;

    // 10 个食物图标
    public final ResourceLocation[] foodFill = new ResourceLocation[MAX_FOOD];
    public final int[] foodYOffset = new int[MAX_FOOD];

    // 饱和度覆盖层
    public int satBars;
    public final int[] satOffsetX = new int[MAX_FOOD];
    public final int[] satU = new int[MAX_FOOD];
}
