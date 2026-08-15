package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.mixin.IAttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.spongepowered.asm.mixin.*;

@Mixin(AttributeMap.class)
public class MixinAttributeMap implements IAttributeMap {

    @Mutable
    @Shadow private AttributeSupplier supplier;

    @Unique
    public AttributeSupplier getSupplier(){
        return supplier;
    }

    @Unique
    public void setSupplier(AttributeSupplier supplier){
        this.supplier = supplier;
    }
}
