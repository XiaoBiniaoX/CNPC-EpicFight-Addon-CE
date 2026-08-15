package com.goodbird.cnpcefaddon.mixin;

import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.spongepowered.asm.mixin.Unique;

public interface IAttributeMap {

    AttributeSupplier getSupplier();
    void setSupplier(AttributeSupplier supplier);
}
