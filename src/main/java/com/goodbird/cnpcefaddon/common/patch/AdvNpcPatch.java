package com.goodbird.cnpcefaddon.common.patch;

import com.goodbird.cnpcefaddon.common.RangedMotionResolver;
import com.goodbird.cnpcefaddon.common.provider.AdvNpcPatchProvider;
import com.nameless.indestructible.world.capability.AdvancedCustomHumanoidMobPatch;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.PathfinderMob;
import noppes.npcs.entity.EntityNPCInterface;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.main.EpicFightSharedConstants;
import yesman.epicfight.world.capabilities.entitypatch.Faction;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.Style;
import yesman.epicfight.world.entity.ai.goal.CombatBehaviors;

import java.util.Map;

public class AdvNpcPatch<T extends PathfinderMob> extends AdvancedCustomHumanoidMobPatch<T> implements INpcPatch {
    AdvNpcPatchProvider provider;
    private boolean entityDataDefined;

    public AdvNpcPatch(Faction faction, AdvNpcPatchProvider provider) {
        super(faction, provider);
        this.provider = provider;
    }

    public void onConstructed(T entityIn) {
        this.original = entityIn;
        this.armature = provider.armature.deepCopy();
        this.animator = EpicFightSharedConstants.getAnimator(this);
        this.initAnimator(animator);
        animator.postInit();
        if (!this.entityDataDefined) {
            try {
                this.capabilityState.entityConstructed();
                this.entityDataDefined = true;
            } catch (IllegalArgumentException e) {
                if (e.getMessage() == null || !e.getMessage().contains("Duplicate id")) {
                    throw e;
                }
                this.entityDataDefined = true;
            }
        }
    }

    /**
     * Resolves the combat behaviour set for the item currently in the main hand.
     * <p>
     * Style fallback stays inside the matched weapon category. Epic Fight's
     * {@code styleProvider} hands non-player entities {@code ONE_HAND} or
     * {@code TWO_HAND} and never {@code COMMON}, so a datapack that declares only
     * {@code common} would otherwise miss; walking the styles of the same category
     * covers that. Crossing into a different category is never done -- it used to hand
     * the NPC an unrelated moveset (fist punches while holding a sword).
     */
    @Override
    protected CombatBehaviors.Builder<HumanoidMobPatch<?>> getHoldingItemWeaponMotionBuilder() {
        if (this.weaponAttackMotions == null || this.weaponAttackMotions.isEmpty()) {
            return super.getHoldingItemWeaponMotionBuilder();
        }

        CapabilityItem itemCap = this.getHoldingItemCapability(InteractionHand.MAIN_HAND);
        boolean armed = itemCap != null && !itemCap.isEmpty();

        Map<Style, CombatBehaviors.Builder<HumanoidMobPatch<?>>> byStyle = armed
                ? this.weaponAttackMotions.get(itemCap.getWeaponCategory())
                : this.weaponAttackMotions.get(CapabilityItem.WeaponCategories.FIST);

        if (byStyle == null || byStyle.isEmpty()) {
            if (this.weaponAttackMotions.size() == 1) {
                // Legacy single-category datapack: that one entry is the catch-all.
                byStyle = this.weaponAttackMotions.values().iterator().next();
            } else {
                return super.getHoldingItemWeaponMotionBuilder();
            }
        }

        if (armed) {
            Style style = itemCap.getStyle(this);
            CombatBehaviors.Builder<HumanoidMobPatch<?>> exact = byStyle.get(style);

            if (exact != null) {
                return exact;
            }
        }

        for (Style fallback : new Style[]{CapabilityItem.Styles.COMMON,
                                          CapabilityItem.Styles.TWO_HAND,
                                          CapabilityItem.Styles.ONE_HAND}) {
            CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = byStyle.get(fallback);

            if (builder != null) {
                return builder;
            }
        }

        return byStyle.values().iterator().next();
    }

    public OpenMatrix4f getModelMatrix(float partialTicks) {
        EntityNPCInterface npc = (EntityNPCInterface) original;
        float scale = (npc.display != null) ? npc.display.getSize() / 5f : 1f;
        return super.getModelMatrix(partialTicks).scale(scale, scale, scale);
    }

    /**
     * Adds bow / crossbow aim and reload poses on top of the inherited motion logic.
     * Custom NPCs fires without going through item usage, so Epic Fight's own
     * {@code isUsingItem()} checks never trigger for an NPC. See {@link RangedMotionResolver}.
     */
    @Override
    public void updateMotion(boolean considerInaction) {
        super.updateMotion(considerInaction);

        LivingMotion ranged = RangedMotionResolver.resolve(this);

        if (ranged != null) {
            this.currentCompositeMotion = ranged;
        }
    }
}
