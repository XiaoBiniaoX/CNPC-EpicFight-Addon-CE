package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.common.GuardProjectileRule;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.skill.guard.GuardSkill;

/**
 * Lets Epic Fight's guard skill block projectiles launched by Custom NPCs.
 * <p>
 * Epic Fight's {@code isBlockableSource} rejects every damage source tagged
 * {@code DamageTypeTags.IS_PROJECTILE}, which is why no projectile could ever be guarded.
 * This hook only runs when the stock method already decided "not blockable" and only flips
 * that answer for a projectile owned by a CNPC (see {@link GuardProjectileRule}); all other
 * rejections -- armour piercing, unblockable, explosion, magic, fire -- keep their result.
 * <p>
 * Every other part of the guard flow is untouched: the animation, sound, particles, stamina
 * cost and the {@code onAttackBlocked} callback still run through Epic Fight's own code.
 * {@code ImpactGuardSkill} overrides this method and is handled separately.
 */
@Mixin(value = GuardSkill.class, remap = false)
public abstract class MixinGuardSkillProjectile {

    @Inject(method = "isBlockableSource", at = @At("RETURN"), cancellable = true)
    private void cnpcef$allowNpcProjectile(DamageSource damageSource, boolean advanced, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && GuardProjectileRule.isNpcProjectile(damageSource)) {
            cir.setReturnValue(true);
        }
    }
}
