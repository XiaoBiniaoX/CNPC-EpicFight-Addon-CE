package top.bincnpcef.mixin.impl;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import yesman.epicfight.registry.entries.EpicFightAttributes;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.MobPatch;
import yesman.epicfight.world.damagesource.EpicFightDamageSource;
import yesman.epicfight.world.damagesource.EpicFightDamageSources;

/**
 * 在 {@link LivingEntity#hurt(DamageSource, float)} 入口处转换 DamageSource 为
 * {@link EpicFightDamageSource}，使 EF 能识别 CNPC 的攻击并正确触发僵直、装甲穿透等。
 *
 * <p>转换策略：
 * <ol>
 *   <li>若 DamageSource 已是 EpicFightDamageSource，原样返回。</li>
 *   <li>若攻击者是 Mob 且拥有 MobPatch，优先复用 {@link MobPatch#getEpicFightDamageSource()}
 *       （EF 的 AnimatedAttackGoal 触发攻击时构造的完整源）。</li>
 *   <li>兜底：vanilla AI 攻击时，用 {@link EpicFightDamageSources#fromVanillaDamageSource}
 *       构造新源，并从 Mob 的 EF 属性读取 impact / armorNegation 注入。</li>
 * </ol>
 */
@Mixin(LivingEntity.class)
public class MixinLivingEntityHurt {
    private static final Logger LOGGER = LoggerFactory.getLogger("CNPC-EF-Addon");

    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
    private DamageSource cnpcef$convertToEpicFightSource(DamageSource value) {
        if (value instanceof EpicFightDamageSource) {
            return value;
        }

        if (value.getEntity() instanceof Mob mob) {
            MobPatch<?> patch = EpicFightCapabilities.getEntityPatch(mob, MobPatch.class);
            if (patch != null) {
                EpicFightDamageSource epSource = patch.getEpicFightDamageSource();
                if (epSource != null) {
                    return epSource;
                }

                epSource = EpicFightDamageSources.fromVanillaDamageSource(value);

                try {
                    float impact = (float) mob.getAttributeValue(EpicFightAttributes.IMPACT);
                    float armorNeg = (float) mob.getAttributeValue(EpicFightAttributes.ARMOR_NEGATION);
                    epSource.setBaseImpact(impact);
                    epSource.setBaseArmorNegation(armorNeg);
                } catch (IllegalArgumentException ignored) {
                    LOGGER.warn("MixinLivingEntityHurt: EF attributes not registered on mob {}", mob.getName().getString());
                }

                return epSource;
            }
        }

        return value;
    }
}
