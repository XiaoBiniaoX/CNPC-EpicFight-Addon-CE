package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.common.NpcDamageModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * Combines held-weapon damage with the NPC's configured melee strength for EF-patched NPCs.
 * <p>
 * Epic Fight funnels the final amount through {@code getModifiedBaseDamage} in
 * {@code EntityEvents.hurtEvent}, which makes it the single place both the vanilla attack
 * path and skill-driven hits pass through. See {@link NpcDamageModel} for the formula.
 * <p>
 * {@code AdvancedCustomHumanoidMobPatch} overrides this method to apply Indestructible's own
 * damage modifier, so the injection targets {@link LivingEntityPatch} and both
 * implementations are covered: the advanced patch calls its multiplier first and this runs on
 * the result.
 */
@Mixin(value = LivingEntityPatch.class, remap = false)
public abstract class MixinLivingEntityPatchDamage {

    @Inject(method = "getModifiedBaseDamage", at = @At("RETURN"), cancellable = true)
    private void cnpcef$combineNpcDamage(float baseDamage, CallbackInfoReturnable<Float> cir) {
        float resolved = NpcDamageModel.resolve(
                (LivingEntityPatch<?>) (Object) this, baseDamage, cir.getReturnValueF());

        if (resolved != cir.getReturnValueF()) {
            cir.setReturnValue(resolved);
        }
    }
}
