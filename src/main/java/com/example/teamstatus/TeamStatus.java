package com.example.teamstatus;

import com.example.teamstatus.client.TeamTracker;
import com.example.teamstatus.client.gui.TeamStatusHud;
import com.example.teamstatus.network.NetworkHandler;
import com.example.teamstatus.server.ServerHandler;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

@Mod(TeamStatus.MOD_ID)
public class TeamStatus {
    public static final String MOD_ID = "teamstatus";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static net.neoforged.fml.ModContainer modContainer;

    public TeamStatus(IEventBus eventBus, net.neoforged.fml.ModContainer container) {
        LOGGER.info("TeamStatus mod loading...");

        modContainer = container;
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, ModConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        // 客户端初始化
        eventBus.addListener(this::clientSetup);
        eventBus.addListener(this::registerPayloadHandlers);
        eventBus.addListener(TeamStatusHud::registerKeyMappings);
        TeamTracker.init();

        // 服务端初始化
        ServerHandler.init();
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("TeamStatus client setup complete");
        TeamStatusHud.init();
    }

    private void registerPayloadHandlers(final RegisterPayloadHandlersEvent event) {
        LOGGER.info("TeamStatus registering network handlers");
        NetworkHandler.registerPayloads(event);
    }

    public static net.neoforged.fml.ModContainer getModContainer() {
        return modContainer;
    }
}
