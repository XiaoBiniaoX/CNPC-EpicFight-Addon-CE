package top.bincnpcef.common;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.ChargedProjectiles;
import noppes.npcs.api.wrapper.ItemStackWrapper;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * Drives a real item-use cycle for NPCs holding a bow or crossbow, so Epic Fight's draw
 * and reload poses appear.
 *
 * <p>Custom NPCs shoots by calling {@code performRangedAttack} directly and swinging the arm
 * afterwards; it never enters the item usage state. Epic Fight picks {@code AIM} and
 * {@code RELOAD} from {@code LivingEntity.isUsingItem()} and {@code CrossbowItem.isCharged()}
 * (see {@code CustomHumanoidMobPatch.updateMotion}), so for an NPC neither condition ever
 * becomes true and the poses never show.
 *
 * <p>The charge state is advanced by this class rather than by
 * {@code CrossbowItem.releaseUsing}. That vanilla path calls {@code tryLoadProjectiles},
 * which looks for an arrow in the entity's own inventory -- an NPC keeps its ammo in the
 * Custom NPCs projectile slot instead, so the load always fails, {@code isCharged} stays
 * false and the charge restarts every tick. That was the "keeps plucking the string and
 * never fires" behaviour. Writing the {@code Charged} tag directly once the charge
 * duration has elapsed keeps the animation flow intact without touching ammo.
 *
 * <p>Anything that is not a bow or crossbow is left entirely to Custom NPCs.
 */
public final class NpcBowDrawFlow {

    /** Fallback draw time when the NPC has no ranged stats to read. */
    private static final int BOW_DRAW_TICKS = 20;

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

        if (npc.getTicksUsingItem() >= drawTicks(npc, crossbow)) {
            setCharged(npc, crossbow, true);
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

        return npc.isUsingItem() && npc.getTicksUsingItem() >= drawTicks(npc);
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
            setCharged(npc, mainHand, false);
        }

        stopUsing(npc);
    }

    public static void reset(EntityNPCInterface npc) {
        if (npc == null) {
            return;
        }

        ItemStack mainHand = npc.getMainHandItem();

        if (mainHand.getItem() instanceof CrossbowItem) {
            setCharged(npc, mainHand, false);
        }

        stopUsing(npc);
    }

    private static void stopUsing(EntityNPCInterface npc) {
        if (npc.isUsingItem()) {
            npc.stopUsingItem();
        }
    }

    /**
     * How many ticks the bow must be drawn before the shot is allowed. Follows the NPC's
     * own ranged delay ({@code stats.ranged.getDelayMin()}), so raising or lowering that
     * delay slows down or speeds up the animation, always completing within the allowed
     * window. Falls back to a vanilla skeleton-like 20 ticks when no stats are readable.
     */
    private static int drawTicks(EntityNPCInterface npc) {
        if (npc != null && npc.stats != null && npc.stats.ranged != null && npc.stats.ranged.getDelayMin() > 0) {
            return npc.stats.ranged.getDelayMin();
        }

        return BOW_DRAW_TICKS;
    }

    /**
     * Same as {@link #drawTicks} for the crossbow variant: the ranged delay wins over the
     * vanilla item charge time whenever it is readable.
     */
    private static int drawTicks(EntityNPCInterface npc, ItemStack crossbow) {
        if (npc != null && npc.stats != null && npc.stats.ranged != null && npc.stats.ranged.getDelayMin() > 0) {
            return npc.stats.ranged.getDelayMin();
        }

        return CrossbowItem.getChargeDuration(crossbow, npc);
    }

    /**
     * {@code CrossbowItem.setCharged} is private, so the backing component is written
     * directly. These are the same fields the vanilla method touches: a non-empty
     * {@code ChargedProjectiles} component makes {@code isCharged} return true.
     *
     * <p>The mock projectile must be a real, non-empty stack. The state is synced to
     * clients through the {@code ChargedProjectiles} stream codec, which encodes each
     * entry with the strict {@code ItemStack} codec and crashes on an empty one. The
     * entity's own ammunition is used when available, otherwise a plain arrow.
     */
    private static void setCharged(EntityNPCInterface npc, ItemStack crossbow, boolean charged) {
        if (charged) {
            crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(resolveProjectile(npc)));
            return;
        }

        crossbow.remove(DataComponents.CHARGED_PROJECTILES);
    }

    private static ItemStack resolveProjectile(EntityNPCInterface npc) {
        if (npc != null) {
            ItemStack projectile = ItemStackWrapper.MCItem(npc.inventory.getProjectile());
            if (!projectile.isEmpty()) {
                return projectile;
            }
        }

        return new ItemStack(Items.ARROW);
    }
}