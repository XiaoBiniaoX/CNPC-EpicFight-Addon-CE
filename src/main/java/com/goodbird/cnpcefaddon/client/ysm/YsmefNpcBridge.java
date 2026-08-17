package com.goodbird.cnpcefaddon.client.ysm;

import com.ysmef.compat.model.YSMMeshLibrary;
import com.ysmef.compat.renderer.YSMMeshSelector;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.client.mesh.HumanoidMesh;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime bridge into the YSM Epic Fight Compat mod. Only class-loaded through
 * {@link YsmefOptional} once {@code ysm_epicfight_compat} is confirmed present, so this
 * class's {@code com.ysmef} references are never resolved on an installation without it.
 * <p>
 * The mesh-selection path is entity-agnostic in the upstream mod (see
 * {@code YSMMeshSelector#selectMeshForModel(LivingEntity, String, String, String)}), so an
 * NPC simply plays the role of the "rendered entity": YSM-EF wraps the converted base mesh
 * ({@code YSMMesh}), injects the runtime model id, sets the current-entity ThreadLocal for
 * {@code YSMRuntimeBridge} and returns the accessor Epic Fight's renderer draws with.
 */
public final class YsmefNpcBridge {

    private YsmefNpcBridge() {
    }

    /** Select the converted base mesh for the NPC's current YSM model, or null. */
    public static AssetAccessor<HumanoidMesh> selectMesh(LivingEntity entity, String modelId) {
        return YSMMeshSelector.selectMeshForModel(entity, modelId, "", entity.getName().getString());
    }

    /** The locally available YSM model ids from the YSM base-mod library. */
    public static List<String> availableModelIds() {
        return new ArrayList<>(YSMMeshLibrary.availableModelIds());
    }
}