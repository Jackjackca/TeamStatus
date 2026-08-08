package com.example.teamstatus.server;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 服务端 Tick 处理器 — 所有状态检测的唯一入口。
 * <p>
 * 事件处理器不再调用 handleStateChange()，避免与 tick handler 的双重检测。
 * 使用服务器原生 tick 计数替代 per-player 递增的静态计数器。
 */
public class ServerTickHandler {

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ServerTickHandler::onPlayerTick);
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // 唯一的差量检测入口
        byte dirty = ServerStateCache.INSTANCE.updateAndGetDirty(player);
        if (dirty != 0) {
            ServerHandler.broadcastPlayerStatus(player, dirty);
        }

        // 使用服务器原生 tick 计数，每 200 tick（10 秒）发送全量心跳兜底
        if (player.getServer() != null && player.getServer().getTickCount() % 200 == 0) {
            ServerHandler.broadcastPlayerStatusFull(player);
        }
    }
}
