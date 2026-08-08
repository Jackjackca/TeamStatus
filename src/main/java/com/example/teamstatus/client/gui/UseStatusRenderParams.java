package com.example.teamstatus.client.gui;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

public class UseStatusRenderParams {
    public float wobbleX;
    public float wobbleY;
    public float wobbleRot;
    public float scale;
    public float swingAngle;
    public float flashIntensity;
    public float shakeAngle;
    public float slashProgress;
    public int destroyStage;
    public boolean shouldSpawnParticles;
    public ResourceLocation particleSprite;
    public TextureAtlasSprite particleAtlasSprite;
    public float particleX;
    public float particleY;
    public int particleCount;
    public int lastParticleSpawnTick;
    public boolean shouldResetMiningProgress;

    // 挖掘渲染缓存: 在 20Hz tick 阶段预计算, render 阶段直接使用, 避免每帧重量级操作
    // cachedBlockSprite: 方块粒子纹理精灵 (通过 BlockModelShaper.getParticleIcon 获取, 任何方块都有效)
    public TextureAtlasSprite cachedBlockSprite;
    // cachedDestroyStageSprite: 破坏阶段纹理精灵, 用于叠加在方块上
    public TextureAtlasSprite cachedDestroyStageSprite;

    public void reset() {
        wobbleX = 0;
        wobbleY = 0;
        wobbleRot = 0;
        scale = 1.0f;
        swingAngle = 0;
        flashIntensity = 0;
        shakeAngle = 0;
        slashProgress = 0;
        destroyStage = 0;
        shouldSpawnParticles = false;
        particleSprite = null;
        particleAtlasSprite = null;
        particleX = 0;
        particleY = 0;
        particleCount = 0;
        shouldResetMiningProgress = false;
        cachedBlockSprite = null;
        cachedDestroyStageSprite = null;
    }
}