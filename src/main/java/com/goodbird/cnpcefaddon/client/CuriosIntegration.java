package com.goodbird.cnpcefaddon.client;

import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional Curios support, kept behind a mod-presence check.
 * <p>
 * Nothing here touches a Curios class directly: the actual work lives in
 * {@link CuriosNpcLayerInstaller}, which is only class-loaded once {@code curios} is confirmed
 * present. Without that separation the JVM would resolve the Curios types while verifying this
 * class and fail on an installation that does not have the mod.
 * <p>
 * Client only. Accessory rendering has no server-side component, and Curios itself supplies the
 * inventory data through its own capability sync.
 */
public final class CuriosIntegration {

    private static final Logger LOGGER = LoggerFactory.getLogger(CuriosIntegration.class);
    private static final String CURIOS_MODID = "curios";

    private CuriosIntegration() {
    }

    /** @return whether Curios is present in this installation */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(CURIOS_MODID);
    }

    /**
     * Installs the accessory layer on the NPC renderers built by the last datapack reload.
     * <p>
     * Safe to call repeatedly: the underlying registration replaces any previous entry instead of
     * adding a second one. Any failure is logged and swallowed - a missing accessory layer must
     * never take the NPC's own model down with it.
     */
    public static void installNpcAccessoryLayer() {
        if (!isLoaded()) {
            return;
        }

        try {
            CuriosNpcLayerInstaller.install();
        } catch (Throwable t) {
            LOGGER.error("[cnpcef-fix] curios optional support failed; NPC models keep rendering without accessories", t);
        }
    }
}
