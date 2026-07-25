package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.client.EfRenderContext;
import com.goodbird.cnpcefaddon.client.NpcVisibility;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import yesman.epicfight.api.client.model.SkinnedMesh;

@Mixin(value = SkinnedMesh.class, remap = false)
public class MixinSkinnedMeshDraw {

    @ModifyVariable(
            method = "draw(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;IFFFFILyesman/epicfight/api/model/Armature;[Lyesman/epicfight/api/utils/math/OpenMatrix4f;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 3)
    private float cnpcef$alpha(float alpha) {
        Entity entity = EfRenderContext.getEntity();
        if (entity != null) {
            return NpcVisibility.resolveAlpha(entity, alpha);
        }
        return alpha;
    }
}
