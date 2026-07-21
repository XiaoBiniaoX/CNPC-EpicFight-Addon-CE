package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.mixin.IMixinCapabilityDispatcher;
import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(CapabilityDispatcher.class)
public class MixinCapabilityDispatcher2 implements IMixinCapabilityDispatcher {

    @Mutable
    @Shadow(remap = false) private ICapabilityProvider[] caps;

    @Unique
    public ICapabilityProvider[] getCaps(){
        return caps;
    }

    @Unique
    public void setCaps(ICapabilityProvider[] arr){
        this.caps = arr;
    }
}
