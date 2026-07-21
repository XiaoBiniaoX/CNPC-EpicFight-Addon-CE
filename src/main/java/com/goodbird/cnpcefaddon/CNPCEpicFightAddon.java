package com.goodbird.cnpcefaddon;

import com.goodbird.cnpcefaddon.common.AdvNpcPatchReloader;
import com.goodbird.cnpcefaddon.common.NpcPatchReloadListener;
import com.goodbird.cnpcefaddon.common.network.NetworkHandler;
import com.goodbird.cnpcefaddon.common.network.SPDatapackSync;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import noppes.npcs.CustomEntities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(CNPCEpicFightAddon.MODID)
public class CNPCEpicFightAddon {
    public static final String MODID = "cnpcefaddon";
    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCEpicFightAddon.class);

    public CNPCEpicFightAddon(){
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::doCommonStuff);
        MinecraftForge.EVENT_BUS.addListener(this::reloadListenerEvent);
        MinecraftForge.EVENT_BUS.addListener(this::onDatapackSync);
    }

    private void doCommonStuff(FMLCommonSetupEvent event) {
        NetworkHandler.register();
    }

    private void reloadListenerEvent(AddReloadListenerEvent event) {
        event.addListener(new NpcPatchReloadListener());
        if(ModList.get().isLoaded("indestructible")){
            try {
                event.addListener((PreparableReloadListener) Class.forName("com.goodbird.cnpcefaddon.common.AdvNpcPatchReloader").getConstructor().newInstance());
            }catch (Exception e){
                LOGGER.error("Failed to load AdvNpcPatchReloader", e);
            }
        }
    }

    private void onDatapackSync(OnDatapackSyncEvent event) {
        ServerPlayer player = event.getPlayer();
        SPDatapackSync mobPatchPacket = new SPDatapackSync(NpcPatchReloadListener.TAGMAP.size());
        for(CompoundTag tag : NpcPatchReloadListener.getDataStream().toList()){
            mobPatchPacket.write(tag);
        }
        var errors = new java.util.HashMap<>(NpcPatchReloadListener.loadErrors);
        NpcPatchReloadListener.loadErrors.clear();
        if (!errors.isEmpty()) {
            if (player != null) {
                for (var entry : errors.entrySet()) {
                    player.sendSystemMessage(Component.literal(
                        "§c[CNPC-EF Addon CE] 数据包加载失败:\n" +
                        "§7路径: " + entry.getKey() + "\n" +
                        "§7原因: " + entry.getValue() + "\n" +
                        "§7已跳过该数据包。"
                    ));
                }
            } else {
                for (ServerPlayer serverPlayer : event.getPlayerList().getPlayers()) {
                    for (var entry : errors.entrySet()) {
                        serverPlayer.sendSystemMessage(Component.literal(
                            "§c[CNPC-EF Addon CE] 数据包加载失败:\n" +
                            "§7路径: " + entry.getKey() + "\n" +
                            "§7原因: " + entry.getValue() + "\n" +
                            "§7已跳过该数据包。"
                        ));
                    }
                }
            }
        }
        if (player != null) {
            if (!player.getServer().isSingleplayerOwner(player.getGameProfile())) {
                NetworkHandler.send(player, mobPatchPacket);
            }
        } else {
            event.getPlayerList().getPlayers().forEach((serverPlayer) -> {
                NetworkHandler.send(serverPlayer, mobPatchPacket);
            });
        }

    }

}
