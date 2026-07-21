package com.goodbird.cnpcefaddon.mixin.impl;

import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.CapabilityProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CapabilityProvider.class)
public interface MixinCapabilityProvider {
    @Invoker(remap = false)
    CapabilityDispatcher invokeGetCapabilities();
}
