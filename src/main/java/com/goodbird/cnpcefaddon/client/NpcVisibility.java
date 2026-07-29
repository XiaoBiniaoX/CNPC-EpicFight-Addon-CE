package com.goodbird.cnpcefaddon.client;

import com.goodbird.cnpcefaddon.mixin.IDataDisplay;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import noppes.npcs.CustomItems;
import noppes.npcs.CustomNpcs;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * CNPC display visibility for EF-rendered NPCs.
 * <ul>
 *   <li>visible=0 (是) — fully opaque</li>
 *   <li>visible=1 (否) — fully hidden (same gate as {@code RenderCustomNpc})</li>
 *   <li>visible=2 (半透明/Partially) — translucent when visible to player; hidden when not</li>
 * </ul>
 */
public final class NpcVisibility {
    public static final float TRANSLUCENT_ALPHA = 0.15f;

    private NpcVisibility() {}

    public static boolean shouldHideFromClient(Entity entity) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return false;
        }
        return entity instanceof EntityNPCInterface npc && shouldHideFromClient(npc);
    }

    public static boolean shouldHideFromClient(EntityNPCInterface npc) {
        if (!isEfNpcClient(npc)) {
            return false;
        }
        if (!CustomNpcs.EnableInvisibleNpcs || CustomNpcs.InvisibilityAlgorithm <= 0) {
            return false;
        }
        Player player = localPlayer();
        if (player == null || isVisibilityException(player)) {
            return false;
        }
        return !npc.display.isVisibleTo(player);
    }

    /**
     * CNPC UI "半透明" (gui.partly, visible=2): render as ghost when the player is allowed to see the NPC.
     */
    public static boolean shouldRenderTranslucent(Entity entity) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return false;
        }
        return entity instanceof EntityNPCInterface npc && shouldRenderTranslucent(npc);
    }

    public static boolean shouldRenderTranslucent(EntityNPCInterface npc) {
        if (!isEfNpcClient(npc)) {
            return false;
        }
        if (npc.display.getVisible() != 2) {
            return false;
        }
        if (!CustomNpcs.EnableInvisibleNpcs) {
            // Still honour the UI mode even if invisible-npc feature flag is off
            return true;
        }
        Player player = localPlayer();
        if (player == null) {
            return true;
        }
        if (isVisibilityException(player)) {
            return false;
        }
        // If conditions hide the NPC, full hide takes priority
        return npc.display.isVisibleTo(player);
    }

    public static float resolveAlpha(Entity entity, float defaultAlpha) {
        if (shouldRenderTranslucent(entity)) {
            return TRANSLUCENT_ALPHA;
        }
        return defaultAlpha;
    }

    private static boolean isEfNpcClient(EntityNPCInterface npc) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return false;
        }
        return npc != null && npc.display != null && ((IDataDisplay) npc.display).hasEFModel();
    }

    private static Player localPlayer() {
        return Minecraft.getInstance().player;
    }

    private static boolean isVisibilityException(Player player) {
        if (player.isSpectator()) {
            return true;
        }
        return player.getMainHandItem().getItem() == CustomItems.wand;
    }
}
