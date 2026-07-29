package com.goodbird.cnpcefaddon.common;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * Derives the aim / reload living motion for an NPC holding a projectile weapon.
 * <p>
 * Epic Fight decides {@code AIM} and {@code RELOAD} from {@code LivingEntity.isUsingItem()}
 * plus {@code CrossbowItem.isCharged()}. Custom NPCs never routes its ranged attack through
 * item usage: {@code EntityAIRangedAttack} calls {@code performRangedAttack} directly and
 * only swings the arm afterwards. Neither condition ever becomes true, so the bow and
 * crossbow poses never appear and the NPC keeps the plain idle/walk pose while shooting.
 * <p>
 * The animations themselves are already present on the animator, because
 * {@code RangedWeaponCapability.getLivingMotionModifier} registers AIM / SHOT / RELOAD for
 * the held bow or crossbow. Only the motion selection is missing, which is what this fills in.
 */
public final class RangedMotionResolver {

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
        if (patch.getTarget() == null || !patch.getTarget().isAlive()) {
            return null;
        }

        // An action animation (melee combo, stagger, dodge) must keep priority.
        if (patch.getEntityState().inaction()) {
            return null;
        }

        if (mainHand.getItem() instanceof CrossbowItem) {
            // Custom NPCs swings the arm right after firing; use that as the reload window.
            if (original.swinging && !CrossbowItem.isCharged(mainHand)) {
                return LivingMotions.RELOAD;
            }

            return LivingMotions.AIM;
        }

        return LivingMotions.AIM;
    }
}
