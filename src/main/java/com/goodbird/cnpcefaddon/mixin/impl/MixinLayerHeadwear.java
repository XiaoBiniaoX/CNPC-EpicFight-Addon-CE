package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.mixin.IDataDisplay;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import noppes.npcs.client.layer.LayerHeadwear;
import noppes.npcs.client.layer.LayerInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LayerHeadwear.class)
public abstract class MixinLayerHeadwear extends LayerInterface {
    public MixinLayerHeadwear(LivingEntityRenderer render) {
        super(render);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    public void render(PoseStack mStack, MultiBufferSource typeBuffer, int lightmapUV, float limbSwing, float limbSwingAmount, float partialTicks, float age, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if(npc.display!=null && ((IDataDisplay)npc.display).hasEFModel()){
            ci.cancel();
        }
    }
}
