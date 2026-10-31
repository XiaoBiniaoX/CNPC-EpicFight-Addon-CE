package top.bincnpcef.mixin.impl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import yesman.epicfight.api.animation.ServerAnimator;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.asset.AssetAccessor;

@Mixin(value = ServerAnimator.class, remap = false)
public interface IServerAnimatorQueue {
    @Accessor("nextAnimation") AssetAccessor<? extends DynamicAnimation> cnpcef$getNextAnimation();
    @Accessor("nextAnimation") void cnpcef$setNextAnimation(AssetAccessor<? extends DynamicAnimation> animation);
}
