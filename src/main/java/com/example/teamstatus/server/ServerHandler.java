package com.example.teamstatus.server;

import com.example.teamstatus.TeamStatus;
import com.example.teamstatus.client.gui.HeartType;
import com.example.teamstatus.network.AttackActionPayload;
import com.example.teamstatus.network.EatingActionPayload;
import com.example.teamstatus.network.MiningActionPayload;
import com.example.teamstatus.network.PlayerStatusPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端状态管理核心
 * <p>
 * 统一管理玩家状态缓存、生命周期事件响应、网络广播。
 */
@SuppressWarnings("null")
public class ServerHandler {

    private static final ServerStateCache stateCache = ServerStateCache.INSTANCE;

    public static void init() {
        ServerEventHandler.register();
        ServerTickHandler.register();
    }

    // === 生命周期事件处理 ===

    public static void handlePlayerJoin(ServerPlayer player) {
        TeamStatus.LOGGER.info("Player {} joined, syncing all player status", player.getName().getString());

        // 向新玩家发送所有已缓存的玩家状态
        for (Map.Entry<UUID, ServerStateCache.PlayerStateSnapshot> entry : stateCache.getAllSnapshots().entrySet()) {
            if (!entry.getKey().equals(player.getUUID())) {
                PacketDistributor.sendToPlayer(player, buildFullPayloadFromSnapshot(entry.getValue()));
            }
        }

        // 初始化缓存并广播新玩家状态
        ServerStateCache.PlayerStateSnapshot snapshot = stateCache.buildSnapshot(player);
        stateCache.put(player.getUUID(), snapshot);
        PacketDistributor.sendToAllPlayers(buildFullPayloadFromSnapshot(snapshot));
    }

    public static void handlePlayerLeave(ServerPlayer player) {
        TeamStatus.LOGGER.info("Player {} left, removing from cache", player.getName().getString());
        stateCache.remove(player.getUUID());
    }

    public static void handlePlayerRespawn(ServerPlayer player) {
        TeamStatus.LOGGER.debug("Player {} respawned, broadcasting status", player.getName().getString());
        
        // 清除旧缓存，构建新快照并广播
        stateCache.remove(player.getUUID());
        ServerStateCache.PlayerStateSnapshot snapshot = stateCache.buildSnapshot(player);
        stateCache.put(player.getUUID(), snapshot);
        PacketDistributor.sendToAllPlayers(buildFullPayloadFromSnapshot(snapshot));
    }

    public static void handlePlayerDeath(ServerPlayer player) {
        TeamStatus.LOGGER.debug("Player {} died, broadcasting death status", player.getName().getString());

        // 构建死亡状态快照（强制血量为 0）
        ServerStateCache.PlayerStateSnapshot deathSnap = new ServerStateCache.PlayerStateSnapshot(
            player.getUUID(),
            player.getName().getString(),
            0.0f,  // 强制血量为 0
            player.getMaxHealth(),
            player.getFoodData().getFoodLevel(),
            player.getFoodData().getSaturationLevel(),
            player.getFoodData().getExhaustionLevel(),
            0.0f,  // 死亡时无吸收
            player.getMainHandItem().copy(),
            false,
            0, 0,
            HeartType.NORMAL.getId(),
            false, false,
            player.level().getLevelData().isHardcore()
        );
        stateCache.put(player.getUUID(), deathSnap);
        PacketDistributor.sendToAllPlayers(buildFullPayloadFromSnapshot(deathSnap));
    }

    /**
     * 处理状态变化事件（由事件监听器调用）
     */
    public static void handleStateChange(ServerPlayer player) {
        byte dirty = stateCache.updateAndGetDirty(player);
        if (dirty != 0) {
            broadcastPlayerStatus(player, dirty);
        }
    }

    // === 广播 ===

    public static void broadcastPlayerStatus(ServerPlayer player, byte dirty) {
        ServerStateCache.PlayerStateSnapshot snapshot = stateCache.getSnapshot(player.getUUID());
        if (snapshot == null) {
            // 缓存不存在时发送全量
            snapshot = stateCache.buildSnapshot(player);
            stateCache.put(player.getUUID(), snapshot);
            PacketDistributor.sendToAllPlayers(buildFullPayloadFromSnapshot(snapshot));
        } else {
            PacketDistributor.sendToAllPlayers(buildDirtyPayload(dirty, snapshot));
        }
    }

    public static void broadcastPlayerStatusFull(ServerPlayer player) {
        ServerStateCache.PlayerStateSnapshot snapshot = stateCache.getSnapshot(player.getUUID());
        if (snapshot == null) {
            snapshot = stateCache.buildSnapshot(player);
            stateCache.put(player.getUUID(), snapshot);
        }
        PacketDistributor.sendToAllPlayers(buildFullPayloadFromSnapshot(snapshot));
    }

    // === Payload 构建 ===

    private static PlayerStatusPayload buildDirtyPayload(byte dirty, ServerStateCache.PlayerStateSnapshot s) {
        return new PlayerStatusPayload(
            dirty, s.playerId(), s.playerName(),
            (dirty & PlayerStatusPayload.HEALTH)  != 0 ? s.health()      : 0,
            (dirty & PlayerStatusPayload.HEALTH)  != 0 ? s.maxHealth()    : 20,
            (dirty & PlayerStatusPayload.HUNGER)  != 0 ? s.hunger()       : 20,
            (dirty & PlayerStatusPayload.HUNGER)  != 0 ? s.saturation()   : 0,
            (dirty & PlayerStatusPayload.HUNGER)  != 0 ? s.exhaustion()   : 0,
            (dirty & PlayerStatusPayload.USE)     != 0 ? s.isUsingItem()  : false,
            (dirty & PlayerStatusPayload.ITEM)    != 0 ? s.mainHandItem() : ItemStack.EMPTY,
            (dirty & PlayerStatusPayload.USE)     != 0 ? s.useDuration()  : 0,
            (dirty & PlayerStatusPayload.USE)     != 0 ? s.useRemaining() : 0,
            (dirty & PlayerStatusPayload.EFFECTS) != 0 ? s.heartType()    : HeartType.NORMAL.getId(),
            (dirty & PlayerStatusPayload.EFFECTS) != 0 ? s.absorption()   : 0,
            false,
            (dirty & PlayerStatusPayload.EFFECTS) != 0 ? s.hasRegeneration() : false,
            (dirty & PlayerStatusPayload.EFFECTS) != 0 ? s.hasHungerEffect() : false
        );
    }

    private static PlayerStatusPayload buildFullPayloadFromSnapshot(ServerStateCache.PlayerStateSnapshot s) {
        return PlayerStatusPayload.full(
            s.playerId(), s.playerName(),
            s.health(), s.maxHealth(),
            s.hunger(), s.saturation(), s.exhaustion(),
            s.isUsingItem(), s.mainHandItem(),
            s.useDuration(), s.useRemaining(),
            s.heartType(), s.absorption(),
            s.isHardcore(), s.hasRegeneration(), s.hasHungerEffect()
        );
    }

    // === 动作事件处理 ===

    public static void handleMiningStart(ServerPlayer player, BlockState targetBlock) {
        MiningActionPayload payload = MiningActionPayload.start(player.getUUID(), targetBlock);
        PacketDistributor.sendToAllPlayers(payload);
    }

    public static void handleMiningStop(ServerPlayer player) {
        MiningActionPayload payload = MiningActionPayload.stop(player.getUUID());
        PacketDistributor.sendToAllPlayers(payload);
    }
    
    public static void handleMiningProgress(ServerPlayer player, BlockState blockState, float playerSpeed, @Nullable BlockPos pos) {
        // 与原版 BlockBehaviour.getDestroyProgress() 一致的公式
        BlockPos blockPos = pos != null ? pos : BlockPos.ZERO;
        float blockHardness = blockState.getDestroySpeed(player.level(), blockPos);
        if (blockHardness <= 0) blockHardness = 0.01f; // 不可破坏方块返回0进度
        boolean correctTool = !blockState.requiresCorrectToolForDrops() || player.hasCorrectToolForDrops(blockState);
        int toolFactor = correctTool ? 30 : 100;
        float perTickProgress = playerSpeed / blockHardness / toolFactor;
        MiningActionPayload payload = MiningActionPayload.progress(player.getUUID(), blockState, perTickProgress);
        PacketDistributor.sendToAllPlayers(payload);
    }

    public static void handleAttack(ServerPlayer player, EntityType<?> targetEntity) {
        AttackActionPayload payload = AttackActionPayload.hit(player.getUUID(), targetEntity);
        PacketDistributor.sendToAllPlayers(payload);
    }

    public static void handleEatingStart(ServerPlayer player, int duration) {
        EatingActionPayload payload = EatingActionPayload.start(player.getUUID(), duration);
        PacketDistributor.sendToAllPlayers(payload);
    }

    public static void handleEatingStop(ServerPlayer player) {
        EatingActionPayload payload = EatingActionPayload.stop(player.getUUID());
        PacketDistributor.sendToAllPlayers(payload);
    }
}