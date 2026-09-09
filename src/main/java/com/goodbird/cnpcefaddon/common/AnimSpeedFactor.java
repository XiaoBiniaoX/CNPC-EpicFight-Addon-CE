package com.goodbird.cnpcefaddon.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;

import com.goodbird.cnpcefaddon.mixin.IDataDisplay;

/**
 * 单个 NPC 的动画攻击速度倍率。
 *
 * <p>与数据包的 {@code play_speed} 是<b>相乘</b>关系，不是替换：数据包写 6.0、这里填 2.0
 * 时实际播放速度为 12.0。默认 {@code 1.0} 表示不改变任何既有行为。
 *
 * <p>值存放在 {@code DataDisplay}（NBT 键 {@link #NBT_KEY}）而不是 {@code DataMelee}：
 * {@code EntityNPCInterface.writeSpawnData} 只把 {@code display.save(...)} 与少数 stats
 * 字段写进生成包，{@code stats.melee} 完全不进网络包。动画播放速度在客户端和服务端各算一次
 * （客户端 {@code ClientAnimator}、服务端 {@code ServerAnimator}），只有存在 display 上
 * 才能双端一致；放在 melee 上会导致客户端永远用 1.0，出现「服务端判定快、客户端画面慢」。
 *
 * <p>取值一律经 {@link #sanitize} 钳制：踩坑第 9 条要求 NBT 取值判类型并过滤
 * NaN / 无穷 / 非正数 —— 字符串型 NBT 经 {@code getFloat} 会静默变成 0.0，
 * 而倍率 0 会让动画彻底冻结。
 */
public final class AnimSpeedFactor {

    /** NBT 键；与既有 {@code efModel} / {@code cnpcefYsmModel} 同放在 DataDisplay 上。 */
    public static final String NBT_KEY = "cnpcefAnimSpeed";

    public static final float DEFAULT = 1.0F;
    public static final float MIN = 0.01F;
    public static final float MAX = 10.0F;

    private AnimSpeedFactor() {
    }

    /**
     * 把任意来源的值收敛到 {@code [MIN, MAX]}。
     * NaN / 无穷 / 非正数一律回落 {@link #DEFAULT}，而不是钳到 MIN ——
     * 这些值代表「读取出错」，此时保持原有行为比给一个极慢速度更安全。
     */
    public static float sanitize(float raw) {
        if (!Float.isFinite(raw) || raw <= 0.0F) {
            return DEFAULT;
        }
        return Math.min(MAX, Math.max(MIN, raw));
    }

    /**
     * 从 NBT 读取倍率。缺字段（旧世界 / 旧 NBT）返回 {@link #DEFAULT}。
     * 数值型直读；字符串型尝试解析（手写数据与旧存档里出现过 {@code "2.0"} 这种写法）；
     * 其他类型视为无效。
     */
    public static float read(CompoundTag tag) {
        if (tag == null || !tag.contains(NBT_KEY)) {
            return DEFAULT;
        }
        Tag entry = tag.get(NBT_KEY);
        if (entry instanceof NumericTag numeric) {
            return sanitize(numeric.getAsFloat());
        }
        if (entry instanceof StringTag text) {
            try {
                return sanitize(Float.parseFloat(text.getAsString().trim()));
            } catch (NumberFormatException ignored) {
                return DEFAULT;
            }
        }
        return DEFAULT;
    }

    /**
     * 取某个实体的倍率。非 CNPC NPC（含玩家与原版生物）恒为 {@link #DEFAULT}，
     * 保证本功能不会波及 NPC 之外的任何实体（踩坑第 26 条）。
     */
    public static float of(Entity entity) {
        if (!(entity instanceof EntityNPCInterface npc) || npc.display == null) {
            return DEFAULT;
        }
        return ((IDataDisplay) npc.display).getAnimSpeedFactor();
    }
}
