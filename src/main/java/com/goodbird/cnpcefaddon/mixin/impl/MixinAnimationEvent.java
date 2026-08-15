package com.goodbird.cnpcefaddon.mixin.impl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yesman.epicfight.api.animation.property.AnimationEvent;
import yesman.epicfight.api.animation.property.AnimationParameters;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

@Mixin(value = AnimationEvent.class, remap = false)
public abstract class MixinAnimationEvent {

    @Redirect(
            method = {"execute", "executeWithNewParams"},
            at = @At(
                    value = "INVOKE",
                    target = "Lyesman/epicfight/api/animation/property/AnimationEvent$Event;fire(Lyesman/epicfight/world/capabilities/entitypatch/LivingEntityPatch;Lyesman/epicfight/api/asset/AssetAccessor;Lyesman/epicfight/api/animation/property/AnimationParameters;)V"
            )
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void cnpcef$safeFire(
            AnimationEvent.Event event,
            LivingEntityPatch entitypatch,
            AssetAccessor animation,
            AnimationParameters params
    ) {
        try {
            event.fire(entitypatch, animation, params);
        } catch (ClassCastException | IllegalArgumentException | NullPointerException ignored) {
        }
    }
}
