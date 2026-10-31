package top.bincnpcef.mixin.impl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.api.animation.AnimationClip;
import yesman.epicfight.api.animation.Pose;

@Mixin(value = AnimationClip.class, remap = false)
public class MixinAnimationClip {
    @Inject(method = "getPoseInTime", at = @At("HEAD"), cancellable = true)
    private void cnpcef$finiteTime(float time, CallbackInfoReturnable<Pose> cir) {
        // NaN 会使 EF 二分查找全部比较为 false，游标不推进而冻结线程。
        if (!Float.isFinite(time)) cir.setReturnValue(new Pose());
    }
}
