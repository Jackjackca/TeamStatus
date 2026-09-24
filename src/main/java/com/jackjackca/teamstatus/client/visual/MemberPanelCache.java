package com.jackjackca.teamstatus.client.visual;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jackjackca.teamstatus.client.render.ArmorRenderer.ArmorIcon;
import com.jackjackca.teamstatus.client.render.EffectRenderer.EffectEntry;
import com.jackjackca.teamstatus.client.render.FoodRenderer.Drumstick;
import com.jackjackca.teamstatus.common.state.TeamMemberState;

/**
 * Per-member cache of the descriptor lists consumed by the 2D row renderers.
 * <p>
 * All 2D layouts (heart shake/regen slot, food jitter, armor icons, effect labels, trimmed
 * name) only change on a gui-tick boundary or when a new snapshot arrives — never between
 * frames at the same tick. Descriptors are therefore rebuilt at most once per member per
 * 20&nbsp;Hz tick and reused across every render frame within that tick, removing all
 * per-frame list/record allocations from the steady-state HUD path.
 * <p>
 * Client main thread only.
 */
public final class MemberPanelCache {

    static final class Entry {
        TeamMemberState snapshot;
        int guiTicks = Integer.MIN_VALUE;
        int panelWidth;

        HealthVisual.HeartLayout hearts = new HealthVisual.HeartLayout(List.of(), List.of());
        int healthHeight;

        List<ArmorIcon> armor = List.of();
        int armorHeight;

        List<Drumstick> drumsticks = List.of();
        int exhaustionWidth;

        List<EffectEntry> effects = List.of();
        int effectsHeight;

        String trimmedName;
    }

    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();

    private MemberPanelCache() {
    }

    /** Returns (creating on first use) the cache entry for one member. */
    static Entry get(UUID memberId) {
        return ENTRIES.computeIfAbsent(memberId, k -> new Entry());
    }

    /**
     * @return whether the cached descriptors are stale for this frame's snapshot/tick/width
     */
    static boolean isStale(Entry entry, MemberVisualContext context, int panelWidth) {
        return entry.snapshot != context.member()
                || entry.guiTicks != context.guiTicks()
                || entry.panelWidth != panelWidth;
    }

    static void markBuilt(Entry entry, MemberVisualContext context, int panelWidth) {
        entry.snapshot = context.member();
        entry.guiTicks = context.guiTicks();
        entry.panelWidth = panelWidth;
    }

    public static void retainOnly(Iterable<UUID> presentMemberIds) {
        java.util.Set<UUID> set = new HashSet<>();
        presentMemberIds.forEach(set::add);
        ENTRIES.keySet().retainAll(set);
    }

    public static void clear() {
        ENTRIES.clear();
    }
}
