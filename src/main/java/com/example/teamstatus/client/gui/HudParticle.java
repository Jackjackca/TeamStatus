package com.example.teamstatus.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import com.mojang.math.Axis;

@SuppressWarnings("null")
public class HudParticle {
    public float x, y;
    private float vx, vy;
    private final float gravity = 0.6f; 
    private int life;
    private final int maxLife;
    private float rotation;
    private float vRot;
    private ResourceLocation sprite;
    private net.minecraft.client.renderer.texture.TextureAtlasSprite atlasSprite;
    private float alpha = 1.0f;

    public HudParticle(float x, float y, float initialVx, ResourceLocation sprite, int maxLife) {
        this.x = x;
        this.y = y;
        this.vx = initialVx;
        this.vy = -2.0f + (float)Math.random() * -3.0f; 
        this.sprite = sprite;
        this.atlasSprite = null;
        this.life = 0;
        this.maxLife = maxLife;
        this.rotation = (float)Math.random() * 360.0f;
        this.vRot = ((float)Math.random() - 0.5f) * 20.0f;
    }

    public HudParticle(float x, float y, float initialVx, net.minecraft.client.renderer.texture.TextureAtlasSprite atlasSprite, int maxLife) {
        this.x = x;
        this.y = y;
        this.vx = initialVx;
        this.vy = -2.0f + (float)Math.random() * -3.0f; 
        this.sprite = null;
        this.atlasSprite = atlasSprite;
        this.life = 0;
        this.maxLife = maxLife;
        this.rotation = (float)Math.random() * 360.0f;
        this.vRot = ((float)Math.random() - 0.5f) * 20.0f;
    }

    public boolean tick() {
        this.life++;
        if (this.life > this.maxLife) return true; // 返回 true 表示需要被移除

        this.x += this.vx;
        this.y += this.vy;
        this.vy += this.gravity;
        this.rotation += this.vRot;

        // 空气阻力
        this.vx *= 0.90f;

        // 生命周期后半段开始渐隐
        float progress = (float)this.life / this.maxLife;
        if (progress > 0.5f) {
            this.alpha = Mth.lerp((progress - 0.5f) * 2.0f, 1.0f, 0.0f);
        }

        return false;
    }

    public void render(PoseStack poseStack, GuiGraphics guiGraphics, int size) {
        if (this.alpha <= 0.05f) return;

        poseStack.pushPose();
        
        // 移动到粒子中心，准备旋转
        poseStack.translate(this.x + size / 2f, this.y + size / 2f, 0);
        poseStack.mulPose(Axis.ZP.rotationDegrees(this.rotation));
        poseStack.translate(-size / 2f, -size / 2f, 0);

        // 设置透明度并渲染
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, this.alpha);
        
        if (this.atlasSprite != null) {
            guiGraphics.blit(0, 0, 0, size, size, this.atlasSprite);
        } else if (this.sprite != null) {
            guiGraphics.blit(this.sprite, 0, 0, 0, 0, size, size, size, size);
        }
        
        // 恢复颜色和矩阵状态
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        poseStack.popPose();
    }
}