package com.goodbird.cnpcefaddon.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(PlaySpeedCache.class);

    /** 播放速度上限。超过该值的配置按上限收敛，避免异常数据把动画甩到不可控速度。 */
    private static final float MAX_SPEED = 20.0F;

    private static final Map<ResourceLocation, Map<ResourceLocation, Float>> CACHE = new ConcurrentHashMap<>();

    /**
     * Every animation key a {@code PLAY_SPEED_MODIFIER} has ever been installed on.
     * <p>
     * Epic Fight stores that modifier inside the {@code StaticAnimation} instance itself
     * ({@code StaticAnimation.properties}), and its {@code AnimationManager} is registered as a
     * <b>client resource</b> reload listener, so switching the language or a resource pack clears
     * {@code AnimationManager.animations} and every later {@code accessor.get()} hands out a fresh
     * instance with an empty property map. The datapack side is not re-read at that point (that
     * only happens on {@code /reload} or when the world is loaded again), so nothing would
     * re-install the modifier and every configured {@code play_speed} silently fell back to 1.0
     * until the world was re-entered.
     * <p>
     * Kept separately from {@link #CACHE} on purpose: the cache is cleared per patch id on every
     * reload, while this set must survive so the modifiers can be re-installed at any time.
     */
    private static final Set<ResourceLocation> INSTALLED_ANIM_KEYS = ConcurrentHashMap.newKeySet();

    public static void register(ResourceLocation patchId, ResourceLocation animKey, float speed) {
        CACHE.computeIfAbsent(patchId, k -> new ConcurrentHashMap<>()).put(animKey, speed);
    }

    /**
     * Re-installs the playback speed modifier on every animation it was ever installed on.
     * <p>
     * Called after a client resource reload, when Epic Fight has rebuilt its animation instances.
     * {@code ensureModifier} is idempotent (it unwraps an existing chain before re-wrapping), so a
     * key that still holds its modifier is left with exactly one.
     *
     * @return how many keys were re-installed, for logging
     */
    public static int reinstallAllModifiers() {
        for (ResourceLocation animKey : INSTALLED_ANIM_KEYS) {
            EntityPlaySpeedManager.ensureModifier(animKey);
        }

        return INSTALLED_ANIM_KEYS.size();
    }

    /** Records that a modifier was installed on this key, so it can be restored after a reload. */
    static void rememberInstalledKey(ResourceLocation animKey) {
        if (animKey != null) {
            INSTALLED_ANIM_KEYS.add(animKey);
        }
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

    /**
     * 解析一个 mobpatch 数据包里的全部 play_speed 配置。
     * <p>
     * play_speed 只是附加特性，任何解析问题都不应该让宿主 mobpatch 加载失败（这曾导致
     * 整个数据包不可用）。因此这里整体兜底，并且兼容旧数据包结构：既支持
     * {@code combat_behavior[].behaviors[]}，也支持 {@code behavior_series[].behaviors[]}
     * 和条目自身直接带 {@code animation} 的写法；旧字段名 {@code playspeed} 与新字段名
     * {@code play_speed} 同时接受。
     */
    public static void parseAndRegister(ResourceLocation patchId, CompoundTag tag) {
        // Stale values must not survive a reload where the key was removed from the file.
        clear(patchId);

        if (tag == null || !tag.contains("combat_behavior")) {
            return;
        }

        try {
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
                if (entry.contains("animation") && hasPlaySpeed(entry)) {
                    registerSingle(patchId, entry);
                }
            }
        } catch (Throwable t) {
            // 保留已成功登记的条目；宿主 mobpatch 继续正常加载。
            LOGGER.error("[cnpcef-fix] play_speed 解析 combat_behavior 失败，该 patch 的剩余速度配置被跳过：patch={}", patchId, t);
        }
    }

    private static void parseBehaviorsList(ResourceLocation patchId, ListTag behaviors) {
        for (int i = 0; i < behaviors.size(); i++) {
            CompoundTag behavior = behaviors.getCompound(i);
            if (hasPlaySpeed(behavior)) {
                registerSingle(patchId, behavior);
            }
        }
    }

    private static boolean hasPlaySpeed(CompoundTag behavior) {
        return behavior.contains("play_speed") || behavior.contains("playspeed");
    }

    /**
     * 读取单条行为的播放速度。
     * <p>
     * 单条条目的任何问题都不得中断整个数据包的解析：旧数据包（以及手写数据包）里出现过
     * 大写字母的动画名、写成字符串的 {@code "play_speed": "1.3"}、以及 0 / 负数 / 非法数值。
     * 早期实现使用 {@code ResourceLocation.parse} 且不做单条保护，一个坏条目会让
     * {@code parseAndRegister} 抛异常，导致该 mobpatch 整包加载失败；而字符串型 NBT 经
     * {@code getFloat} 静默变成 0.0，会被当成倍率写入并让动画彻底冻结。这两点都以跳过
     * 单条并保留其余条目的方式处理。
     */
    private static void registerSingle(ResourceLocation patchId, CompoundTag behavior) {
        try {
            String speedKey = behavior.contains("play_speed") ? "play_speed" : "playspeed";
            float speed = readSpeedValue(behavior, speedKey);

            // 0 表示无效值（含非数字 NBT 被静默读成 0 的情况），1 表示无需改速。
            if (speed == 0.0F || speed == 1.0F) {
                return;
            }

            String animName = behavior.getString("animation");
            if (animName.isEmpty()) {
                return;
            }

            // tryParse 而非 parse：非法动画名只跳过这一条，不牵连整包。
            ResourceLocation animKey = ResourceLocation.tryParse(animName);
            if (animKey == null) {
                LOGGER.error("[cnpcef-fix] play_speed 跳过非法动画名：patch={} animation={}", patchId, animName);
                return;
            }

            register(patchId, animKey, speed);
            EntityPlaySpeedManager.ensureModifier(animKey);
        } catch (Throwable t) {
            LOGGER.error("[cnpcef-fix] play_speed 解析单条行为失败，已跳过该条：patch={}", patchId, t);
        }
    }

    /**
     * 把 NBT 中的播放速度读成可用倍率；无法安全使用时返回 0，由调用方跳过。
     * <p>
     * 只接受数字型 NBT；字符串型（旧手写数据包常见）额外尝试一次数字解析，避免
     * {@code getFloat} 静默返回 0 而把动画冻结。同时拒绝 NaN、无穷和非正数。
     */
    private static float readSpeedValue(CompoundTag tag, String key) {
        Tag raw = tag.get(key);

        float value;
        if (raw instanceof NumericTag numeric) {
            value = numeric.getAsFloat();
        } else if (raw instanceof StringTag stringTag) {
            try {
                value = Float.parseFloat(stringTag.getAsString().trim());
            } catch (NumberFormatException e) {
                LOGGER.error("[cnpcef-fix] play_speed 值不是数字，已忽略：{}={}", key, stringTag.getAsString());
                return 0.0F;
            }
        } else {
            return 0.0F;
        }

        return sanitizeSpeed(value, key);
    }

    /** 过滤 NaN / 无穷 / 非正数，并把倍率限制在合理区间，避免动画冻结或速度爆表。 */
    private static float sanitizeSpeed(float value, String source) {
        if (!Float.isFinite(value) || value <= 0.0F) {
            LOGGER.error("[cnpcef-fix] play_speed 数值非法，已忽略：{}={}", source, value);
            return 0.0F;
        }

        if (value > MAX_SPEED) {
            LOGGER.error("[cnpcef-fix] play_speed 数值过大，已收敛到 {}：{}={}", MAX_SPEED, source, value);
            return MAX_SPEED;
        }

        return value;
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

    /**
     * 还原 {@link #writeSpeeds} 随同步包发来的速度表。
     * <p>
     * 这是网络接收路径，必须把内容当作不可信数据：非法键名、非数字值、NaN、负数都只跳过
     * 该条目，绝不允许抛出异常打断整包同步（否则客户端会丢失全部 mobpatch）。
     */
    public static void readSpeeds(ResourceLocation patchId, CompoundTag tag) {
        clear(patchId);

        if (tag == null) {
            return;
        }

        for (String key : tag.getAllKeys()) {
            try {
                ResourceLocation animKey = ResourceLocation.tryParse(key);
                if (animKey == null) {
                    LOGGER.error("[cnpcef-fix] play_speed 同步包含非法动画键，已跳过：patch={} key={}", patchId, key);
                    continue;
                }

                float speed = readSpeedValue(tag, key);
                if (speed == 0.0F || speed == 1.0F) {
                    continue;
                }

                register(patchId, animKey, speed);
                EntityPlaySpeedManager.ensureModifier(animKey);
            } catch (Throwable t) {
                LOGGER.error("[cnpcef-fix] play_speed 同步条目还原失败，已跳过：patch={} key={}", patchId, key, t);
            }
        }
    }
}
