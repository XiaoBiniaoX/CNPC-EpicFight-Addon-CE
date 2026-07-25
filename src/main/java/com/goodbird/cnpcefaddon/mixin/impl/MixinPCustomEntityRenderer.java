package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.client.EfRenderContext;
import com.goodbird.cnpcefaddon.client.NpcVisibility;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.client.renderer.patched.entity.PCustomEntityRenderer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

@Mixin(value = PCustomEntityRenderer.class, remap = false)
public class MixinPCustomEntityRenderer {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void cnpcef$skipHidden(
            LivingEntity entity,
            LivingEntityPatch<LivingEntity> entitypatch,
            EntityRenderer<LivingEntity> renderer,
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
            LivingEntityPatch<LivingEntity> entitypatch,
            EntityRenderer<LivingEntity> renderer,
            MultiBufferSource buffer,
            PoseStack poseStack,
            int packedLight,
            float partialTicks,
            CallbackInfo ci) {
        EfRenderContext.clear();
    }

    @ModifyVariable(method = "render", at = @At(value = "STORE", ordinal = 0), ordinal = 0, require = 0)
    private RenderType cnpcef$translucentType(
            RenderType original,
            LivingEntity entity,
            LivingEntityPatch<LivingEntity> entitypatch,
            EntityRenderer<LivingEntity> renderer,
            MultiBufferSource buffer,
            PoseStack poseStack,
            int packedLight,
            float partialTicks) {
        if (NpcVisibility.shouldRenderTranslucent(entity) && original != null) {
            ResourceLocation texture = renderer.getTextureLocation(entity);
            return RenderType.entityTranslucent(texture);
        }
        return original;
    }
}
