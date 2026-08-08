package com.example.teamstatus.client.gui;

import net.minecraft.resources.ResourceLocation;

/**
 * 预缓存的心形纹理查找表 — O(1) 数组寻址，零字符串拼接。
 * 消除原 getHeartTexture() 中每帧每心的 ResourceLocation 动态创建。
 */
@SuppressWarnings("null")
public final class HeartTextures {
    // === 容器背景（始终渲染） ===
    public static final ResourceLocation CONTAINER =
            ResourceLocation.withDefaultNamespace("hud/heart/container");
    public static final ResourceLocation CONTAINER_BLINK =
            ResourceLocation.withDefaultNamespace("hud/heart/container_blinking");

    // === 前景填充纹理查找表 ===
    // 维度: [HeartType.ordinal()][variant]
    // variant: 0 = full, 1 = half
    private static final int FULL = 0;
    private static final int HALF = 1;
    private static final int VARIANTS = 2;

    private static final ResourceLocation[][] TABLE =
            new ResourceLocation[HeartType.values().length][VARIANTS];

    static {
        put(HeartType.NORMAL,    "hud/heart/full");
        put(HeartType.POISONED,  "hud/heart/poisoned_full");
        put(HeartType.WITHERED,  "hud/heart/withered_full");
        put(HeartType.ABSORBING, "hud/heart/absorbing_full");
        put(HeartType.FROZEN,    "hud/heart/frozen_full");
    }

    private static void put(HeartType type, String fullTexturePath) {
        TABLE[type.ordinal()][FULL] = ResourceLocation.withDefaultNamespace(fullTexturePath);
        TABLE[type.ordinal()][HALF] = ResourceLocation.withDefaultNamespace(
                fullTexturePath.replace("full", "half"));
    }

    /**
     * O(1) 获取指定类型和填充度的心形纹理引用。
     * 返回的是预分配的 static final 引用，无任何堆分配。
     */
    public static ResourceLocation get(HeartType type, boolean half) {
        return TABLE[type.ordinal()][half ? HALF : FULL];
    }

    private HeartTextures() {} // 工具类，禁止实例化
}
