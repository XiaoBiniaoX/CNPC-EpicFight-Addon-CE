package com.goodbird.cnpcefaddon.common;

import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class CapabilityCacheRefresher {
    private static final Logger LOGGER = LogManager.getLogger("cnpcefaddon");

    public static void refresh(CapabilityDispatcher dispatcher, ICapabilityProvider[] caps) {
        if (ModernFixHandler.AVAILABLE) {
            try {
                ModernFixHandler.refresh(dispatcher, caps);
            } catch (Exception e) {
                LOGGER.error("Failed to refresh capability dispatcher cache", e);
            }
        }
    }

    private static class ModernFixHandler {
        static final boolean AVAILABLE;
        private static final Field TURBO_FIELD;
        private static final Method GEN_METHOD;

        static {
            boolean avail = false;
            Field f = null;
            Method m = null;
            try {
                f = CapabilityDispatcher.class.getDeclaredField("mfix$turboDispatcher");
                f.setAccessible(true);
                Class<?> genClass = Class.forName("org.embeddedt.modernfix.forge.capability.CapabilityProviderDispatcherGenerator");
                m = genClass.getMethod("getOrGenerateDispatcher", ICapabilityProvider[].class);
                avail = true;
            } catch (Exception ignored) {
            }
            AVAILABLE = avail;
            TURBO_FIELD = f;
            GEN_METHOD = m;
        }

        static void refresh(CapabilityDispatcher dispatcher, ICapabilityProvider[] caps) throws Exception {
            if (TURBO_FIELD.get(dispatcher) != null) {
                Object newTurbo = GEN_METHOD.invoke(null, (Object) caps);
                TURBO_FIELD.set(dispatcher, newTurbo);
            }
        }
    }
}
