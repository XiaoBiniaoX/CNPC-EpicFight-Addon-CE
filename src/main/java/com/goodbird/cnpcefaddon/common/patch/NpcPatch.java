package com.goodbird.cnpcefaddon.common.patch;

import com.goodbird.cnpcefaddon.common.provider.NpcPatchProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.main.EpicFightMod;
import yesman.epicfight.main.EpicFightSharedConstants;
import yesman.epicfight.world.capabilities.entitypatch.CustomMobPatch;
import yesman.epicfight.world.capabilities.entitypatch.Faction;

public class NpcPatch<T extends PathfinderMob> extends CustomMobPatch<T> implements INpcPatch {
    NpcPatchProvider provider;

    public NpcPatch(Faction faction, NpcPatchProvider provider) {
        super(faction, provider);
        this.provider = provider;
    }

    public void onConstructed(T entityIn) {
        this.original = entityIn;
        this.armature = provider.armature.deepCopy();
        this.animator = EpicFightSharedConstants.getAnimator(this);
        this.initAnimator(animator);
        animator.postInit();
    }

    public OpenMatrix4f getModelMatrix(float partialTicks) {
        EntityNPCInterface npc = (EntityNPCInterface) original;
        float scale = (npc.display != null) ? npc.display.getSize() / 5f : 1f;
        return super.getModelMatrix(partialTicks).scale(scale, scale, scale);
    }
}
