package top.bincnpcef.common;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.entity.EntityNPCInterface;
import yesman.epicfight.api.event.EpicFightEventHooks;
import yesman.epicfight.api.event.types.entity.ApplyStunEvent;
import yesman.epicfight.api.event.types.entity.StunnedEvent;
import yesman.epicfight.world.capabilities.entitypatch.HurtableEntityPatch;
import yesman.epicfight.world.damagesource.EpicFightDamageSource;
import yesman.epicfight.world.damagesource.StunType;

/**
 * 将 {@link AddonConfig} 的护甲-僵直规则应用到 NPC 对玩家的攻击上。
 *
 * <p>EF 的伤害处理链在同一同步调用中依次触发：
 * <ol>
 *   <li>{@link StunnedEvent}（VanillaEntityEventHooks.onCalculateDamagePre，含伤害源）——
 *       护甲 >= 阈值在此取消事件实现完全免僵直；</li>
 *   <li>{@link ApplyStunEvent}（LivingEntityPatch.applyStun，不含伤害源）——
 *       在此写入最终僵直时长。</li>
 * </ol>
 *
 * <p>重要：EF 21.17.3.1 的 {@code ApplyStunEvent} 构造器从未给 {@code stunTime} 字段赋值
 * （该参数被丢弃），导致 {@code getStunTime()} 恒为 0 —— SHORT 僵直的时长永远不会生效。
 * 本处理器在 StunnedEvent 阶段按 EF 自身公式镜像计算基准时长（impact/stunReduction/
 * knockbackResistance），叠加护甲缩放后暂存，在 ApplyStunEvent 阶段写入 ——
 * 同时修复该 EF bug 与"调高冲击值无效"。
 */
public class PlayerStunHandler {
    /** 时长可被推远的最大比例（1.20.1 同款）。 */
    private static final float MAX_SCALE_SHIFT = 0.75F;

    private static Object pendingTarget;
    private static float pendingStunTime = 0.0F;

    public static void init() {
        EpicFightEventHooks.Entity.ON_STUNNED.registerEvent(PlayerStunHandler::onStunned, "cnpcef_stun_handler", 100);
        EpicFightEventHooks.Entity.APPLY_STUN.registerEvent(PlayerStunHandler::onApplyStun, "cnpcef_apply_stun_handler", 100);
    }

    private static void onStunned(StunnedEvent event) {
        clearPending();

        double threshold = AddonConfig.PLAYER_STUN_IMMUNE_ARMOR.get();
        if (threshold <= 0.0D || event.getStunType() == StunType.NONE) {
            return;
        }

        HurtableEntityPatch<?> stunned = event.getEntityPatch();
        if (stunned == null || !(stunned.getOriginal() instanceof Player player)) {
            return;
        }

        EpicFightDamageSource source = event.getDamageSource();
        if (!isNpcAttack(source)) {
            return;
        }

        double armor = player.getAttributeValue(Attributes.ARMOR);

        if (armor >= threshold) {
            event.cancel();
            return;
        }

        // 镜像 EF VanillaEntityEventHooks 的 SHORT/LONG 分支公式（同版本源码逐行对照）
        float baseStunTime = computeBaseStunTime(event.getStunType(), source.calculateImpact(),
                stunned.getStunReduction(), (float) player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));

        float ratio = (float) Math.min(1.0D, Math.max(0.0D, armor / threshold));
        boolean positive = AddonConfig.ARMOR_STUN_BENEFIT_POSITIVE.get();

        pendingTarget = stunned;
        pendingStunTime = positive
                ? baseStunTime * (1.0F - MAX_SCALE_SHIFT * ratio)
                : baseStunTime * (1.0F + MAX_SCALE_SHIFT * ratio);
    }

    private static void onApplyStun(ApplyStunEvent event) {
        HurtableEntityPatch<?> stunned = event.getEntityPatch();
        if (stunned != pendingTarget) {
            return;
        }

        event.setStunTime(pendingStunTime);
        clearPending();
    }

    private static float computeBaseStunTime(StunType stunType, float impact, float stunReduction, float knockbackResistance) {
        return switch (stunType) {
            case SHORT -> {
                float totalStunTime = (0.25F + impact * 0.1F) * (1.0F - stunReduction);

                if (totalStunTime < 0.075F) {
                    yield 0.0F;
                }

                float stunTime = totalStunTime - 0.1F;
                if (totalStunTime >= 0.83F) {
                    stunTime = 0.83F;
                }

                yield stunTime * (1.0F - knockbackResistance);
            }
            case LONG -> 0.83F * (1.0F - knockbackResistance);
            case KNOCKDOWN -> 2.0F;
            default -> 0.0F;
        };
    }

    private static boolean isNpcAttack(EpicFightDamageSource source) {
        if (source == null) {
            return false;
        }

        Entity attacker = source.getEntity();
        if (attacker == null) {
            attacker = source.getDirectEntity();
        }

        return attacker instanceof EntityNPCInterface;
    }

    private static void clearPending() {
        pendingTarget = null;
        pendingStunTime = 0.0F;
    }
}
