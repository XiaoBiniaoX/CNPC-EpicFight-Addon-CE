package top.bincnpcef.mixin.impl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import yesman.epicfight.api.client.animation.Layer;

@Mixin(value = Layer.BaseLayer.class, remap = false)
public interface IBaseLayerPriority {
    @Accessor("baseLayerPriority") void cnpcef$setBaseLayerPriority(Layer.Priority priority);
}
