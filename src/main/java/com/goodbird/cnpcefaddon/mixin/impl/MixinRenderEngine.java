package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.client.NpcVisibility;
import com.goodbird.cnpcefaddon.client.render.RenderStorage;
import com.goodbird.cnpcefaddon.mixin.IDataDisplay;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.client.events.engine.RenderEngine;
import yesman.epicfight.client.renderer.patched.entity.PatchedEntityRenderer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

@Mixin(value = RenderEngine.class, remap = false)
public class MixinRenderEngine {

    @Inject(method = "getEntityRenderer(Lnet/minecraft/world/entity/Entity;)Lyesman/epicfight/client/renderer/patched/entity/PatchedEntityRenderer;", at = @At("HEAD"), cancellable = true)
    private void cnpcef$getEntityRenderer(Entity entity, CallbackInfoReturnable<PatchedEntityRenderer> cir) {
        if (!(entity instanceof EntityNPCInterface npc) || npc.display == null) {
            return;
        }
        if (NpcVisibility.shouldHideFromClient(npc)) {
            return;
        }
        ResourceLocation model = ((IDataDisplay) npc.display).getEFModel();
        if (model != null && RenderStorage.renderersMap.containsKey(model)) {
            PatchedEntityRenderer renderer = RenderStorage.renderersMap.get(model);
            if (renderer != null) {
                cir.setReturnValue(renderer);
            }
        }
    }

    @Inject(method = "hasRendererFor", at = @At("HEAD"), cancellable = true)
    private void cnpcef$hasRendererFor(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof EntityNPCInterface npc) || npc.display == null) {
            return;
        }
        if (NpcVisibility.shouldHideFromClient(npc)) {
            cir.setReturnValue(false);
            return;
        }
        ResourceLocation model = ((IDataDisplay) npc.display).getEFModel();
        if (model != null && RenderStorage.renderersMap.containsKey(model)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "renderEntityArmatureModel", at = @At("HEAD"), cancellable = true)
    private void cnpcef$skipHiddenNpc(
            LivingEntity livingEntity,
            LivingEntityPatch<?> entitypatch,
            EntityRenderer<? extends Entity> renderer,
            MultiBufferSource buffer,
            PoseStack matStack,
            int packedLight,
            float partialTicks,
            CallbackInfo ci) {
        if (NpcVisibility.shouldHideFromClient(livingEntity)) {
            ci.cancel();
        }
    }
}
