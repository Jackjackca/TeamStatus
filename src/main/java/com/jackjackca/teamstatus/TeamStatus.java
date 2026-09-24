package com.jackjackca.teamstatus;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.jackjackca.teamstatus.common.network.NetworkHandler;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * TeamStatus main entry point.
 * <p>
 * Synchronizes party members' health, food/saturation/exhaustion, armor, status effects and
 * held items regardless of distance or dimension. Party data comes from Open Parties and
 * Claims (optional) and rendering is client-side only.
 */
@Mod(TeamStatus.MODID)
public final class TeamStatus {

    public static final String MODID = "teamstatus";

    public static final Logger LOGGER = LogUtils.getLogger();

    public TeamStatus(IEventBus modEventBus, ModContainer modContainer) {
        // 1.21 custom payload registration (CustomPacketPayload + RegisterPayloadHandlersEvent)
        modEventBus.addListener(NetworkHandler::onRegisterPayloadHandlers);

        // Server / game-bus listeners are picked up automatically from ServerEventHandler
        // via @EventBusSubscriber; client-only listeners live in TeamStatusClient.

        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
    }
}
