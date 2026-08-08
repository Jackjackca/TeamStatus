package com.example.teamstatus.client.gui;

import net.minecraft.resources.ResourceLocation;

/**
 * 头像的预计算渲染参数 — 缓存皮肤查找结果，消除每帧线性扫描。
 */
public class AvatarRenderState {
    /** 玩家皮肤纹理（null = 使用回退渲染） */
    public ResourceLocation skinTexture;

    /** 回退渲染：UUID 派生颜色 */
    public int fallbackColor;
    /** 回退渲染：首字母 */
    public String initial;

    /** 闪烁 tint（NONE / HURT / HEAL） */
    public int tint;
}
