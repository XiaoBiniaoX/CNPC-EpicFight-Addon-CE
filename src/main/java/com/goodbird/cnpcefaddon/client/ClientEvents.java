package com.goodbird.cnpcefaddon.client;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;

@Mod.EventBusSubscriber(modid = "cnpcefaddon", value = Dist.CLIENT)
public class ClientEvents {

    /**
     * EF's own Pre listener ignores {@link RenderLivingEvent.Pre#isCanceled()}.
     * We still cancel so vanilla / other mods skip when EF is forced off via overrideRender.
     */
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
}
