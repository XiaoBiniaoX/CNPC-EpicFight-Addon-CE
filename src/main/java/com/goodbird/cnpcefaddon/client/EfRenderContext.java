package com.goodbird.cnpcefaddon.client;

import net.minecraft.world.entity.Entity;

public final class EfRenderContext {
    private static final ThreadLocal<Entity> CURRENT_ENTITY = new ThreadLocal<>();

    private EfRenderContext() {}

    public static void setEntity(Entity entity) {
        CURRENT_ENTITY.set(entity);
    }

    public static Entity getEntity() {
        return CURRENT_ENTITY.get();
    }

    public static void clear() {
        CURRENT_ENTITY.remove();
    }
}
