package com.jackjackca.teamstatus.server;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

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
 */
public final class DisplayListStore extends SavedData {

    private static final String DATA_NAME = "teamstatus_display_lists";
    private static final String TAG_VERSION = "version";
    private static final String TAG_VIEWERS = "viewers";
    private static final int FORMAT_VERSION = 1;

    /** viewer UUID -> set of hidden target UUIDs. */
    private final Map<UUID, Set<UUID>> hiddenByViewer = new java.util.HashMap<>();

    public static DisplayListStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DisplayListStore::new, DisplayListStore::load, null), DATA_NAME);
    }

    private DisplayListStore() {
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

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(TAG_VERSION, FORMAT_VERSION);
        CompoundTag viewers = new CompoundTag();
        for (Map.Entry<UUID, Set<UUID>> entry : hiddenByViewer.entrySet()) {
            ListTag list = new ListTag();
            for (UUID target : entry.getValue()) {
                list.add(StringTag.valueOf(target.toString()));
            }
            viewers.put(entry.getKey().toString(), list);
        }
        tag.put(TAG_VIEWERS, viewers);
        return tag;
    }

    private static DisplayListStore load(CompoundTag tag, HolderLookup.Provider registries) {
        DisplayListStore store = new DisplayListStore();
        CompoundTag viewers = tag.getCompound(TAG_VIEWERS);
        for (String viewerKey : viewers.getAllKeys()) {
            UUID viewer;
            try {
                viewer = UUID.fromString(viewerKey);
            } catch (IllegalArgumentException e) {
                continue;
            }
            Set<UUID> hidden = new HashSet<>();
            ListTag list = viewers.getList(viewerKey, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                try {
                    hidden.add(UUID.fromString(list.getString(i)));
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
}
