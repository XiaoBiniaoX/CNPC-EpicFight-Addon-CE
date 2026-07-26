package com.goodbird.cnpcefaddon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;

@Mod.EventBusSubscriber(modid = "cnpcefaddon", value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof EntityNPCInterface npc && NpcVisibility.shouldHideFromClient(npc)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (event.getEntity() instanceof EntityNPCInterface npc && NpcVisibility.shouldHideFromClient(npc)) {
            event.setResult(Event.Result.DENY);
        }
    }

    /**
     * EF's ControlEngine cancels the USE key when USE/GUARD share the same binding,
     * which prevents CNPC interact scripts from firing on EF-patched NPCs.
     * Un-cancel if the crosshair target is a CNPC NPC so the interaction reaches the server.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isCanceled()) return;
        if (!event.isUseItem()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof EntityNPCInterface) {
            event.setCanceled(false);
        }
    }
}
