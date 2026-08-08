package com.example.teamstatus.client.gui;

import net.minecraft.resources.ResourceLocation;

/**
 * 预缓存的食物/饱和度/疲劳纹理 — 消除渲染循环中的纹理查找。
 */
public final class HungerTextures {
    // === 普通食物图标 ===
    public static final ResourceLocation EMPTY =
            ResourceLocation.withDefaultNamespace("hud/food_empty");
    public static final ResourceLocation FULL =
            ResourceLocation.withDefaultNamespace("hud/food_full");
    public static final ResourceLocation HALF =
            ResourceLocation.withDefaultNamespace("hud/food_half");

    // === 饥饿效果食物图标 ===
    public static final ResourceLocation EMPTY_HUNGER =
            ResourceLocation.withDefaultNamespace("hud/food_empty_hunger");
    public static final ResourceLocation FULL_HUNGER =
            ResourceLocation.withDefaultNamespace("hud/food_full_hunger");
    public static final ResourceLocation HALF_HUNGER =
            ResourceLocation.withDefaultNamespace("hud/food_half_hunger");

    // === AppleSkin Mod 纹理（饱和度 / 疲劳） ===
    public static final ResourceLocation SATURATION =
            ResourceLocation.fromNamespaceAndPath("appleskin", "textures/icons.png");
    public static final ResourceLocation EXHAUSTION =
            ResourceLocation.fromNamespaceAndPath("appleskin", "textures/icons.png");

    private HungerTextures() {}
}
