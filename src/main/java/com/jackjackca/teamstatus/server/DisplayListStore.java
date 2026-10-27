package com.jackjackca.teamstatus.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * World-persisted, per-player display configuration.
 * <p>
 * Each viewer owns a <b>hidden list</b> of target UUIDs: everyone online is shown by default,
 * and hiding a target removes just that player from the viewer's HUD. The relation is
 * one-way (it only affects the viewer's own screen) and needs no consent. Identity is the
 * UUID so the setting survives name changes.
 * <p>
 * Stored on the overworld's {@code DimensionDataStorage}, which persists with the world save
 * on both dedicated servers and singleplayer. Server main thread only.
 * <p>
 * Since 1.21.5 {@link SavedData} is persisted through a {@link SavedDataType} with a
 * {@link Codec} instead of the old NBT hook methods. The on-disk shape is unchanged (a
 * "viewers" compound mapping viewer UUID strings to lists of target UUID strings), so saves
 * written by older mod versions keep loading.
 */
public final class DisplayListStore extends SavedData {

    private static final String DATA_NAME = "teamstatus_display_lists";
    private static final String TAG_VIEWERS = "viewers";

    /** viewer UUID -> set of hidden target UUIDs. */
    private final Map<UUID, Set<UUID>> hiddenByViewer = new HashMap<>();

    public static final SavedDataType<DisplayListStore> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(DATA_NAME),
            DisplayListStore::new,
            RawData.CODEC.xmap(DisplayListStore::fromRaw, DisplayListStore::toRaw));

    private DisplayListStore() {
    }

    public static DisplayListStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Whether {@code viewer} currently sees {@code target}. */
    public boolean isVisible(UUID viewer, UUID target) {
        Set<UUID> hidden = hiddenByViewer.get(viewer);
        return hidden == null || !hidden.contains(target);
    }

    /** Unmodifiable hidden set for a viewer. */
    public Set<UUID> getHidden(UUID viewer) {
        Set<UUID> hidden = hiddenByViewer.get(viewer);
        return hidden == null ? Set.of() : Collections.unmodifiableSet(hidden);
    }

    /** Adds targets to the viewer's hidden list; returns true if anything changed. */
    public boolean hide(UUID viewer, Iterable<UUID> targets) {
        Set<UUID> hidden = hiddenByViewer.computeIfAbsent(viewer, k -> new HashSet<>());
        boolean changed = false;
        for (UUID target : targets) {
            if (!target.equals(viewer) && hidden.add(target)) {
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
        return changed;
    }

    /** Removes targets from the viewer's hidden list; returns true if anything changed. */
    public boolean show(UUID viewer, Iterable<UUID> targets) {
        Set<UUID> hidden = hiddenByViewer.get(viewer);
        if (hidden == null) {
            return false;
        }
        boolean changed = false;
        for (UUID target : targets) {
            changed |= hidden.remove(target);
        }
        if (changed) {
            if (hidden.isEmpty()) {
                hiddenByViewer.remove(viewer);
            }
            setDirty();
        }
        return changed;
    }

    /** Clears the viewer's hidden list (show everyone); returns true if anything changed. */
    public boolean showAll(UUID viewer) {
        if (hiddenByViewer.remove(viewer) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    private static DisplayListStore fromRaw(RawData raw) {
        DisplayListStore store = new DisplayListStore();
        for (Map.Entry<String, List<String>> entry : raw.viewers().entrySet()) {
            UUID viewer;
            try {
                viewer = UUID.fromString(entry.getKey());
            } catch (IllegalArgumentException e) {
                continue;
            }
            Set<UUID> hidden = new HashSet<>();
            for (String target : entry.getValue()) {
                try {
                    hidden.add(UUID.fromString(target));
                } catch (IllegalArgumentException ignored) {
                    // Skip malformed entries rather than failing the whole store load.
                }
            }
            if (!hidden.isEmpty()) {
                store.hiddenByViewer.put(viewer, hidden);
            }
        }
        return store;
    }

    private RawData toRaw() {
        Map<String, List<String>> raw = new HashMap<>();
        for (Map.Entry<UUID, Set<UUID>> entry : hiddenByViewer.entrySet()) {
            List<String> targets = new ArrayList<>(entry.getValue().size());
            for (UUID target : entry.getValue()) {
                targets.add(target.toString());
            }
            raw.put(entry.getKey().toString(), targets);
        }
        return new RawData(raw);
    }

    /** Wire shape: {@code {"viewers": {"<viewer-uuid>": ["<target-uuid>", ...]}}}. */
    private record RawData(Map<String, List<String>> viewers) {

        static final Codec<RawData> CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf())
                .fieldOf(TAG_VIEWERS)
                .codec()
                .xmap(RawData::new, RawData::viewers);
    }
}
