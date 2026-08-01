package com.goodbird.cnpcefaddon.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-patch {@code play_speed} overrides parsed out of the NPC mobpatch datapacks.
 * <p>
 * The outer key is the <b>patch id</b> (the datapack file, e.g. {@code customnpcs:samurai}),
 * not the entity type: every NPC shares the {@code customnpcs:customnpc} entity type while
 * selecting its patch through {@code DataDisplay}. See {@link PatchKeyResolver}.
 * <p>
 * Reloads clear entries per patch id rather than wiping the whole cache, so the normal and
 * advanced reload listeners cannot erase each other's data.
 */
public class PlaySpeedCache {
    private static final Map<ResourceLocation, Map<ResourceLocation, Float>> CACHE = new ConcurrentHashMap<>();

    public static void register(ResourceLocation patchId, ResourceLocation animKey, float speed) {
        CACHE.computeIfAbsent(patchId, k -> new ConcurrentHashMap<>()).put(animKey, speed);
    }

    public static float getSpeed(ResourceLocation patchId, ResourceLocation animKey) {
        Map<ResourceLocation, Float> animSpeeds = CACHE.get(patchId);
        if (animSpeeds == null) return 1.0F;
        return animSpeeds.getOrDefault(animKey, 1.0F);
    }

    /** Drops the entries of a single patch, used before re-parsing it on reload. */
    public static void clear(ResourceLocation patchId) {
        if (patchId != null) {
            CACHE.remove(patchId);
        }
    }

    /** Drops every entry. Only valid when all reload listeners are about to re-register. */
    public static void clear() {
        CACHE.clear();
        EntityPlaySpeedManager.clearPatched();
    }

    public static void parseAndRegister(ResourceLocation patchId, CompoundTag tag) {
        // Stale values must not survive a reload where the key was removed from the file.
        clear(patchId);

        if (!tag.contains("combat_behavior")) return;
        ListTag list = tag.getList("combat_behavior", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.contains("behaviors")) {
                parseBehaviorsList(patchId, entry.getList("behaviors", 10));
            }
            if (entry.contains("behavior_series")) {
                ListTag seriesList = entry.getList("behavior_series", 10);
                for (int j = 0; j < seriesList.size(); j++) {
                    CompoundTag series = seriesList.getCompound(j);
                    if (series.contains("behaviors")) {
                        parseBehaviorsList(patchId, series.getList("behaviors", 10));
                    }
                }
            }
            if (entry.contains("animation") && entry.contains("play_speed")) {
                registerSingle(patchId, entry);
            }
        }
    }

    private static void parseBehaviorsList(ResourceLocation patchId, ListTag behaviors) {
        for (int i = 0; i < behaviors.size(); i++) {
            CompoundTag behavior = behaviors.getCompound(i);
            if (behavior.contains("play_speed")) {
                registerSingle(patchId, behavior);
            }
        }
    }

    private static void registerSingle(ResourceLocation patchId, CompoundTag behavior) {
        float speed = behavior.getFloat("play_speed");
        if (speed == 1.0F) return;
        String animName = behavior.getString("animation");
        if (animName.isEmpty()) return;
        ResourceLocation animKey = ResourceLocation.parse(animName);
        register(patchId, animKey, speed);
        EntityPlaySpeedManager.ensureModifier(animKey);
    }

    /**
     * Serialises the parsed speeds of one patch so they can ride along with the datapack
     * sync packet. {@code MobPatchReloadListener.filterClientData} drops
     * {@code combat_behavior}, so a remote client would otherwise never learn about
     * {@code play_speed} and would play animations at a different rate than the server.
     */
    public static CompoundTag writeSpeeds(ResourceLocation patchId) {
        CompoundTag out = new CompoundTag();
        Map<ResourceLocation, Float> speeds = CACHE.get(patchId);

        if (speeds != null) {
            speeds.forEach((animKey, speed) -> out.putFloat(animKey.toString(), speed));
        }

        return out;
    }

    /** Restores speeds shipped by {@link #writeSpeeds}. */
    public static void readSpeeds(ResourceLocation patchId, CompoundTag tag) {
        clear(patchId);

        for (String key : tag.getAllKeys()) {
            ResourceLocation animKey = ResourceLocation.tryParse(key);

            if (animKey == null) {
                continue;
            }

            register(patchId, animKey, tag.getFloat(key));
            EntityPlaySpeedManager.ensureModifier(animKey);
        }
    }
}
