package com.goodbird.cnpcefaddon.common.provider;

import com.goodbird.cnpcefaddon.common.patch.CeNpcPatch;
import net.minecraft.world.entity.Entity;
import net.shelmarow.combat_evolution.ai.CEPatchReloadListener;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.world.capabilities.entitypatch.EntityPatch;

/**
 * CE 数据包的 CNPC 专用 provider。
 *
 * <p>CE 原版按 EntityType 绑定 patch，而全部 CNPC 共用 customnpcs:customnpc；本 provider
 * 由我方 NpcBranchPatchProvider 按 efModel 分派，避免一份 CE 数据误套到全部 NPC。
 */
public final class CeNpcPatchProvider extends CEPatchReloadListener.CEDatapackMobPatchProvider {
    public AssetAccessor<? extends Armature> armature;

    @Override
    public EntityPatch<?> get(Entity entity) {
        if (entity instanceof net.minecraft.world.entity.Mob mob) {
        }
        return new CeNpcPatch(this);
    }
}
