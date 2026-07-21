package com.goodbird.cnpcefaddon.mixin.impl;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.entitypatch.MobPatch;
import yesman.epicfight.world.damagesource.EpicFightDamageSource;
import yesman.epicfight.world.damagesource.EpicFightDamageSources;
import yesman.epicfight.world.entity.ai.attribute.EpicFightAttributes;

@Mixin(LivingEntity.class)
public class MixinLivingEntityHurt {

    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
    private DamageSource convertToEpicFightSource(DamageSource value) {
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

                if (mob.getAttributes().hasAttribute(EpicFightAttributes.IMPACT.get())) {
                    epSource.setBaseImpact((float) mob.getAttributeValue(EpicFightAttributes.IMPACT.get()));
                }
                if (mob.getAttributes().hasAttribute(EpicFightAttributes.ARMOR_NEGATION.get())) {
                    epSource.setBaseArmorNegation((float) mob.getAttributeValue(EpicFightAttributes.ARMOR_NEGATION.get()));
                }

                return epSource;
            }
        }

        return value;
    }
}
