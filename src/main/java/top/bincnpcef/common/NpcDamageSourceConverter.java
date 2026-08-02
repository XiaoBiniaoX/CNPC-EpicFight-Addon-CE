package top.bincnpcef.common;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yesman.epicfight.registry.entries.EpicFightAttributes;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.MobPatch;
import yesman.epicfight.world.damagesource.EpicFightDamageSource;
import yesman.epicfight.world.damagesource.EpicFightDamageSources;

/**
 * 将 CNPC 的原始 {@link DamageSource} 转换为 {@link EpicFightDamageSource}，
 * 使 EF 能识别攻击者并正确处理僵直、冲击、装甲穿透。
 *
 * <p>转换策略：
 * <ol>
 *   <li>若已是 EpicFightDamageSource，原样返回。</li>
 *   <li>优先复用 {@link MobPatch#getEpicFightDamageSource()}（EF 的 AnimatedAttackGoal
 *       触发攻击时构造的完整源，含冲击/动画数据）。</li>
 *   <li>兜底：用 {@link EpicFightDamageSources#fromVanillaDamageSource} 构造新源，
 *       并从 Mob 的 EF 属性读取 impact / armorNegation 注入。</li>
 * </ol>
 */
public final class NpcDamageSourceConverter {
    private static final Logger LOGGER = LoggerFactory.getLogger("CNPC-EF-Addon");

    private NpcDamageSourceConverter() {
    }

    public static DamageSource convertMobAttack(Entity attacker, DamageSource source) {
        if (source instanceof EpicFightDamageSource) {
            return source;
        }

        if (!(attacker instanceof Mob mob)) {
            return source;
        }

        MobPatch<?> patch = EpicFightCapabilities.getEntityPatch(mob, MobPatch.class);
        if (patch == null) {
            return source;
        }

        EpicFightDamageSource epSource = patch.getEpicFightDamageSource();
        if (epSource != null) {
            return epSource;
        }

        epSource = EpicFightDamageSources.fromVanillaDamageSource(source);

        try {
            epSource.setBaseImpact((float) mob.getAttributeValue(EpicFightAttributes.IMPACT));
            epSource.setBaseArmorNegation((float) mob.getAttributeValue(EpicFightAttributes.ARMOR_NEGATION));
        } catch (IllegalArgumentException e) {
            LOGGER.warn("NpcDamageSourceConverter: EF attributes not registered on mob {}", mob.getName().getString());
        }

        return epSource;
    }
}
