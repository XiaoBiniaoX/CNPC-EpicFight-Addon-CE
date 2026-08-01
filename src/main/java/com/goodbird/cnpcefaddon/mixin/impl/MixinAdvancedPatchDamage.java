package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.common.NpcDamageModel;
import com.nameless.indestructible.world.capability.AdvancedCustomHumanoidMobPatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * Applies the NPC damage model to advanced (Indestructible) patches.
 * <p>
 * {@code AdvancedCustomHumanoidMobPatch} overrides {@code getModifiedBaseDamage} without
 * calling super, so the injection on {@link LivingEntityPatch} never runs for these NPCs.
 * This hook covers them, running after Indestructible's own damage multiplier.
 * See {@link NpcDamageModel} for the formula.
 */
@Mixin(value = AdvancedCustomHumanoidMobPatch.class, remap = false)
public abstract class MixinAdvancedPatchDamage {

    @Inject(method = "getModifiedBaseDamage", at = @At("RETURN"), cancellable = true)
    private void cnpcef$combineNpcDamage(float baseDamage, CallbackInfoReturnable<Float> cir) {
        float resolved = NpcDamageModel.resolve(
                (LivingEntityPatch<?>) (Object) this, baseDamage, cir.getReturnValueF());

        if (resolved != cir.getReturnValueF()) {
            cir.setReturnValue(resolved);
        }
    }
}
