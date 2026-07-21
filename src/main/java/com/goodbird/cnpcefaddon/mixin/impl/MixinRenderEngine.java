package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.client.render.RenderStorage;
import com.goodbird.cnpcefaddon.mixin.IDataDisplay;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.event.RenderHandEvent;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.client.events.engine.RenderEngine;
import yesman.epicfight.client.renderer.patched.entity.PatchedEntityRenderer;

@Mixin(RenderEngine.class)
public class MixinRenderEngine {

    @Inject(method = "getEntityRenderer", at = @At("HEAD"), cancellable = true, remap = false)
    public void getEntityRenderer(Entity entity, CallbackInfoReturnable<PatchedEntityRenderer> cir) {
        if(entity instanceof EntityNPCInterface npc && npc.display != null){
            ResourceLocation model = ((IDataDisplay) npc.display).getEFModel();
            if(model != null && RenderStorage.renderersMap.containsKey(model)){
                PatchedEntityRenderer renderer = RenderStorage.renderersMap.get(model);
                if (renderer != null) {
                    cir.setReturnValue(renderer);
                }
            }
        }
    }

    @Inject(method = "hasRendererFor", at = @At("HEAD"), cancellable = true, remap = false)
    public void hasRendererFor(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if(entity instanceof EntityNPCInterface npc && npc.display != null){
            ResourceLocation model = ((IDataDisplay) npc.display).getEFModel();
            if(model != null && RenderStorage.renderersMap.containsKey(model)){
                cir.setReturnValue(true);
            }
        }
    }
}
