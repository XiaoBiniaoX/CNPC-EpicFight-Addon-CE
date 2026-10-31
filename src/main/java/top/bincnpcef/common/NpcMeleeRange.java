package top.bincnpcef.common;

import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.property.AnimationProperty.ActionAnimationProperty;
import yesman.epicfight.api.animation.property.AnimationProperty.AttackAnimationProperty;
import yesman.epicfight.api.animation.property.MoveCoordFunctions;
import yesman.epicfight.api.animation.types.AirSlashAnimation;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.DashAttackAnimation;
import yesman.epicfight.api.animation.types.RangedAttackAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.world.capabilities.entitypatch.MobPatch;

/** CNPC range is an additional selection condition, not a damage or animation-playback limit. */
public final class NpcMeleeRange {
    private NpcMeleeRange() {}

    public static boolean canSelect(MobPatch<?> patch, AnimationAccessor<? extends StaticAnimation> motion) {
        if (patch == null || !(patch.getOriginal() instanceof EntityNPCInterface npc)) return true;
        if (!isOrdinaryMelee(motion.get())) return true;
        LivingEntity target = patch.getTarget();
        if (target == null || npc.stats == null || npc.stats.melee == null) return false;
        double distance = npc.distanceToSqr(target.getX(), target.getBoundingBox().minY, target.getZ());
        return distance <= rangeSqr(npc.stats.melee.getRange(), npc.getBbWidth(), target.getBbWidth());
    }

    static double rangeSqr(int range, float npcWidth, float targetWidth) {
        // Same formula and float arithmetic as CNPC EntityAIAttackTarget.tick, including the size floor.
        return Math.max((float) (range * range) + targetWidth,
                npcWidth * 2.0F * npcWidth * 2.0F + targetWidth);
    }

    static boolean isOrdinaryMelee(StaticAnimation animation) {
        if (!(animation instanceof AttackAnimation) || animation instanceof DashAttackAnimation
                || animation instanceof RangedAttackAnimation || animation instanceof AirSlashAnimation) return false;
        // Keep explicitly authored movement (e.g. battojutsu_dash) under its original JSON conditions.
        // This classifies default melee movement; it does not estimate root travel or collider reach.
        return animation.getProperty(ActionAnimationProperty.STOP_MOVEMENT).orElse(false)
                && !animation.getProperty(ActionAnimationProperty.MOVE_VERTICAL).orElse(false)
                && !animation.getProperty(AttackAnimationProperty.FIXED_MOVE_DISTANCE).orElse(false)
                && animation.getProperty(ActionAnimationProperty.COORD).isEmpty()
                && animation.getProperty(ActionAnimationProperty.COORD_SET_BEGIN).orElse(null)
                        == MoveCoordFunctions.TRACE_TARGET_DISTANCE
                && animation.getProperty(ActionAnimationProperty.COORD_SET_TICK).orElse(null)
                        == MoveCoordFunctions.TRACE_TARGET_DISTANCE
                && animation.getProperty(ActionAnimationProperty.COORD_GET).orElse(null) == MoveCoordFunctions.MODEL_COORD
                && animation.getProperty(ActionAnimationProperty.DEST_LOCATION_PROVIDER).orElse(null)
                        == MoveCoordFunctions.ATTACK_TARGET_LOCATION
                && animation.getProperty(ActionAnimationProperty.ENTITY_YROT_PROVIDER).orElse(null)
                        == MoveCoordFunctions.MOB_ATTACK_TARGET_LOOK;
    }
}
