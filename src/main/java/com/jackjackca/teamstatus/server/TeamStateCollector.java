package com.jackjackca.teamstatus.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.jackjackca.teamstatus.common.network.TeamStatePacket;
import com.jackjackca.teamstatus.common.state.EffectState;
import com.jackjackca.teamstatus.common.state.EquipmentState;
import com.jackjackca.teamstatus.common.state.HungerState;
import com.jackjackca.teamstatus.common.state.ItemState;
import com.jackjackca.teamstatus.common.state.TeamMemberState;
import com.jackjackca.teamstatus.common.state.VitalsState;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Collects online players' vitals on the server main thread and pushes a snapshot to a player
 * only when what they can see changed (dirty checking) or on the 10-second heartbeat.
 * <p>
 * Team membership is the self-hosted {@link DisplayListStore}: everyone online is shown by
 * default, and each viewer can hide individual players one-way with {@code /teamstatus}.
 * There are no offline rows — only currently online players can appear. The receiver's own
 * state is always excluded (the client synthesizes self locally).
 * <p>
 * Collection is event-driven (damage/heal/death/respawn/login/equipment mark members dirty,
 * re-read at tick end); hunger values are polled at 4 Hz. Fields are quantized to HUD
 * resolution before comparison so sub-pixel churn cannot pin the send rate.
 */
public final class TeamStateCollector {

    /** Food values have no change events, so online members are polled at 4 Hz. */
    public static final int FOOD_POLL_INTERVAL_TICKS = 5;

    /** A full snapshot is forced once every 10 seconds even if nothing changed. */
    public static final int HEARTBEAT_INTERVAL_TICKS = 200;

    /** Exhaustion bar resolution: 81 px over one hunger point's exhaustion range (4.0). */
    private static final float EXHAUSTION_STEP = 4.0F / 81.0F;
    /** Saturation is shown as 4 discrete icon levels per drumstick. */
    private static final float SATURATION_STEP = 0.25F;

    private static final Comparator<TeamMemberState> MEMBER_ORDER = Comparator.comparing(TeamMemberState::id);

    private final TeamStateBroadcaster broadcaster;

    /** Latest collected state per online member; the cache snapshots are built from. */
    private final Map<UUID, TeamMemberState> currentStates = new HashMap<>();

    /** Members whose events asked for a re-collection on the next {@link #serverTick}. */
    private final Set<UUID> dirtyMembers = new HashSet<>();

    private final Map<UUID, List<TeamMemberState>> lastSnapshots = new HashMap<>();

    /** Set on logout or display-list command so receivers are refreshed without delay. */
    private boolean forceRebuild;

    private int tickCounter = 0;

    public TeamStateCollector(TeamStateBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    /** Called by the display-list command when any player's hidden list changes. */
    public void markDisplayListsChanged() {
        forceRebuild = true;
    }

    /** Marks a member for immediate re-collection at the next tick end. */
    public void markDirty(UUID memberId) {
        dirtyMembers.add(memberId);
    }

    /** Invoked from {@code ServerTickEvent.Post} on the server main thread only. */
    public void serverTick(MinecraftServer server) {
        tickCounter++;
        boolean foodPoll = tickCounter % FOOD_POLL_INTERVAL_TICKS == 0;
        boolean heartbeat = tickCounter % HEARTBEAT_INTERVAL_TICKS == 0;
        boolean force = heartbeat || forceRebuild;
        forceRebuild = false;

        List<ServerPlayer> online = server.getPlayerList().getPlayers();

        Set<UUID> changed = new HashSet<>();
        if (heartbeat) {
            for (ServerPlayer player : online) {
                recollect(player, changed);
            }
            Set<UUID> onlineIds = new HashSet<>();
            for (ServerPlayer player : online) {
                onlineIds.add(player.getUUID());
            }
            currentStates.keySet().retainAll(onlineIds);
        } else {
            for (UUID memberId : dirtyMembers) {
                ServerPlayer player = server.getPlayerList().getPlayer(memberId);
                if (player != null) {
                    recollect(player, changed);
                }
            }
            if (foodPoll) {
                for (ServerPlayer player : online) {
                    recollect(player, changed);
                }
            }
        }
        dirtyMembers.clear();

        // With one online player there are no viewers for other people's panels; the self
        // panel is synthesized client-side, so skip snapshot building entirely.
        if (online.size() < 2) {
            return;
        }
        if (!force && changed.isEmpty()) {
            return;
        }

        // Build the shared online entry list once; each receiver snapshot is this list minus
        // self and minus their hidden targets.
        List<TeamMemberState> onlineEntries = new ArrayList<>(online.size());
        for (ServerPlayer player : online) {
            TeamMemberState state = currentStates.get(player.getUUID());
            if (state != null) {
                onlineEntries.add(state);
            }
        }
        onlineEntries.sort(MEMBER_ORDER);

        DisplayListStore displayLists = DisplayListStore.get(server);
        for (ServerPlayer receiver : online) {
            UUID receiverId = receiver.getUUID();
            List<TeamMemberState> snapshot = new ArrayList<>(onlineEntries.size());
            for (TeamMemberState entry : onlineEntries) {
                if (!entry.id().equals(receiverId) && displayLists.isVisible(receiverId, entry.id())) {
                    snapshot.add(entry);
                }
            }

            List<TeamMemberState> previous = lastSnapshots.get(receiverId);
            if (force || previous == null || !previous.equals(snapshot)) {
                broadcaster.sendToPlayer(receiver, new TeamStatePacket(snapshot));
                lastSnapshots.put(receiverId, snapshot);
            }
        }
    }

    private void recollect(ServerPlayer player, Set<UUID> changed) {
        TeamMemberState fresh = collectOnline(player);
        TeamMemberState previous = currentStates.put(player.getUUID(), fresh);
        if (previous == null || !previous.equals(fresh)) {
            changed.add(player.getUUID());
        }
    }

    private TeamMemberState collectOnline(ServerPlayer player) {
        HungerState hunger = new HungerState(
                player.getFoodData().getFoodLevel(),
                quantize(player.getFoodData().getSaturationLevel(), SATURATION_STEP),
                quantize(player.getFoodData().getExhaustionLevel(), EXHAUSTION_STEP));
        VitalsState vitals = new VitalsState(
                player.getHealth(),
                player.getMaxHealth(),
                player.getAbsorptionAmount(),
                player.getArmorValue(),
                player.isFullyFrozen(),
                hunger);

        List<EffectState> effects = player.getActiveEffects().stream()
                .map(EffectState::of)
                .sorted(EffectState.COMPARATOR)
                .toList();

        EquipmentState equipment = new EquipmentState(ItemState.of(player.getMainHandItem()));

        return TeamMemberState.online(player.getUUID(), player.getGameProfile().getName(),
                !player.isDeadOrDying(), vitals, effects, equipment);
    }

    private static float quantize(float value, float step) {
        return Math.round(value / step) * step;
    }

    /** Drops cached state when a player leaves the server; receivers are updated next tick. */
    public void onPlayerLoggedOut(UUID playerId) {
        dirtyMembers.remove(playerId);
        currentStates.remove(playerId);
        lastSnapshots.remove(playerId);
        forceRebuild = true;
    }

    public void onServerStopped() {
        currentStates.clear();
        dirtyMembers.clear();
        lastSnapshots.clear();
        forceRebuild = false;
        tickCounter = 0;
    }
}
