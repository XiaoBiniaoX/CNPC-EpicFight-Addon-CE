package com.goodbird.cnpcefaddon.common.provider;

import com.goodbird.cnpcefaddon.common.patch.NpcHumanoidPatch;
import net.minecraft.world.entity.Entity;
import yesman.epicfight.api.data.reloader.MobPatchReloadListener;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.model.armature.HumanoidArmature;
import yesman.epicfight.world.capabilities.entitypatch.EntityPatch;

public class NpcHumanoidPatchProvider extends MobPatchReloadListener.CustomHumanoidMobPatchProvider implements INpcPatchProvider {
    public Armature armature;

    public EntityPatch<?> get(Entity entity) {
        return new NpcHumanoidPatch(this.faction, this);
    }

    @Override
    public void setArmature(Armature armature) {
        this.armature = armature;
    }
}