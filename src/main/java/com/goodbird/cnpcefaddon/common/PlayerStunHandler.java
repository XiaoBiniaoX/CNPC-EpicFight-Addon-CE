package com.goodbird.cnpcefaddon.common;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import noppes.npcs.entity.EntityNPCInterface;
import yesman.epicfight.api.forgeevent.EntityStunEvent;
import yesman.epicfight.world.capabilities.entitypatch.HurtableEntityPatch;
import yesman.epicfight.world.damagesource.StunType;

/**
 * Applies the configurable armour-vs-stun rules from {@link AddonConfig} to NPC attacks
 * against players.
 * <p>
 * Epic Fight's {@code EntityEvents.hurtEvent} fires a cancellable {@link EntityStunEvent}
 * and then, in the same synchronous call, computes the duration and invokes
 * {@code applyStun(stunType, stunTime)}. That gives two clean hooks:
 * <ul>
 *   <li>cancelling the event at or above the armour threshold yields full stun immunity;</li>
 *   <li>a scale factor staged here is consumed by {@code MixinLivingEntityPatchStun}, which
 *       multiplies the {@code stunTime} argument. Positive benefit shortens the stun as
 *       armour grows, negative benefit lengthens it.</li>
 * </ul>
 * Only NPC attackers are affected; vanilla mobs and PvP keep Epic Fight's defaults.
 * <p>
 * The staged value lives across exactly one event → applyStun pair on the server thread,
 * and is cleared both when consumed and when the next stun event starts.
 */
public class PlayerStunHandler {

    /** How far the duration may be pushed in either direction, as a fraction. */
    private static final float MAX_SCALE_SHIFT = 0.75F;

    private static Object pendingTarget;
    private static float pendingScale = 1.0F;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityStun(EntityStunEvent event) {
        clearPending();

        double threshold = AddonConfig.PLAYER_STUN_IMMUNE_ARMOR.get();

        if (threshold <= 0.0D || event.getStunType() == StunType.NONE) {
            return;
        }

        HurtableEntityPatch<?> stunned = event.getStunnedEntityPatch();

        if (stunned == null || !(stunned.getOriginal() instanceof Player player)) {
            return;
        }

        if (!isNpcAttack(event)) {
            return;
        }

        double armor = player.getAttributeValue(Attributes.ARMOR);

        if (armor >= threshold) {
            event.setCanceled(true);
            return;
        }

        float ratio = (float) Math.min(1.0D, Math.max(0.0D, armor / threshold));
        boolean positive = AddonConfig.ARMOR_STUN_BENEFIT_POSITIVE.get();

        pendingTarget = stunned;
        pendingScale = positive
                ? 1.0F - MAX_SCALE_SHIFT * ratio
                : 1.0F + MAX_SCALE_SHIFT * ratio;
    }

    private static boolean isNpcAttack(EntityStunEvent event) {
        if (event.getDamageSource() == null) {
            return false;
        }

        Entity attacker = event.getDamageSource().getEntity();

        if (attacker == null) {
            attacker = event.getDamageSource().getDirectEntity();
        }

        return attacker instanceof EntityNPCInterface;
    }

    /**
     * @return the duration multiplier staged for this patch, or 1.0 when none applies
     */
    public static float consumeStunScale(Object patch) {
        if (patch != pendingTarget) {
            return 1.0F;
        }

        float scale = pendingScale;
        clearPending();

        return scale;
    }

    private static void clearPending() {
        pendingTarget = null;
        pendingScale = 1.0F;
    }
}
