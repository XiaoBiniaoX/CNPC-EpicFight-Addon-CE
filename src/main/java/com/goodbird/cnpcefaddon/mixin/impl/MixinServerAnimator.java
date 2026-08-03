package com.goodbird.cnpcefaddon.mixin.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.api.animation.ServerAnimator;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;

/**
 * Guards against NullPointerExceptions when a combat behavior tries to play an animation
 * that no longer exists (e.g. a datapack referencing animations of a removed addon like
 * EpicFight-Extra). Instead of crashing, the animation is skipped with a log entry.
 */
@Mixin(value = ServerAnimator.class, remap = false)
public abstract class MixinServerAnimator {

    private static final Logger LOGGER = LoggerFactory.getLogger("cnpcefaddon.syncguard");

    @Inject(method = "playAnimation(Lyesman/epicfight/api/asset/AssetAccessor;F)V", at = @At("HEAD"), cancellable = true)
    private void cnpcef$guardMissingAnimation(AssetAccessor<? extends StaticAnimation> nextAnimation, float convertTimeModifier, CallbackInfo ci) {
        if (nextAnimation == null) {
            LOGGER.warn("[cnpcefaddon] 尝试播放不存在的动画（null 引用），数据包可能引用了已移除 mod 的动画，已跳过");
            ci.cancel();
        }
    }
}
