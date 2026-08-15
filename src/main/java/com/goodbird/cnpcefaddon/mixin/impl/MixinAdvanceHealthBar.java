package com.goodbird.cnpcefaddon.mixin.impl;

import com.nameless.indestructible.client.gui.AdvanceHealthBar;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

@Mixin(value = AdvanceHealthBar.class, remap = false)
public class MixinAdvanceHealthBar {

    @Inject(method = "shouldDraw(Lnet/minecraft/world/entity/LivingEntity;Lyesman/epicfight/world/capabilities/entitypatch/LivingEntityPatch;Lyesman/epicfight/client/world/capabilites/entitypatch/player/LocalPlayerPatch;F)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    public void onShouldDraw(LivingEntity entity, LivingEntityPatch<?> entitypatch, LocalPlayerPatch playerpatch, float partialTicks, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
