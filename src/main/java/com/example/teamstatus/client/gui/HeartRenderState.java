package com.example.teamstatus.client.gui;

import net.minecraft.resources.ResourceLocation;

/**
 * 每颗心的预计算渲染参数 — 可变值对象，配合 TeamMember 内部数组复用，避免每帧 GC。
 * <p>
 * render 阶段仅做 O(1) 读取 + blitSprite 调用，不执行任何业务逻辑。
 */
public class HeartRenderState {
    /** 容器背景纹理（CONTAINER 或 CONTAINER_BLINK） */
    public ResourceLocation container;
    /** 前景填充纹理（null 表示空心，不绘制前景） */
    public ResourceLocation fill;
    /** 预计算的 Y 轴偏移（低血量抖动 / 再生跳动），已在 tick 阶段完成 */
    public int yOffset;
    /** 受伤差值半透明覆盖纹理（null 表示无差值，跳过绘制） */
    public ResourceLocation diff;

    public void reset() {
        container = null;
        fill = null;
        yOffset = 0;
        diff = null;
    }
}
