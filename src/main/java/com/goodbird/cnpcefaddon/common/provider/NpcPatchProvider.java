package com.goodbird.cnpcefaddon.common.provider;

import com.goodbird.cnpcefaddon.common.patch.NpcPatch;
import net.minecraft.world.entity.Entity;
import yesman.epicfight.api.data.reloader.MobPatchReloadListener;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.world.capabilities.entitypatch.EntityPatch;

public class NpcPatchProvider extends MobPatchReloadListener.CustomMobPatchProvider implements INpcPatchProvider {
    public Armature armature;

    public EntityPatch<?> get(Entity entity) {
        return new NpcPatch<>(this.faction, this);
    }

    @Override
    public void setArmature(Armature armature) {
        this.armature = armature;
    }


}
