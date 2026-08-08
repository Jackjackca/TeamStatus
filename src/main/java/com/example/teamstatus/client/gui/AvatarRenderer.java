package com.example.teamstatus.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.example.teamstatus.TeamMember;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * 头像渲染器 — 数据与渲染完全解耦。
 * <p>
 * computeState() 在 20Hz tick 阶段缓存皮肤查找结果（消除每帧线性扫描）。
 * render() 仅执行 fill / blit / drawString 调用。
 */
@SuppressWarnings("null")
public class AvatarRenderer {

    /**
     * 20Hz 预计算：缓存皮肤纹理、回退颜色、首字母、闪烁 tint。
     */
    public static void computeState(TeamMember member, AvatarRenderState state) {
        state.skinTexture = resolveSkinTexture(member);

        if (state.skinTexture == null) {
            state.fallbackColor = computeFallbackColor(member);
            String name = member.getPlayerName();
            state.initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        }

        state.tint = member.getAvatarTint();
    }

    /**
     * 极简渲染 — 读取预计算状态，执行 fill / blit / drawString。
     */
    public static void render(GuiGraphics guiGraphics, TeamMember member, int x, int y, int size) {
        AvatarRenderState state = member.getAvatarState();

        if (state.skinTexture != null) {
            // 真实玩家头像
            guiGraphics.fill(x, y, x + size, y + size, 0xFF000000);
            guiGraphics.blit(state.skinTexture, x, y, size, size, 8.0f, 8.0f, 8, 8, 64, 64);
        } else {
            // 回退：颜色方块 + 首字母
            Minecraft minecraft = Minecraft.getInstance();
            guiGraphics.fill(x, y, x + size, y + size, 0xFF555555);

            guiGraphics.fill(x, y, x + 1, y + size, 0xFFFFFFFF);
            guiGraphics.fill(x, y, x + size, y + 1, 0xFFFFFFFF);
            guiGraphics.fill(x + size - 1, y, x + size, y + size, 0xFFFFFFFF);
            guiGraphics.fill(x, y + size - 1, x + size, y + size, 0xFFFFFFFF);

            guiGraphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, state.fallbackColor);

            int textWidth = minecraft.font.width(state.initial);
            guiGraphics.drawString(minecraft.font, state.initial,
                    x + size / 2 - textWidth / 2, y + size / 2 - 3, 0x000000, false);
        }

        // 闪烁覆盖层
        if (state.tint == TeamMember.AVATAR_TINT_HURT) {
            RenderSystem.enableBlend();
            guiGraphics.fill(x, y, x + size, y + size, 0x80FF0000);
            RenderSystem.disableBlend();
        } else if (state.tint == TeamMember.AVATAR_TINT_HEAL) {
            RenderSystem.enableBlend();
            guiGraphics.fill(x, y, x + size, y + size, 0x4000FF00);
            RenderSystem.disableBlend();
        }
    }

    // === 内部方法 ===

    private static ResourceLocation resolveSkinTexture(TeamMember member) {
        Minecraft minecraft = Minecraft.getInstance();
        var localPlayer = minecraft.player;
        if (localPlayer == null || minecraft.getConnection() == null) return null;

        PlayerSkin skin = null;
        if (localPlayer.getUUID().equals(member.getPlayerId())) {
            skin = localPlayer.getSkin();
        } else {
            for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
                if (info.getProfile().getId().equals(member.getPlayerId())) {
                    skin = info.getSkin();
                    break;
                }
            }
        }
        return skin != null ? skin.texture() : null;
    }

    private static int computeFallbackColor(TeamMember member) {
        java.util.UUID uuid = member.getPlayerId();
        if (uuid == null) return 0xFF8B4513;
        int hash = uuid.hashCode();
        int r = Math.max((hash & 0xFF0000) >> 16, 100);
        int g = Math.max((hash & 0x00FF00) >> 8, 100);
        int b = Math.max(hash & 0x0000FF, 100);
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }
}
