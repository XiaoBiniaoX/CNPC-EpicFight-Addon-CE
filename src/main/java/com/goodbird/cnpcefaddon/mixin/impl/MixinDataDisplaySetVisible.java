package com.goodbird.cnpcefaddon.mixin.impl;

import noppes.npcs.controllers.VisibilityController;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DataDisplay.class, priority = 1100)
public class MixinDataDisplaySetVisible {
    @Shadow(remap = false)
    EntityNPCInterface npc;
    @Shadow(remap = false)
    private int visible;

    @Inject(method = "setVisible(I)V", at = @At("RETURN"), remap = false)
    private void cnpcef$flushVisibleOnReturn(int type, CallbackInfo ci) {
        if (npc == null || npc.level() == null || npc.level().isClientSide()) {
            return;
        }
        if (!npc.updateClient) {
            return;
        }
        VisibilityController.instance.trackNpc(npc);
        npc.updateClient();
    }
}
