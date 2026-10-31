package com.goodbird.cnpcefaddon.mixin.impl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.client.animation.ClientAnimator;

@Mixin(value = ClientAnimator.class, remap = false)
public interface IAnimatorRevival {
    @Accessor("currentMotion")
    void cnpcef$setCurrentMotion(LivingMotion motion);

    @Accessor("currentCompositeMotion")
    void cnpcef$setCurrentCompositeMotion(LivingMotion motion);
}
