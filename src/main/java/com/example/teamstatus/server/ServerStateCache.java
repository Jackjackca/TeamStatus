package com.example.teamstatus.server;

import com.example.teamstatus.client.gui.HeartType;
import com.example.teamstatus.network.PlayerStatusPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端玩家状态缓存
 * <p>
 * 维护所有在线玩家的最新状态快照，用于差量检测。
 * 仅在状态实际变化时分配新快照（惰性分配），消除每 tick 的无谓对象创建。
 */
@SuppressWarnings("null")
public class ServerStateCache {
    public static final ServerStateCache INSTANCE = new ServerStateCache();

    private final Map<UUID, PlayerStateSnapshot> cache = new ConcurrentHashMap<>();

    public record PlayerStateSnapshot(
        UUID playerId,
        String playerName,
        float health,
        float maxHealth,
        int hunger,
        float saturation,
        float exhaustion,
        float absorption,
        ItemStack mainHandItem,
        boolean isUsingItem,
        int useDuration,
        int useRemaining,
        byte heartType,
        boolean hasRegeneration,
        boolean hasHungerEffect,
        boolean isHardcore
    ) {}

    private ServerStateCache() {}

    /**
     * 更新状态并返回脏位掩码。仅在有变化时分配新快照。
     */
    public byte updateAndGetDirty(ServerPlayer player) {
        UUID id = player.getUUID();
        PlayerStateSnapshot current = cache.get(id);

        if (current == null) {
            cache.put(id, buildSnapshot(player));
            return PlayerStatusPayload.FULL_STATUS;
        }

        byte dirty = computeDirty(current, player);
        if (dirty != 0) {
            cache.put(id, buildSnapshot(player)); // 仅 dirty 时分配
        }
        return dirty;
    }

    public PlayerStateSnapshot getSnapshot(UUID id) {
        return cache.get(id);
    }

    public void remove(UUID id) {
        cache.remove(id);
    }

    public void put(UUID id, PlayerStateSnapshot snapshot) {
        cache.put(id, snapshot);
    }

    public Map<UUID, PlayerStateSnapshot> getAllSnapshots() {
        return Map.copyOf(cache);
    }

    public PlayerStateSnapshot buildSnapshot(ServerPlayer player) {
        return new PlayerStateSnapshot(
            player.getUUID(),
            player.getName().getString(),
            player.getHealth(),
            player.getMaxHealth(),
            player.getFoodData().getFoodLevel(),
            player.getFoodData().getSaturationLevel(),
            player.getFoodData().getExhaustionLevel(),
            player.getAbsorptionAmount(),
            player.getMainHandItem().copy(),
            player.isUsingItem(),
            player.isUsingItem() ? player.getUseItem().getUseDuration(player) : 0,
            player.getUseItemRemainingTicks(),
            HeartType.fromPlayerState(player).getId(),
            player.hasEffect(MobEffects.REGENERATION),
            player.hasEffect(MobEffects.HUNGER),
            player.level().getLevelData().isHardcore()
        );
    }

    /**
     * 直接从缓存快照与当前 ServerPlayer 比较，跳过中间 snapshot 创建。
     */
    private byte computeDirty(PlayerStateSnapshot old, ServerPlayer p) {
        byte dirty = 0;

        if (Float.compare(old.health(), p.getHealth()) != 0 ||
            Float.compare(old.maxHealth(), p.getMaxHealth()) != 0) {
            dirty |= PlayerStatusPayload.HEALTH;
        }

        if (old.hunger() != p.getFoodData().getFoodLevel() ||
            Float.compare(old.saturation(), p.getFoodData().getSaturationLevel()) != 0 ||
            Float.compare(old.exhaustion(), p.getFoodData().getExhaustionLevel()) != 0) {
            dirty |= PlayerStatusPayload.HUNGER;
        }

        if (old.isUsingItem() != p.isUsingItem() ||
            old.useDuration() != (p.isUsingItem() ? p.getUseItem().getUseDuration(p) : 0) ||
            old.useRemaining() != p.getUseItemRemainingTicks()) {
            dirty |= PlayerStatusPayload.USE;
        }

        if (!ItemStack.isSameItemSameComponents(old.mainHandItem(), p.getMainHandItem())) {
            dirty |= PlayerStatusPayload.ITEM;
        }

        if (old.heartType() != HeartType.fromPlayerState(p).getId() ||
            Float.compare(old.absorption(), p.getAbsorptionAmount()) != 0 ||
            old.hasRegeneration() != p.hasEffect(MobEffects.REGENERATION) ||
            old.hasHungerEffect() != p.hasEffect(MobEffects.HUNGER)) {
            dirty |= PlayerStatusPayload.EFFECTS;
        }

        // isHardcore 是世界属性，永不变化，不参与比较

        return dirty;
    }
}
