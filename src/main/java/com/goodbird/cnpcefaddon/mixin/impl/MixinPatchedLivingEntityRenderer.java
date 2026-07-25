package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.client.EfRenderContext;
import com.goodbird.cnpcefaddon.client.NpcVisibility;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

@Mixin(value = PatchedLivingEntityRenderer.class, remap = false)
public class MixinPatchedLivingEntityRenderer {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void cnpcef$skipHidden(
            LivingEntity entity,
            LivingEntityPatch entitypatch,
            LivingEntityRenderer renderer,
            MultiBufferSource buffer,
            PoseStack poseStack,
            int packedLight,
            float partialTicks,
            CallbackInfo ci) {
        if (NpcVisibility.shouldHideFromClient(entity)) {
            ci.cancel();
            return;
        }
        EfRenderContext.setEntity(entity);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void cnpcef$clearEntity(
            LivingEntity entity,
            LivingEntityPatch entitypatch,
            LivingEntityRenderer renderer,
            MultiBufferSource buffer,
            PoseStack poseStack,
            int packedLight,
            float partialTicks,
            CallbackInfo ci) {
        EfRenderContext.clear();
    }
}
