package top.bincnpcef.common;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * 阵营伤害保护（攻击者为 NPC 时按阵营取消伤害）。
 *
 * <p>两条规则：
 * <ul>
 *   <li>NPC → 玩家：友好阵营完全免疫；中立阵营仅在玩家是当前攻击目标时可伤（防仇恨残留）；</li>
 *   <li>NPC → NPC：攻击者阵营对受害者不敌对时取消（无敌对配置按中性放行）。</li>
 * </ul>
 *
 * <p>null 保底：任一方 faction 为 null 时放行，交给 CNPC 自身规则处理。
 */
public class FactionDamageHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingIncomingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof EntityNPCInterface npcAttacker)) return;
        if (npcAttacker.faction == null) return;

        Entity victim = event.getEntity();

        if (!AddonConfig.NPC_FACTION_FRIENDLY_FIRE.get() && !AddonConfig.NPC_PLAYER_FACTION_PROTECTION.get()) return;

        if (victim instanceof Player player) {
            if (!AddonConfig.NPC_PLAYER_FACTION_PROTECTION.get()) return;

            if (npcAttacker.faction.isFriendlyToPlayer(player)) {
                event.setCanceled(true);
                return;
            }
            if (npcAttacker.faction.isNeutralToPlayer(player) && npcAttacker.getTarget() != player) {
                event.setCanceled(true);
            }
            return;
        }

        if (victim instanceof EntityNPCInterface npcVictim) {
            if (!AddonConfig.NPC_FACTION_FRIENDLY_FIRE.get()) return;
            if (npcVictim.faction == null) return;

            if (!npcAttacker.faction.isAggressiveToNpc(npcVictim)) {
                event.setCanceled(true);
            }
        }
    }
}
