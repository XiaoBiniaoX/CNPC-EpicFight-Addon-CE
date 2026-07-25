package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.client.NpcVisibility;
import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.RenderLivingEvent;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.client.events.engine.RenderEngine;

@Mixin(value = RenderEngine.Events.class, remap = false)
public class MixinRenderEngineEvents {

    @Inject(method = "renderLivingEvent", at = @At("HEAD"), cancellable = true)
    private static void cnpcef$skipInvisibleNpc(
            RenderLivingEvent.Pre<? extends LivingEntity, ? extends EntityModel<? extends LivingEntity>> event,
            CallbackInfo ci) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof EntityNPCInterface npc && NpcVisibility.shouldHideFromClient(npc)) {
            event.setCanceled(true);
            ci.cancel();
        }
    }
}
