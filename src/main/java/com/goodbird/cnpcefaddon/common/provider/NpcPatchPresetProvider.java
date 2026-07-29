package com.goodbird.cnpcefaddon.common.provider;

import net.minecraft.world.entity.Entity;
import yesman.epicfight.api.data.reloader.MobPatchReloadListener;
import yesman.epicfight.world.capabilities.entitypatch.EntityPatch;

import java.util.function.Function;
import java.util.function.Supplier;

public class NpcPatchPresetProvider extends MobPatchReloadListener.MobPatchPresetProvider {
    public NpcPatchPresetProvider(Function<Entity, Supplier<EntityPatch<?>>> presetProvider) {
        super(presetProvider);
    }

    public EntityPatch<?> get(Entity entity) {
        return (this.presetProvider.apply(entity)).get();
    }
}
