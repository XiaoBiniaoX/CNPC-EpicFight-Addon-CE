package top.bincnpcef.mixin.impl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.animation.Layer;

@Mixin(value = Layer.class, remap = false)
public interface ILayerAnimationQueue {
    @Accessor("nextAnimation") void cnpcef$setNextAnimation(AssetAccessor<? extends StaticAnimation> animation);
}
