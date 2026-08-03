package com.goodbird.cnpcefaddon.common;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Derives the aim / reload / shot living motion for an NPC holding a projectile weapon.
 * <p>
 * Epic Fight decides {@code AIM} and {@code RELOAD} from {@code LivingEntity.isUsingItem()}
 * plus {@code CrossbowItem.isCharged()}. Custom NPCs never routes its ranged attack through
 * item usage: {@code EntityAIRangedAttack} calls {@code performRangedAttack} directly and
 * only swings the arm afterwards. Neither condition ever becomes true, so the bow and
 * crossbow poses never appear and the NPC keeps the plain idle/walk pose while shooting.
 * <p>
 * {@link NpcBowDrawFlow} drives a real use cycle so the two item conditions hold again;
 * this resolver then maps them to motions, and additionally maps the arm swing Custom NPCs
 * performs right after firing to {@code SHOT}, so the shot reads as a distinct fire pose
 * instead of the swing falling through to Epic Fight's default dig/swing animation.
 */
public final class RangedMotionResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(RangedMotionResolver.class);

    /** Last logged tick per entity, to avoid spamming the log every tick. */
    private static final Map<LivingEntity, Integer> LAST_LOG_TICK = new WeakHashMap<>();

    /** Previous use-state per entity (client side), to detect the shot edge. */
    private static final Map<LivingEntity, Boolean> PREV_USING = new WeakHashMap<>();

    /** Client tick at which the last shot edge was seen per entity. */
    private static final Map<LivingEntity, Integer> SHOT_TICK = new WeakHashMap<>();

    /** How many client ticks the shot pose is held after firing. */
    private static final int SHOT_HOLD_TICKS = 8;

    private RangedMotionResolver() {
    }

    /**
     * @return the motion to display, or {@code null} when the NPC is not drawing a projectile weapon
     */
    public static LivingMotion resolve(LivingEntityPatch<?> patch) {
        LivingEntity original = patch.getOriginal();

        if (original == null) {
            return null;
        }

        ItemStack mainHand = original.getItemInHand(InteractionHand.MAIN_HAND);

        if (!(mainHand.getItem() instanceof ProjectileWeaponItem)) {
            return null;
        }

        // Only pose while there is something to shoot at; otherwise the NPC should idle normally.
        // NOTE: patch.getTarget() is a server-side field - it reads null on the client, so it
        // cannot gate the motion here or the client would always fall through to IDLE/null and
        // every change below would appear to do nothing. The use state driven by
        // NpcBowDrawFlow only exists while a target is engaged, so it is a sufficient signal.
        if (patch.getTarget() != null && !patch.getTarget().isAlive()) {
            return null;
        }

        // An action animation (melee combo, stagger, dodge) must keep priority.
        if (patch.getEntityState().inaction()) {
            return null;
        }

        // Crossbow: Custom NPCs swings the arm right after firing; read it as the shot pose.
        if (mainHand.getItem() instanceof CrossbowItem) {
            if (original.swinging) {
                return logDecision(original, "crossbowSwing", LivingMotions.SHOT);
            }

            // Crossbow uses its OWN animations only. Epic Fight maps a charged crossbow to
            // AIM, and its AIM animation is the bow aim - which made a crossbow NPC look
            // like it was drawing a bow all the time. Charged means IDLE instead (the reload
            // animation plays during the charge, the shot animation on swinging).
            if (original.isUsingItem()) {
                return logDecision(original, "crossbowUsing", LivingMotions.RELOAD);
            }

            if (CrossbowItem.isCharged(mainHand)) {
                return logDecision(original, "crossbowCharged", LivingMotions.IDLE);
            }

            return logDecision(original, "crossbowIdle", null);
        }

        // Bow: the use cycle driven by NpcBowDrawFlow is draw -> fire (server stops using
        // and immediately starts again). Custom NPCs does NOT swing its arm for bows, so
        // the swinging check never fires on the client and the shot pose would never show.
        // The false->true edge of the use state marks the moment the arrow is released.
        boolean using = original.isUsingItem();
        Boolean prevUsing = PREV_USING.get(original);

        if (using && Boolean.FALSE.equals(prevUsing)) {
            SHOT_TICK.put(original, original.tickCount);
        }

        PREV_USING.put(original, using);

        Integer shotAt = SHOT_TICK.get(original);

        if (shotAt != null && original.tickCount - shotAt < SHOT_HOLD_TICKS) {
            return logDecision(original, "bowShotEdge", LivingMotions.SHOT);
        }

        return using ? logDecision(original, "bowUsing", LivingMotions.AIM) : null;
    }

    private static LivingMotion logDecision(LivingEntity original, String reason, LivingMotion motion) {
        int tick = original.tickCount;
        Integer last = LAST_LOG_TICK.get(original);

        if (last == null || tick - last >= 20) {
            LAST_LOG_TICK.put(original, tick);
            String target = (original instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null)
                    ? mob.getTarget().getName().getString()
                    : "none";
            LOGGER.info("[rangedmotion] entity={} tick={} held={} target={} using={} ticksUsing={} charged={} swinging={} -> {} (reason={})",
                    original.getName().getString(),
                    tick,
                    original.getMainHandItem().getItem().getDescriptionId(),
                    target,
                    original.isUsingItem(),
                    original.getTicksUsingItem(),
                    CrossbowItem.isCharged(original.getMainHandItem()),
                    original.swinging,
                    motion == null ? "IDLE/null" : motion.toString(),
                    reason);
        }

        return motion;
    }
}
