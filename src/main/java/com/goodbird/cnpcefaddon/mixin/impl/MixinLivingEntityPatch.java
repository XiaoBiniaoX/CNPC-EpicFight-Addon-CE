package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.client.NpcVisibility;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

@Mixin(value = LivingEntityPatch.class, remap = false)
public abstract class MixinLivingEntityPatch {

    @Inject(method = "overrideRender()Z", at = @At("HEAD"), cancellable = true)
    private void cnpcef$disableOverrideWhenHidden(CallbackInfoReturnable<Boolean> cir) {
        Entity original = ((LivingEntityPatch<?>) (Object) this).getOriginal();
        if (NpcVisibility.shouldHideFromClient(original)) {
            cir.setReturnValue(false);
        }
    }
}
