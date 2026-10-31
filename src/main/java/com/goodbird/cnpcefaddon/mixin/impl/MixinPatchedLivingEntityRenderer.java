package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.client.EfRenderContext;
import com.goodbird.cnpcefaddon.client.NpcVisibility;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.client.renderer.RenderCustomNpc;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

@Mixin(value = PatchedLivingEntityRenderer.class, remap = false)
public class MixinPatchedLivingEntityRenderer {

    // CNPC外观模型把装备层包装成匿名RenderLayer，EF的精确class匹配不会展开它。
    // 只替换原版层的枚举来源：装备/骨架仍来自原NPC，customLayers仍只绘制一次。
    @Redirect(method = "renderLayer",
            at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;", ordinal = 0),
            require = 1)
    private Iterator<?> cnpcef$modelLayers(
            List<?> layers, LivingEntityRenderer renderer, LivingEntityPatch entitypatch,
            LivingEntity entity, OpenMatrix4f[] poses, MultiBufferSource buffer,
            PoseStack poseStack, int packedLight, float partialTicks) {
        if (entity instanceof EntityCustomNpc npc && renderer instanceof RenderCustomNpc
                && ((IRenderCustomNpc) renderer).cnpcef$getRenderEntity() instanceof RenderCustomNpc modelRenderer) {
            return npc.display.isShowingLayers() ? modelRenderer.npclayers.iterator() : Collections.emptyIterator();
        }
        return layers.iterator();
    }

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
