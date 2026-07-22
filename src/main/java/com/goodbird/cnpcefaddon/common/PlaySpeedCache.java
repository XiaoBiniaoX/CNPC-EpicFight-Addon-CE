package com.goodbird.cnpcefaddon.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class PlaySpeedCache {
    private static final Map<ResourceLocation, Map<ResourceLocation, Float>> CACHE = new ConcurrentHashMap<>();

    public static void register(ResourceLocation entityType, ResourceLocation animKey, float speed) {
        CACHE.computeIfAbsent(entityType, k -> new ConcurrentHashMap<>()).put(animKey, speed);
    }

    public static float getSpeed(ResourceLocation entityType, ResourceLocation animKey) {
        Map<ResourceLocation, Float> animSpeeds = CACHE.get(entityType);
        if (animSpeeds == null) return 1.0F;
        return animSpeeds.getOrDefault(animKey, 1.0F);
    }

    public static void clear() {
        CACHE.clear();
    }

    public static void parseAndRegister(ResourceLocation entityType, CompoundTag tag) {
        if (!tag.contains("combat_behavior")) return;
        ListTag list = tag.getList("combat_behavior", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.contains("behaviors")) {
                parseBehaviorsList(entityType, entry.getList("behaviors", 10));
            }
            if (entry.contains("behavior_series")) {
                ListTag seriesList = entry.getList("behavior_series", 10);
                for (int j = 0; j < seriesList.size(); j++) {
                    CompoundTag series = seriesList.getCompound(j);
                    if (series.contains("behaviors")) {
                        parseBehaviorsList(entityType, series.getList("behaviors", 10));
                    }
                }
            }
            if (entry.contains("animation") && entry.contains("play_speed")) {
                registerSingle(entityType, entry);
            }
        }
    }

    private static void parseBehaviorsList(ResourceLocation entityType, ListTag behaviors) {
        for (int i = 0; i < behaviors.size(); i++) {
            CompoundTag behavior = behaviors.getCompound(i);
            if (behavior.contains("play_speed")) {
                registerSingle(entityType, behavior);
            }
        }
    }

    private static void registerSingle(ResourceLocation entityType, CompoundTag behavior) {
        float speed = behavior.getFloat("play_speed");
        if (speed == 1.0F) return;
        String animName = behavior.getString("animation");
        if (animName.isEmpty()) return;
        ResourceLocation animKey = ResourceLocation.parse(animName);
        register(entityType, animKey, speed);
        EntityPlaySpeedManager.ensureModifier(animKey);
    }
}
