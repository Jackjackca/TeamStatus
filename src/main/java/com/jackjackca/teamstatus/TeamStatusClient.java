package com.jackjackca.teamstatus;

import com.jackjackca.teamstatus.client.render.TeamHudRenderer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only entry point. Not loaded on dedicated servers. */
@Mod(value = TeamStatus.MODID, dist = Dist.CLIENT)
public final class TeamStatusClient {

    public TeamStatusClient(IEventBus modEventBus, ModContainer container) {
        // Register the custom HUD layer (RegisterGuiLayersEvent, GuiGraphics based rendering).
        modEventBus.addListener(TeamHudRenderer::register);

        // Mods screen -> config screen for the CLIENT config.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
