package com.goodbird.cnpcefaddon.client;

import com.goodbird.cnpcefaddon.common.PlaySpeedCache;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Re-installs the {@code play_speed} modifiers after a client resource reload.
 * <p>
 * Epic Fight keeps a {@code PLAY_SPEED_MODIFIER} inside the {@code StaticAnimation} instance and
 * registers its {@code AnimationManager} as a client resource reload listener. Switching the
 * language or a resource pack therefore clears {@code AnimationManager.animations}, and the
 * animation instances handed out afterwards are new objects with an empty property map - every
 * configured {@code play_speed} stopped applying, while the datapack side (which is what installs
 * the modifier) is not re-read for a resource reload. Re-entering the world fixed it because that
 * runs the datapack listener and the sync packet again.
 * <p>
 * This listener is registered on the same event as Epic Fight's {@code AnimationManager}. Forge
 * runs client reload listeners in registration order, and Epic Fight registers during mod setup
 * while this addon depends on Epic Fight, so {@code AnimationManager} has already rebuilt its
 * animations by the time {@link #onResourceManagerReload} runs.
 * <p>
 * Client only: {@code RegisterClientReloadListenersEvent} does not exist on a dedicated server,
 * hence the {@code Dist.CLIENT} event bus subscriber.
 */
@Mod.EventBusSubscriber(modid = "cnpcefaddon", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class PlaySpeedReloadListener implements ResourceManagerReloadListener {

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new PlaySpeedReloadListener());
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        PlaySpeedCache.reinstallAllModifiers();
    }
}
