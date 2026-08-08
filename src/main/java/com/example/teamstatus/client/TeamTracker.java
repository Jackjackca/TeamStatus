package com.example.teamstatus.client;

import com.example.teamstatus.TeamMember;
import com.example.teamstatus.network.AttackActionPayload;
import com.example.teamstatus.network.EatingActionPayload;
import com.example.teamstatus.network.MiningActionPayload;
import com.example.teamstatus.network.PlayerStatusPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 客户端团队状态管理器
 * 仅负责接收和显示远程玩家状态，不主动上报
 */
public class TeamTracker {
    private static final Map<UUID, TeamMember> teamMembers = new HashMap<>();
    private static UUID localPlayerId = null;

    public static void init() {
        // 仅保留渲染相关的 Tick 处理（驱动动画）
        NeoForge.EVENT_BUS.addListener(TeamTracker::onRenderTick);
        // 保留玩家离开事件处理
        NeoForge.EVENT_BUS.addListener(TeamTracker::onPlayerLoggedOut);
    }

    private static void ensureLocalPlayerRegistered() {
        Minecraft minecraft = Minecraft.getInstance();
        var localPlayer = minecraft.player;
        if (localPlayer == null) return;

        UUID currentId = localPlayer.getUUID();
        if (!currentId.equals(localPlayerId)) {
            localPlayerId = currentId;
        }
    }

    /**
     * 渲染 Tick - 驱动客户端本地动画
     */
    public static void onRenderTick(PlayerTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;

        ensureLocalPlayerRegistered();

        // 使用原生 GUI Ticks 保持与 Minecraft 渲染系统同步
        int guiTicks = minecraft.gui.getGuiTicks();

        // 每 tick 驱动所有成员的动画计时器 + 预计算心形渲染状态
        for (TeamMember m : teamMembers.values()) {
            m.tickAnimations(guiTicks);
        }
    }

    // === 远程玩家状态接收 ===

    /**
     * 更新远程玩家状态（由服务端推送触发）
     */
    public static void updateRemotePlayer(UUID playerId, PlayerStatusPayload payload) {
        // 跳过本地玩家的更新（服务端也会广播给所有人）
        if (playerId.equals(localPlayerId)) {
            return;
        }

        TeamMember member = teamMembers.get(playerId);
        if (member == null) {
            member = new TeamMember(payload.playerId(), payload.playerName());
            teamMembers.put(playerId, member);
        }
        member.updateFromPayload(payload);
    }

    public static void removeRemotePlayer(UUID playerId) {
        teamMembers.remove(playerId);
    }

    public static boolean isLocalPlayer(UUID uuid) {
        return uuid != null && uuid.equals(localPlayerId);
    }

    public static Collection<TeamMember> getTeamMembers() {
        return Collections.unmodifiableCollection(teamMembers.values());
    }

    public static TeamMember getTeamMember(UUID uuid) {
        return teamMembers.get(uuid);
    }

    public static boolean hasTeamMembers() {
        return !teamMembers.isEmpty();
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
            UUID uuid = player.getUUID();
            if (!uuid.equals(localPlayerId)) {
                removeRemotePlayer(uuid);
            } else {
                teamMembers.clear();
                localPlayerId = null;
            }
        }
    }

    public static void updateMiningAction(UUID playerId, MiningActionPayload payload) {
        if (playerId.equals(localPlayerId)) return;
        
        TeamMember member = teamMembers.get(playerId);
        if (member != null) {
            int guiTicks = Minecraft.getInstance().gui.getGuiTicks();
            member.updateMiningState(payload.targetBlock(), payload.mineProgress(), payload.isActive(), guiTicks);
        }
    }

    public static void updateAttackAction(UUID playerId, AttackActionPayload payload) {
        if (playerId.equals(localPlayerId)) return;
        
        TeamMember member = teamMembers.get(playerId);
        if (member != null) {
            int guiTicks = Minecraft.getInstance().gui.getGuiTicks();
            member.updateAttackState(payload.targetEntity(), payload.isActive(), guiTicks);
        }
    }

    public static void updateEatingAction(UUID playerId, EatingActionPayload payload) {
        if (playerId.equals(localPlayerId)) return;
        
        TeamMember member = teamMembers.get(playerId);
        if (member != null) {
            int guiTicks = Minecraft.getInstance().gui.getGuiTicks();
            member.updateEatingState(payload.actionDuration(), payload.isActive(), guiTicks);
        }
    }
}
