package com.jackjackca.teamstatus.client.render.pip;

import com.jackjackca.teamstatus.TeamStatus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

/**
 * Registers the mod's custom picture-in-picture renderers (3D block cube and mob head inside
 * the deferred GUI tree).
 */
@EventBusSubscriber(modid = TeamStatus.MODID, value = Dist.CLIENT)
public final class PipRendererRegistration {

    private PipRendererRegistration() {
    }

    @SubscribeEvent
    public static void onRegisterPipRenderers(RegisterPictureInPictureRenderersEvent event) {
        event.register(BlockInGuiState.class, BlockInGuiRenderer::new);
        event.register(MobHeadGuiState.class, MobHeadGuiRenderer::new);
    }
}
