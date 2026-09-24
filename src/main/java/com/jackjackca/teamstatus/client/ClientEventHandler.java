package com.jackjackca.teamstatus.client;

import com.jackjackca.teamstatus.TeamStatus;
import com.jackjackca.teamstatus.client.action.HandActionStates;
import com.jackjackca.teamstatus.client.action.LocalActionFeed;
import com.jackjackca.teamstatus.client.render.HitIconRenderer;
import com.jackjackca.teamstatus.client.render.ParticleRenderer;
import com.jackjackca.teamstatus.client.state.ClientHealthFxState;
import com.jackjackca.teamstatus.client.state.ClientTeamState;
import com.jackjackca.teamstatus.client.state.LocalSelfState;
import com.jackjackca.teamstatus.client.visual.MemberPanelCache;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Client game-bus events: advances the floating-text particles and the hand action state
 * machines every client tick, and clears all cached team state when the player disconnects
 * (memory-leak prevention).
 */
@EventBusSubscriber(modid = TeamStatus.MODID, value = Dist.CLIENT)
public final class ClientEventHandler {

    private ClientEventHandler() {
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        ParticleRenderer.INSTANCE.clientTick();
        HandActionStates.clientTick();
        // Local self feed runs after the generic tick so its authoritative elapsed values win.
        LocalActionFeed.clientTick();
        // Synthesize the local player's own panel state from the authoritative client entity.
        LocalSelfState.publish();
    }

    /** Client-side mining edges for the local player's own panel (no network involved). */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        LocalActionFeed.onLeftClickBlock(event);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientTeamState.clear();
        ParticleRenderer.INSTANCE.clear();
        HandActionStates.clear();
        ClientHealthFxState.clear();
        MemberPanelCache.clear();
        HitIconRenderer.clearTempEntities();
    }
}
