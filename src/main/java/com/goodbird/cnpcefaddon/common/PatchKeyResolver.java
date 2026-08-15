package com.goodbird.cnpcefaddon.common;

import com.goodbird.cnpcefaddon.mixin.IDataDisplay;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * Resolves the datapack key that owns an entity's Epic Fight patch.
 * <p>
 * NPC patches are keyed by the datapack file name (e.g. {@code customnpcs:samurai}), not by
 * the entity type, because every NPC shares the single {@code customnpcs:customnpc} entity
 * type while picking a different patch through {@code DataDisplay}'s EF model field.
 * Anything that looks up per-patch data must therefore ask the display, and only fall back
 * to the entity type for non-NPC entities.
 */
public final class PatchKeyResolver {

    private PatchKeyResolver() {
    }

    public static ResourceLocation resolve(Entity entity) {
        if (entity == null) {
            return null;
        }

        if (entity instanceof EntityNPCInterface npc && npc.display instanceof IDataDisplay display) {
            if (display.hasEFModel()) {
                return display.getEFModel();
            }
        }

        return EntityType.getKey(entity.getType());
    }
}
