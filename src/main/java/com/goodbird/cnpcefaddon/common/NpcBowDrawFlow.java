package com.goodbird.cnpcefaddon.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * Drives a real item-use cycle for NPCs holding a bow or crossbow, so Epic Fight's draw
 * and reload poses appear.
 * <p>
 * Custom NPCs shoots by calling {@code performRangedAttack} directly and swinging the arm
 * afterwards; it never enters the item usage state. Epic Fight picks {@code AIM} and
 * {@code RELOAD} from {@code LivingEntity.isUsingItem()} and {@code CrossbowItem.isCharged()},
 * so for an NPC neither condition ever becomes true.
 * <p>
 * The charge state is advanced by this class rather than by
 * {@code CrossbowItem.releaseUsing}. That vanilla path calls {@code tryLoadProjectiles},
 * which looks for an arrow in the entity's own inventory -- an NPC keeps its ammo in the
 * Custom NPCs projectile slot instead, so the load always fails, {@code isCharged} stays
 * false and the charge restarts every tick. That was the "keeps plucking the string and
 * never fires" behaviour. Writing the {@code Charged} tag directly once the charge
 * duration has elapsed keeps the animation flow intact without touching ammo.
 * <p>
 * Anything that is not a bow or crossbow is left entirely to Custom NPCs.
 */
public final class NpcBowDrawFlow {

    /** Vanilla skeletons draw for 20 ticks; matching that keeps the pose readable. */
    private static final int BOW_DRAW_TICKS = 20;

    private static final String TAG_CHARGED = "Charged";
    private static final String TAG_CHARGED_PROJECTILES = "ChargedProjectiles";

    private NpcBowDrawFlow() {
    }

    /** Whether this addon takes over the use cycle for the given stack. */
    public static boolean isRealFlowWeapon(ItemStack stack) {
        return stack.getItem() instanceof ProjectileWeaponItem;
    }

    /**
     * Advances the draw / charge state. Safe to call every tick of the ranged goal.
     */
    public static void tickDraw(EntityNPCInterface npc) {
        if (npc == null || npc.level().isClientSide()) {
            return;
        }

        ItemStack mainHand = npc.getMainHandItem();

        if (!isRealFlowWeapon(mainHand)) {
            // Not our business: make sure we are not holding the NPC in a use state.
            stopUsing(npc);
            return;
        }

        if (mainHand.getItem() instanceof CrossbowItem) {
            tickCrossbow(npc, mainHand);
        } else if (!npc.isUsingItem()) {
            npc.startUsingItem(InteractionHand.MAIN_HAND);
        }
    }

    private static void tickCrossbow(EntityNPCInterface npc, ItemStack crossbow) {
        if (CrossbowItem.isCharged(crossbow)) {
            // Charged: leave the use state so the AIM pose (driven by isCharged) shows.
            stopUsing(npc);
            return;
        }

        if (!npc.isUsingItem()) {
            npc.startUsingItem(InteractionHand.MAIN_HAND);
            return;
        }

        if (npc.getTicksUsingItem() >= CrossbowItem.getChargeDuration(crossbow)) {
            setCharged(crossbow, true);
            stopUsing(npc);
        }
    }

    /**
     * @return whether the draw / charge has progressed far enough to let the shot through
     */
    public static boolean readyToFire(EntityNPCInterface npc) {
        if (npc == null) {
            return true;
        }

        ItemStack mainHand = npc.getMainHandItem();

        if (!isRealFlowWeapon(mainHand)) {
            return true;
        }

        if (mainHand.getItem() instanceof CrossbowItem) {
            return CrossbowItem.isCharged(mainHand);
        }

        return npc.isUsingItem() && npc.getTicksUsingItem() >= BOW_DRAW_TICKS;
    }

    /**
     * Closes the cycle after Custom NPCs has launched the projectile.
     */
    public static void onFired(EntityNPCInterface npc) {
        if (npc == null) {
            return;
        }

        ItemStack mainHand = npc.getMainHandItem();

        if (!isRealFlowWeapon(mainHand)) {
            return;
        }

        if (mainHand.getItem() instanceof CrossbowItem) {
            setCharged(mainHand, false);
        }

        stopUsing(npc);
    }

    public static void reset(EntityNPCInterface npc) {
        if (npc == null) {
            return;
        }

        ItemStack mainHand = npc.getMainHandItem();

        if (mainHand.getItem() instanceof CrossbowItem) {
            setCharged(mainHand, false);
        }

        stopUsing(npc);
    }

    private static void stopUsing(EntityNPCInterface npc) {
        if (npc.isUsingItem()) {
            npc.stopUsingItem();
        }
    }

    /**
     * {@code CrossbowItem.setCharged} is private, so the backing tags are written directly.
     * These are the same fields the vanilla method touches.
     */
    private static void setCharged(ItemStack crossbow, boolean charged) {
        if (charged) {
            crossbow.getOrCreateTag().putBoolean(TAG_CHARGED, true);
            return;
        }

        CompoundTag tag = crossbow.getTag();

        if (tag != null) {
            tag.putBoolean(TAG_CHARGED, false);
            tag.remove(TAG_CHARGED_PROJECTILES);
        }
    }
}
