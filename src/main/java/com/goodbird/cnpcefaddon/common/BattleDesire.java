package com.goodbird.cnpcefaddon.common;

import com.goodbird.cnpcefaddon.api.IDataMeleeAttackDesire;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * 战斗欲望（0.0~10.0）到各项战斗系数的统一换算。
 *
 * <p>GUI 输入框写入 {@code DataMelee} 的 NBT 键 {@code MeleeAttackDesire}，默认 5.0。
 * 无论数据包是 EF 原生格式、坚不可摧格式还是 CombatEvolution 格式，都按同一套系数缩放，
 * 数据包本身的数值不被改写 —— 5.0 时全部系数为 1，等同「JSON 怎么写就怎么读」。
 *
 * <table>
 *   <tr><th>欲望</th><th>攻速</th><th>伤害</th><th>长格挡概率</th><th>短格挡穿插</th></tr>
 *   <tr><td>0.0</td><td>×0.5</td><td>×0.5</td><td>3/4</td><td>无</td></tr>
 *   <tr><td>5.0</td><td>×1.0</td><td>×1.0</td><td>基准</td><td>无</td></tr>
 *   <tr><td>10.0</td><td>×1.5</td><td>×1.5</td><td>1/5</td><td>大幅提高</td></tr>
 * </table>
 */
public final class BattleDesire {

    /** 默认值：所有系数为 1，行为与未接入本功能时完全一致。 */
    public static final float DEFAULT = 5.0F;
    public static final float MIN = 0.0F;
    public static final float MAX = 10.0F;

    private BattleDesire() {
    }

    /** 取实体的战斗欲望；非 CNPC 或读取失败时返回默认值。 */
    public static float of(LivingEntity entity) {
        try {
            if (entity instanceof EntityNPCInterface npc && npc.stats != null && npc.stats.getMelee() != null) {
                float raw = ((IDataMeleeAttackDesire) (Object) npc.stats.getMelee()).getAttackDesire();
                return sanitize(raw);
            }
        } catch (Throwable ignored) {
            // 缺 mixin / 数据未初始化等情况一律退回默认值
        }
        return DEFAULT;
    }

    /** 过滤 NaN、无穷与越界值，保证后续换算不会产出异常系数。 */
    public static float sanitize(float desire) {
        if (Float.isNaN(desire) || Float.isInfinite(desire)) {
            return DEFAULT;
        }
        return Math.max(MIN, Math.min(MAX, desire));
    }

    /** 攻速倍率：0.0 → 0.5，5.0 → 1.0，10.0 → 1.5。 */
    public static float attackSpeedFactor(float desire) {
        return linear(sanitize(desire), 0.5F, 1.0F, 1.5F);
    }

    /** 伤害倍率：0.0 → 0.5，5.0 → 1.0，10.0 → 1.5。 */
    public static float damageFactor(float desire) {
        return linear(sanitize(desire), 0.5F, 1.0F, 1.5F);
    }

    /**
     * 长格挡（持续举盾防御）的选取概率：0.0 → 0.75，5.0 → 基准 0.5，10.0 → 0.2。
     * 欲望越高越倾向于进攻而非长时间防守。
     */
    public static float longGuardChance(float desire) {
        return linear(sanitize(desire), 0.75F, 0.5F, 0.2F);
    }

    /**
     * 短格挡穿插反击的概率：格挡受击后立刻插入 1~3 次攻击。
     * 5.0 及以下为 0（不改变原行为），10.0 时为 0.8。
     */
    public static float shortGuardCounterChance(float desire) {
        float d = sanitize(desire);
        if (d <= DEFAULT) {
            return 0.0F;
        }
        return (d - DEFAULT) / (MAX - DEFAULT) * 0.8F;
    }

    /** 短格挡穿插时的攻击次数（1~3），随欲望提高。 */
    public static int shortGuardCounterHits(float desire) {
        float d = sanitize(desire);
        if (d <= DEFAULT) {
            return 0;
        }
        return 1 + (int) Math.floor((d - DEFAULT) / (MAX - DEFAULT) * 2.999F);
    }

    /**
     * 攻击间隔倍率：欲望越高间隔越短。沿用既有的立方曲线
     * {@code (5/desire)^3}，与 {@code MixinEntityAIAttackTarget} 的历史行为保持一致：
     * 5.0 恰好为 1.0（旧世界与旧数据包行为不变），0.0 表示不主动发起近战。
     */
    public static double attackIntervalFactor(float desire) {
        float d = sanitize(desire);
        if (d <= 0.0F) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.pow(DEFAULT / d, 3.0);
    }

    /** 以 5.0 为中点的分段线性插值。 */
    private static float linear(float desire, float atMin, float atMid, float atMax) {
        if (desire <= DEFAULT) {
            return atMin + (atMid - atMin) * (desire - MIN) / (DEFAULT - MIN);
        }
        return atMid + (atMax - atMid) * (desire - DEFAULT) / (MAX - DEFAULT);
    }
}
