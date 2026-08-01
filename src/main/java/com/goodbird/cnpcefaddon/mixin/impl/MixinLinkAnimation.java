package com.goodbird.cnpcefaddon.mixin.impl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.animation.types.LinkAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * Guards {@code LinkAnimation.getPlaySpeed} against being called before the link is connected.
 * <p>
 * {@code StaticAnimation.setLinkAnimation} invokes the animation's PLAY_SPEED_MODIFIER at
 * line 186 but only calls {@code dest.setConnectedAnimations(...)} at line 200, so the
 * {@code LinkAnimation} handed to the modifier still has a null {@code toAnimation}. Modifiers
 * that inspect the animation they are given -- Epic Fight Nightfall's
 * {@code calculateWeaponSpeedWithCap} calls {@code getPlaySpeed} on it -- therefore dereference
 * null and kill the server thread mid-tick.
 * <p>
 * Returning Epic Fight's own default of 1.0 in that window is correct: the real speed is
 * recomputed from the connected animation on every subsequent call.
 */
@Mixin(value = LinkAnimation.class, remap = false)
public abstract class MixinLinkAnimation {

    @Shadow
    protected AssetAccessor<? extends StaticAnimation> toAnimation;

    @Inject(method = "getPlaySpeed", at = @At("HEAD"), cancellable = true)
    private void cnpcef$playSpeedBeforeConnected(LivingEntityPatch<?> entitypatch,
                                                 DynamicAnimation animation,
                                                 CallbackInfoReturnable<Float> cir) {
        if (this.toAnimation == null) {
            cir.setReturnValue(1.0F);
        }
    }
}
