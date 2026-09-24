package com.jackjackca.teamstatus.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jackjackca.teamstatus.client.action.HandActionStates;
import com.jackjackca.teamstatus.client.render.ParticleRenderer;
import com.jackjackca.teamstatus.client.state.ClientHealthFxState;
import com.jackjackca.teamstatus.client.state.ClientTeamState;
import com.jackjackca.teamstatus.client.state.LocalSelfState;
import com.jackjackca.teamstatus.client.visual.MemberPanelCache;
import net.minecraft.client.Minecraft;
import com.jackjackca.teamstatus.common.network.AttackActionPacket;
import com.jackjackca.teamstatus.common.network.ItemUseActionPacket;
import com.jackjackca.teamstatus.common.network.MiningActionPacket;
import com.jackjackca.teamstatus.common.network.TeamStatePacket;
import com.jackjackca.teamstatus.common.state.TeamMemberState;

/**
 * Client-side payload entry point. Every method here runs on the client main thread because
 * {@code NetworkHandler} wraps the call in {@code context.enqueueWork(...)}.
 * <p>
 * The snapshot fully replaces {@link ClientTeamState}; floating damage/heal particles are
 * emitted by diffing the new snapshot against the cached one before the replacement.
 */
public final class ClientPacketHandler {

    private static final int HEAL_COLOR = 0xFF55FF55;
    private static final int DAMAGE_COLOR = 0xFFFF5555;
    private static final float HEALTH_CHANGE_THRESHOLD = 0.5F;

    private ClientPacketHandler() {
    }

    public static void handle(TeamStatePacket packet) {
        List<TeamMemberState> members = packet.members();
        spawnHealthChangeParticles(members);
        ClientHealthFxState.update(members);
        ClientTeamState.replace(members);

        // The local player is never part of the server snapshot, but their locally fed
        // animations/panels must survive the retain-only cleanup.
        List<UUID> ids = new ArrayList<>(members.size() + 1);
        for (TeamMemberState member : members) {
            ids.add(member.id());
        }
        if (Minecraft.getInstance().player != null) {
            ids.add(Minecraft.getInstance().player.getUUID());
        }
        ParticleRenderer.INSTANCE.retainOnly(ids);
        HandActionStates.retainOnly(ids);
        ClientHealthFxState.retainOnly(ids);
        MemberPanelCache.retainOnly(ids);

        // Re-publish self immediately so the frame between packet arrival and the next client
        // tick never shows a missing self panel.
        LocalSelfState.publish();
    }

    public static void handle(MiningActionPacket packet) {
        HandActionStates.onMining(packet);
    }

    public static void handle(AttackActionPacket packet) {
        HandActionStates.onAttack(packet);
    }

    public static void handle(ItemUseActionPacket packet) {
        HandActionStates.onItemUse(packet);
    }

    /**
     * Spawns the floating damage/heal numbers by comparing health against the currently
     * cached snapshot. Members absent from the cache are skipped, so the first snapshot of
     * a member never produces feedback.
     */
    private static void spawnHealthChangeParticles(List<TeamMemberState> snapshot) {
        Map<UUID, Float> previousHealth = new HashMap<>();
        for (TeamMemberState member : ClientTeamState.getMembers()) {
            previousHealth.put(member.id(), member.health());
        }
        for (TeamMemberState member : snapshot) {
            Float previous = previousHealth.get(member.id());
            if (previous == null) {
                continue;
            }
            float delta = member.health() - previous;
            if (Math.abs(delta) >= HEALTH_CHANGE_THRESHOLD) {
                String label = (delta > 0 ? "+" : "") + Math.round(Math.abs(delta));
                ParticleRenderer.INSTANCE.spawn(member.id(), label,
                        delta > 0 ? HEAL_COLOR : DAMAGE_COLOR);
            }
        }
    }
}
