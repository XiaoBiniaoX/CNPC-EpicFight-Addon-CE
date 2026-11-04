package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.common.GuardProjectileRule;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.skill.guard.ImpactGuardSkill;

/**
 * Same relaxation as {@link MixinGuardSkillProjectile}, for the energizing / impact guard.
 * <p>
 * {@code ImpactGuardSkill} overrides {@code isBlockableSource} instead of calling the parent
 * one, so the {@code GuardSkill} hook never executes for it. Its own condition already accepts
 * every non-projectile source and also accepts anything once {@code advanced} is true, so only
 * the "normal guard with a weapon that has no advanced guard motion" case needs help here.
 * <p>
 * When the source becomes blockable, {@code ImpactGuardSkill.dealEvent} still applies its own
 * projectile handling: reduced damage through {@code damage_reducer} plus the guard animation,
 * which is exactly how Epic Fight treats other blockable special sources for this skill.
 */
@Mixin(value = ImpactGuardSkill.class, remap = false)
public abstract class MixinImpactGuardSkillProjectile {

    @Inject(method = "isBlockableSource", at = @At("RETURN"), cancellable = true)
    private void cnpcef$allowNpcProjectile(DamageSource damageSource, boolean advanced, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && GuardProjectileRule.isNpcProjectile(damageSource)) {
            cir.setReturnValue(true);
        }
    }
}
