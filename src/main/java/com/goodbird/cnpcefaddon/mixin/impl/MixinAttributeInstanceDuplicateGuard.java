package com.goodbird.cnpcefaddon.mixin.impl;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EF refreshes held-item modifiers through a forEach lambda. CNPC can emit a
 * second equipment-change event during that refresh, so the same modifier is
 * submitted twice and vanilla throws instead of treating it as a no-op.
 */
@Mixin(AttributeInstance.class)
public abstract class MixinAttributeInstanceDuplicateGuard {
    @Inject(method = "addTransientModifier", at = @At("HEAD"), cancellable = true)
    private void cnpcef$skipExistingModifier(AttributeModifier modifier, CallbackInfo ci) {
        if (((AttributeInstance) (Object) this).hasModifier(modifier)) {
            ci.cancel();
        }
    }
}
