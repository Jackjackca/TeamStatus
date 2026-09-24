package com.jackjackca.teamstatus.client.state;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jackjackca.teamstatus.common.state.TeamMemberState;

import javax.annotation.Nullable;

/**
 * Client-side cache of team state. Two independent sources:
 * <ul>
 *   <li>{@link #replace}: teammates snapshot from the server. The receiver's own state is
 *       never included (the server excludes it), so this is teammate data only;</li>
 *   <li>{@link #setLocalSelf}: the local player's own state synthesized every client tick
 *       from the authoritative client player — zero latency, zero self-directed traffic.
 *       This entry also exists in solo mode where the server sends nothing.</li>
 * </ul>
 * All access happens on the client main thread.
 */
public final class ClientTeamState {

    private static final Map<UUID, TeamMemberState> MEMBERS = new HashMap<>();
    private static Collection<TeamMemberState> snapshotView = List.of();

    @Nullable
    private static TeamMemberState localSelf;

    private ClientTeamState() {
    }

    /**
     * @return immutable teammate snapshot; the same instance is returned until the next packet
     */
    public static Collection<TeamMemberState> getMembers() {
        return snapshotView;
    }

    /** The locally synthesized state of the local player, or {@code null} when not in a world. */
    @Nullable
    public static TeamMemberState getLocalSelf() {
        return localSelf;
    }

    public static void replace(Collection<TeamMemberState> members) {
        MEMBERS.clear();
        for (TeamMemberState member : members) {
            MEMBERS.put(member.id(), member);
        }
        rebuildView();
    }

    public static void setLocalSelf(@Nullable TeamMemberState state) {
        localSelf = state;
    }

    public static void clear() {
        MEMBERS.clear();
        snapshotView = List.of();
        localSelf = null;
    }

    private static void rebuildView() {
        snapshotView = List.copyOf(MEMBERS.values());
    }
}
