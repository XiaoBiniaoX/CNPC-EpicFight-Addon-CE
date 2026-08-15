package com.goodbird.cnpcefaddon.common;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import noppes.npcs.entity.EntityNPCInterface;
import yesman.epicfight.api.forgeevent.EntityStunEvent;
import yesman.epicfight.world.capabilities.entitypatch.HurtableEntityPatch;
import yesman.epicfight.world.damagesource.StunType;
import yesman.epicfight.world.entity.ai.attribute.EpicFightAttributes;

/**
 * Adjusts the stun a target receives from an NPC attack, driven by two factors:
 * <ul>
 *   <li>the attacker's {@code EpicFightAttributes.IMPACT}: high impact keeps the original
 *       stun, low impact shortens it. Fixed-length stun types (LONG, KNOCKDOWN, NEUTRALIZE,
 *       FALL) whose duration is baked into the animation are downgraded to a weaker type
 *       instead of being shortened in place;</li>
 *   <li>the configurable armour-vs-stun rules from {@link AddonConfig} (players only).</li>
 * </ul>
 * Epic Fight's {@code EntityEvents.hurtEvent} fires a cancellable {@link EntityStunEvent}
 * and then, in the same synchronous call, computes the duration and invokes
 * {@code applyStun(stunType, stunTime)}. That gives two clean hooks:
 * <ul>
 *   <li>cancelling the event at or above the armour threshold yields full stun immunity;</li>
 *   <li>a scale factor staged here is consumed by {@code MixinLivingEntityPatchStun}, which
 *       multiplies the {@code stunTime} argument and downgrades the {@code stunType}.
 *       Positive benefit shortens the stun as armour grows, negative benefit lengthens it.</li>
 * </ul>
 * Only NPC attackers are affected; vanilla mobs and PvP keep Epic Fight's defaults.
 * <p>
 * The staged value lives across exactly one event → applyStun pair on the server thread,
 * and is cleared both when consumed and when the next stun event starts.
 */
public class PlayerStunHandler {

    /** How far the duration may be pushed in either direction, as a fraction. */
    private static final float MAX_SCALE_SHIFT = 0.75F;

    /** Impact value that maps to a neutral factor of 1.0. */
    private static final float BASE_IMPACT = 1.0F;

    /** Clamp for the impact factor, applied before it is used. */
    private static final float MIN_IMPACT_FACTOR = 0.25F;
    private static final float MAX_IMPACT_FACTOR = 2.0F;

    /** Impact factors below this downgrade fixed-length stun types. */
    private static final float DOWNGRADE_THRESHOLD = 0.6F;
    private static final float HARD_DOWNGRADE_THRESHOLD = 0.35F;

    private static Object pendingTarget;
    private static float pendingScale = 1.0F;
    private static float pendingImpactFactor = 1.0F;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityStun(EntityStunEvent event) {
        clearPending();

        if (event.getStunType() == StunType.NONE || !isNpcAttack(event)) {
            return;
        }

        HurtableEntityPatch<?> stunned = event.getStunnedEntityPatch();

        if (stunned == null) {
            return;
        }

        pendingTarget = stunned;
        pendingImpactFactor = impactFactorOf(getAttacker(event));

        double threshold = AddonConfig.PLAYER_STUN_IMMUNE_ARMOR.get();

        if (threshold <= 0.0D) {
            return;
        }

        if (!(stunned.getOriginal() instanceof Player player)) {
            return;
        }

        double armor = player.getAttributeValue(Attributes.ARMOR);

        if (armor >= threshold) {
            event.setCanceled(true);
            return;
        }

        float ratio = (float) Math.min(1.0D, Math.max(0.0D, armor / threshold));
        boolean positive = AddonConfig.ARMOR_STUN_BENEFIT_POSITIVE.get();

        pendingScale = positive
                ? 1.0F - MAX_SCALE_SHIFT * ratio
                : 1.0F + MAX_SCALE_SHIFT * ratio;
    }

    private static float impactFactorOf(Entity attacker) {
        if (attacker instanceof Mob mob
                && mob.getAttributes().hasAttribute(EpicFightAttributes.IMPACT.get())) {
            float impact = (float) mob.getAttributeValue(EpicFightAttributes.IMPACT.get());
            return Math.max(MIN_IMPACT_FACTOR, Math.min(MAX_IMPACT_FACTOR, impact / BASE_IMPACT));
        }

        return 1.0F;
    }

    private static Entity getAttacker(EntityStunEvent event) {
        Entity attacker = event.getDamageSource() == null ? null : event.getDamageSource().getEntity();

        if (attacker == null && event.getDamageSource() != null) {
            attacker = event.getDamageSource().getDirectEntity();
        }

        return attacker;
    }

    private static boolean isNpcAttack(EntityStunEvent event) {
        return getAttacker(event) instanceof EntityNPCInterface;
    }

    /**
     * @return the downgraded stun type for a weak-impact hit, or the original type.
     *         Reads the staged factor without consuming it, so it may be called
     *         independently of {@link #consumeStunScale(Object)}.
     */
    public static StunType applyImpactDowngrade(Object patch, StunType stunType) {
        if (stunType == StunType.NONE || patch != pendingTarget || pendingImpactFactor >= DOWNGRADE_THRESHOLD) {
            return stunType;
        }

        boolean weak = pendingImpactFactor < HARD_DOWNGRADE_THRESHOLD;

        return switch (stunType) {
            case KNOCKDOWN -> weak ? StunType.SHORT : StunType.LONG;
            case LONG, NEUTRALIZE, FALL -> StunType.SHORT;
            default -> stunType;
        };
    }

    /**
     * @return the duration multiplier staged for this patch, or 1.0 when none applies
     */
    public static float consumeStunScale(Object patch) {
        if (patch != pendingTarget) {
            return 1.0F;
        }

        float scale = Math.max(0.25F, pendingScale * pendingImpactFactor);
        clearPending();

        return scale;
    }

    private static void clearPending() {
        pendingTarget = null;
        pendingScale = 1.0F;
        pendingImpactFactor = 1.0F;
    }
}
