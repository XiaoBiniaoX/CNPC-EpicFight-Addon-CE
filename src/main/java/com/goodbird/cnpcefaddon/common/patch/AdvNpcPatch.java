package com.goodbird.cnpcefaddon.common.patch;

import com.goodbird.cnpcefaddon.common.provider.AdvNpcPatchProvider;
import com.nameless.indestructible.world.capability.AdvancedCustomHumanoidMobPatch;
import net.minecraft.world.entity.PathfinderMob;
import noppes.npcs.entity.EntityNPCInterface;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.main.EpicFightMod;
import yesman.epicfight.main.EpicFightSharedConstants;
import yesman.epicfight.world.capabilities.entitypatch.Faction;

public class AdvNpcPatch<T extends PathfinderMob> extends AdvancedCustomHumanoidMobPatch<T> implements INpcPatch {
    AdvNpcPatchProvider provider;

    public AdvNpcPatch(Faction faction, AdvNpcPatchProvider provider) {
        super(faction, provider);
        this.provider = provider;
    }

    public void onConstructed(T entityIn) {
        this.original = entityIn;
        this.armature = provider.armature.deepCopy();
        this.animator = EpicFightSharedConstants.getAnimator(this);
        this.initAnimator(animator);
        animator.postInit();
        this.capabilityState.entityConstructed();
    }

    public OpenMatrix4f getModelMatrix(float partialTicks) {
        EntityNPCInterface npc = (EntityNPCInterface) original;
        float scale = (npc.display != null) ? npc.display.getSize() / 5f : 1f;
        return super.getModelMatrix(partialTicks).scale(scale, scale, scale);
    }
}
