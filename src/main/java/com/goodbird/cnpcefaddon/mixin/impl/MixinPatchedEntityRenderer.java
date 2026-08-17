package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.client.ysm.YsmefOptional;
import com.goodbird.cnpcefaddon.mixin.IDataDisplay;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.client.renderer.patched.entity.PatchedEntityRenderer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * Swaps the patched renderer's mesh for a YSM-converted one when the NPC's data pack uses
 * an Epic Fight humanoid renderer and the NPC has a YSM model selected.
 *
 * {@code PatchedLivingEntityRenderer#render} obtains the mesh via
 * {@code getMeshProvider(entitypatch)} (see its render L134), which every humanoid patched
 * renderer -- {@code PCustomHumanoidEntityRenderer} and the basic humanoid
 * {@code PHumanoidRenderer} used for "player"/"zombie"+biped -- inherits unchanged from
 * {@code PatchedEntityRenderer}. Injecting at this single point covers both without touching
 * their fixed fields. {@code PCustomEntityRenderer} draws {@code this.mesh} directly and
 * never calls {@code getMeshProvider}, so the non-humanoid path is unaffected by design.
 *
 * Old behavior is preserved exactly: an NPC without a YSM model (or without the
 * ysm_epicfight_compat mod installed) falls through to the original mesh.
 */
@Mixin(value = PatchedEntityRenderer.class, remap = false)
public abstract class MixinPatchedEntityRenderer {

    @Inject(method = "getMeshProvider", at = @At("HEAD"), cancellable = true)
    private void cnpcef$swapYsmMesh(LivingEntityPatch<?> entitypatch,
                                    CallbackInfoReturnable<AssetAccessor<?>> cir) {
        LivingEntity entity = entitypatch.getOriginal();
        if (!(entity instanceof EntityNPCInterface npc) || npc.display == null) {
            return;
        }
        if (!(npc.display instanceof IDataDisplay display) || !display.hasEFModel() || !display.hasYsmModel()) {
            return;
        }
        Object mesh = YsmefOptional.trySelectMesh(npc, display.getYsmModel());
        if (mesh instanceof AssetAccessor<?> accessor) {
            cir.setReturnValue(accessor);
        }
    }
}