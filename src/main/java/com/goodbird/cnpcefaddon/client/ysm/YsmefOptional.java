package com.goodbird.cnpcefaddon.client.ysm;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * Optional YSM Epic Fight Compat support, kept behind a mod-presence check.
 * <p>
 * Nothing here references a {@code com.ysmef} class directly: the actual work lives in
 * {@link YsmefNpcBridge}, which is only class-loaded once {@code ysm_epicfight_compat} is
 * confirmed present. Without that separation the JVM would resolve the YSM-EF types while
 * verifying this class and fail on an installation without the mod.
 * <p>
 * Client only. Mesh swapping is a render-time concern with no server-side component.
 */
public final class YsmefOptional {

    private static final Logger LOGGER = LoggerFactory.getLogger(YsmefOptional.class);
    private static final String YSMEF_MODID = "ysm_epicfight_compat";

    private YsmefOptional() {
    }

    /** @return whether YSM Epic Fight Compat is present in this installation */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(YSMEF_MODID);
    }

    /**
     * Select the converted YSM base mesh for an NPC with the given YSM model id, or null
     * when the mod is absent, the model has no converted mesh yet, or selection failed.
     * The returned object is Epic Fight's {@code AssetAccessor<HumanoidMesh>}; callers cast
     * it back to the accessor type the current renderer expects.
     */
    public static Object trySelectMesh(LivingEntity entity, String modelId) {
        if (!isLoaded() || entity == null || modelId == null || modelId.isEmpty()) {
            return null;
        }
        try {
            return YsmefNpcBridge.selectMesh(entity, modelId);
        } catch (Throwable t) {
            LOGGER.error("[cnpcef-fix] YSM-EF mesh selection failed; NPC keeps its default Epic Fight mesh", t);
            return null;
        }
    }

    /**
     * The locally available YSM model ids (YSM base-mod library), or an empty list when the
     * mod is absent. Used to populate the NPC creation GUI selection.
     */
    public static List<String> tryAvailableModelIds() {
        if (!isLoaded()) {
            return Collections.emptyList();
        }
        try {
            return YsmefNpcBridge.availableModelIds();
        } catch (Throwable t) {
            LOGGER.error("[cnpcef-fix] failed to list YSM models", t);
            return Collections.emptyList();
        }
    }
}