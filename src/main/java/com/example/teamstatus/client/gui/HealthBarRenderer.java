package com.example.teamstatus.client.gui;

import com.example.teamstatus.TeamMember;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 心形血条渲染器 — 重构后为纯 O(1) 读取 + blitSprite 绘制。
 * <p>
 * 所有业务逻辑（闪烁判断、抖动计算、纹理选择、差值推导）已下沉至
 * {@link TeamMember#tickAnimations(int)} 的 20Hz 预计算阶段，
 * 渲染阶段零 Math.ceil / 零 ResourceLocation 创建 / 零 Random 调用。
 */
@SuppressWarnings("null")
public class HealthBarRenderer {

    /**
     * 极简渲染入口 — 仅遍历预计算缓存，执行 blitSprite 绘制。
     *
     * @param guiGraphics Minecraft GuiGraphics
     * @param member      包含预计算心形状态的队员数据
     * @param x           血条起始 X 坐标
     * @param y           血条起始 Y 坐标
     */
    public static void render(GuiGraphics guiGraphics, TeamMember member, int x, int y) {
        HeartRenderState[] states = member.getHeartStates();
        int count = member.getActiveHeartCount();
        if (count == 0) return;

        // === 第 1-2 层：容器背景 + 闪烁白边 + 前景填充 ===
        // 从后向前遍历（高索引先绘制），保证低索引心形覆盖高索引的 1px 重叠区域
        for (int i = count - 1; i >= 0; i--) {
            HeartRenderState s = states[i];
            int heartX = x + (i % 10) * 8;
            int heartY = y - (i / 10) * 11 + s.yOffset;

            // 第 1 层：容器背景（CONTAINER 或 CONTAINER_BLINK）
            guiGraphics.blitSprite(s.container, heartX, heartY,
                    HudLayout.ICON_SIZE, HudLayout.ICON_SIZE);

            // 第 2 层：前景填充（null = 空心，跳过）
            if (s.fill != null) {
                guiGraphics.blitSprite(s.fill, heartX, heartY,
                        HudLayout.ICON_SIZE, HudLayout.ICON_SIZE);
            }
        }

        // === 第 3 层：受伤/回血差值半透明覆盖 ===
        // 从前向后遍历（与原版一致），仅在有差值时启用 blend 状态
        boolean blendActive = false;
        for (int i = 0; i < count; i++) {
            HeartRenderState s = states[i];
            if (s.diff == null) continue;

            if (!blendActive) {
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.5F);
                blendActive = true;
            }

            // 差值层使用基础 Y 坐标（不应用 yOffset，与原版一致）
            int heartX = x + (i % 10) * 8;
            int heartY = y - (i / 10) * 11;
            guiGraphics.blitSprite(s.diff, heartX, heartY,
                    HudLayout.ICON_SIZE, HudLayout.ICON_SIZE);
        }

        if (blendActive) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }
    }
}
