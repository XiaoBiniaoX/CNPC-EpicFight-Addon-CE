package com.goodbird.cnpcefaddon.common;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import noppes.npcs.entity.EntityNPCInterface;

public class FactionDamageHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!AddonConfig.NPC_PLAYER_FACTION_PROTECTION.get()) return;
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof EntityNPCInterface npcAttacker)) return;
        if (npcAttacker.faction == null) return;
        if (!(event.getEntity() instanceof Player player)) return;

        if (npcAttacker.faction.isFriendlyToPlayer(player)) {
            event.setCanceled(true);
            return;
        }
        if (npcAttacker.faction.isNeutralToPlayer(player) && npcAttacker.getTarget() != player) {
            event.setCanceled(true);
        }
    }
}
