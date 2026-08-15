package com.goodbird.cnpcefaddon.mixin.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.network.common.AnimatorControlPacket;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * Guards the synchronised animation path: {@code playAnimationSynchronized} builds the
 * {@code SPAnimatorControl} network packet inside {@code handleAnimationPacket}, and the
 * packet constructor dereferences the {@code AssetAccessor} immediately. A combat behavior
 * referencing a missing animation (e.g. datapack of a removed addon like
 * EpicFight-Extra) used to crash with {@code nextAnimation == null} there. Now it is
 * skipped with a log entry.
 */
@Mixin(value = LivingEntityPatch.class, remap = false)
public abstract class MixinLivingEntityPatchPlaySync {

    private static final Logger LOGGER = LoggerFactory.getLogger("cnpcefaddon.syncguard");

    @Inject(
        method = "handleAnimationPacket(Lyesman/epicfight/network/common/AnimatorControlPacket$Action;Lyesman/epicfight/api/asset/AssetAccessor;FLyesman/epicfight/world/capabilities/entitypatch/LivingEntityPatch$ServerAnimationPacketProvider;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void cnpcef$guardMissingAnimation(AnimatorControlPacket.Action action, AssetAccessor<? extends StaticAnimation> animation, float convertTimeModifier, LivingEntityPatch.ServerAnimationPacketProvider provider, CallbackInfo ci) {
        if (animation == null) {
            LOGGER.error("[cnpcefaddon] 同步播放的动画不存在（null 引用），数据包可能引用了已移除 mod 的动画，已跳过");
            ci.cancel();
        }
    }
}
