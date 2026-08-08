package com.example.teamstatus.client.gui;

import com.example.teamstatus.ModConfig;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;


public class HudLayout {
    public static final int PADDING = 4;
    public static final int ICON_SIZE = 9;
    public static final int ENTRY_GAP = 2;
    public static final int ENTRY_HEIGHT = 30;

    public record LayoutParams(int screenWidth, int screenHeight, int avatarSize) {}

    public static LayoutParams computeLayoutParams() {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        int screenWidth = window.getGuiScaledWidth();
        int screenHeight = window.getGuiScaledHeight();
        int avatarSize = ICON_SIZE * 2 + 1; // 血条 + 间隔 + 饥饿条
        return new LayoutParams(screenWidth, screenHeight, avatarSize);
    }

    // 实际内容高度：PADDING + 血条 + 间隔 + 饥饿条
    public static final int CONTENT_HEIGHT = PADDING + ICON_SIZE + 2 + ICON_SIZE; // 24

    public static int getScissorX() {
        return ModConfig.POSITION_X.get();
    }

    public static int getScissorY() {
        return 0;
    }

    public static int getScissorWidth(LayoutParams params) {
        return params.screenWidth() - getScissorX();
    }

    public static int getScissorHeight(LayoutParams params) {
        return params.screenHeight();
    }
}
