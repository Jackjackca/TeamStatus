package com.example.teamstatus.client.gui;

import com.example.teamstatus.ModConfig;
import com.example.teamstatus.TeamMember;
import com.example.teamstatus.TeamStatus;
import com.example.teamstatus.client.TeamTracker;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.util.Collection;

@SuppressWarnings("null")
public class TeamStatusHud {
    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
        "key.teamstatus.openConfig",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_RIGHT_CONTROL,
        "key.categories.teamstatus"
    );

    public static void init() {
        NeoForge.EVENT_BUS.addListener(TeamStatusHud::onRenderGui);
        NeoForge.EVENT_BUS.addListener(TeamStatusHud::onClientTick);
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONFIG);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        HudParticleManager.INSTANCE.tick();

        if (OPEN_CONFIG.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new ConfigurationScreen(TeamStatus.getModContainer(), null));
        }
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        HudLayout.LayoutParams params = HudLayout.computeLayoutParams();
        boolean showSelf = ModConfig.SHOW_SELF.get();
        Collection<TeamMember> members = TeamTracker.getTeamMembers();
        if (members.isEmpty()) return;

        // scissor 裁剪
        int scissorX = HudLayout.getScissorX();
        int scissorY = HudLayout.getScissorY();
        int scissorW = HudLayout.getScissorWidth(params);
        int scissorH = HudLayout.getScissorHeight(params);
        guiGraphics.enableScissor(scissorX, scissorY, scissorX + scissorW, scissorY + scissorH);

        // PoseStack 变换
        var poseStack = guiGraphics.pose();
        poseStack.pushPose();

        int baseX = ModConfig.POSITION_X.get();
        int baseY = params.screenHeight() - ModConfig.POSITION_Y.get();
        int visibleIndex = 0;

        for (TeamMember member : members) {
            if (!showSelf && TeamTracker.isLocalPlayer(member.getPlayerId())) {
                continue;
            }

            // 内联 computeEntryPosition，消除每帧 int[] 分配
            int x = baseX;
            int y = baseY - HudLayout.CONTENT_HEIGHT - visibleIndex * (HudLayout.ENTRY_HEIGHT + HudLayout.ENTRY_GAP);
            visibleIndex++;

            if (y + HudLayout.ENTRY_HEIGHT < 0) continue;

            renderTeamMember(guiGraphics, member, x, y, params, partialTick);
        }

        poseStack.popPose();
        guiGraphics.disableScissor();

        // 粒子渲染在 scissor 之外 (不受 HUD 裁剪区域限制)
        HudParticleManager.INSTANCE.render(guiGraphics);
    }

    private static void renderTeamMember(GuiGraphics guiGraphics, TeamMember member, int x, int y, HudLayout.LayoutParams params, float partialTick) {
        int avatarX = x;
        int avatarY = y + HudLayout.PADDING;
        AvatarRenderer.render(guiGraphics, member, avatarX, avatarY, params.avatarSize());

        int statusX = avatarX + params.avatarSize() + HudLayout.PADDING;
        int statusY = y + HudLayout.PADDING;

        HealthBarRenderer.render(guiGraphics, member, statusX, statusY);
        HungerBarRenderer.render(guiGraphics, member, statusX, statusY + HudLayout.ICON_SIZE + 2);

        // 物品显示：仅远程玩家
        if (!TeamTracker.isLocalPlayer(member.getPlayerId())) {
            int heartCount = (int) Math.min(Math.ceil(member.getMaxHealth() / 2.0f), 10);
            int barsWidth = Math.max(heartCount, 10) * 8;
            int itemX = statusX + barsWidth + HudLayout.PADDING;
            int itemY = statusY + 2;
            UseStatusRenderer.render(guiGraphics, member, itemX, itemY, partialTick);
        }
    }
}
