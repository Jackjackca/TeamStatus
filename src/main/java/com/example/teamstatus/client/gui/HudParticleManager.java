package com.example.teamstatus.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class HudParticleManager {
    public static final HudParticleManager INSTANCE = new HudParticleManager();
    private final List<HudParticle> particles = new ArrayList<>();
    
    private HudParticleManager() {}

    /**
     * 需要在 ClientTickEvent.Post 中调用此方法
     */
    public void tick() {
        if (particles.isEmpty()) return;
        
        Iterator<HudParticle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().tick()) {
                iterator.remove();
            }
        }
    }

    /**
     * 需要在 HudLayer 渲染逻辑的最后（覆盖在所有UI之上）调用
     */
    public void render(GuiGraphics guiGraphics) {
        if (particles.isEmpty()) return;
        
        PoseStack poseStack = guiGraphics.pose();
        int pSize = 4; 
        for (HudParticle particle : particles) {
            particle.render(poseStack, guiGraphics, pSize);
        }
    }

    /**
     * 触发喷射动画
     * @param centerX 喷射中心X
     * @param centerY 喷射中心Y
     * @param sprite  要喷射的碎屑纹理 (如食物碎屑、方块碎屑)
     * @param count   喷射数量
     */
    public void spawnParticles(float centerX, float centerY, ResourceLocation sprite, int count) {
        for (int i = 0; i < count; i++) {
            // 随机左右飞溅速度
            float vx = ((float)Math.random() - 0.5f) * 6.0f; 
            // 存活时间 15~30 Tick
            int life = 15 + (int)(Math.random() * 15); 
            
            // 起始位置在中心附近轻微偏移
            float sx = centerX + ((float)Math.random() - 0.5f) * 8f;
            float sy = centerY + ((float)Math.random() - 0.5f) * 8f;

            particles.add(new HudParticle(sx, sy, vx, sprite, life));
        }
    }

    /**
     * 使用 Atlas Sprite 触发喷射动画 (方块破坏碎屑)
     */
    public void spawnParticlesAtlas(float centerX, float centerY, TextureAtlasSprite atlasSprite, int count) {
        for (int i = 0; i < count; i++) {
            float vx = ((float)Math.random() - 0.5f) * 6.0f; 
            int life = 15 + (int)(Math.random() * 15); 
            float sx = centerX + ((float)Math.random() - 0.5f) * 8f;
            float sy = centerY + ((float)Math.random() - 0.5f) * 8f;
            particles.add(new HudParticle(sx, sy, vx, atlasSprite, life));
        }
    }
}