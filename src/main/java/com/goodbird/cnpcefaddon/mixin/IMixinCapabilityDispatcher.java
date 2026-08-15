package com.goodbird.cnpcefaddon.mixin;

import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.spongepowered.asm.mixin.gen.Accessor;

public interface IMixinCapabilityDispatcher {
    ICapabilityProvider[] getCaps();

    void setCaps(ICapabilityProvider[] arr);
}
