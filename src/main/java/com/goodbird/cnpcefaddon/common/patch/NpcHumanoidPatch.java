package com.goodbird.cnpcefaddon.common.patch;

import com.goodbird.cnpcefaddon.common.NpcBowDrawFlow;
import com.goodbird.cnpcefaddon.common.RangedMotionResolver;
import com.goodbird.cnpcefaddon.common.provider.NpcHumanoidPatchProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import noppes.npcs.entity.EntityNPCInterface;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.main.EpicFightMod;
import yesman.epicfight.main.EpicFightSharedConstants;
import yesman.epicfight.model.armature.HumanoidArmature;
import yesman.epicfight.world.capabilities.entitypatch.CustomHumanoidMobPatch;
import yesman.epicfight.world.capabilities.entitypatch.Faction;
import yesman.epicfight.world.capabilities.item.CapabilityItem;

public class NpcHumanoidPatch<T extends PathfinderMob> extends CustomHumanoidMobPatch<T> implements INpcPatch {
    NpcHumanoidPatchProvider provider;
    public NpcHumanoidPatch(Faction faction, NpcHumanoidPatchProvider provider) {
        super(faction, provider);
        this.provider = provider;
    }

    public void onConstructed(T entityIn) {
        this.original = entityIn;
        this.armature = provider.armature.deepCopy();
        this.animator = EpicFightSharedConstants.getAnimator(this);
        this.initAnimator(animator);
        animator.postInit();
    }

    /**
     * Clears a bow draw or crossbow charge left over from a previous session; see
     * {@link com.goodbird.cnpcefaddon.common.NpcBowDrawFlow}.
     */
    @Override
    public void onJoinWorld(T entityIn, EntityJoinLevelEvent event) {
        super.onJoinWorld(entityIn, event);

        if (entityIn instanceof EntityNPCInterface npc) {
            NpcBowDrawFlow.reset(npc);
        }
    }

    public void applyWeaponLivingMotions() {
        if (this.original == null) return;

        CapabilityItem mainhandCap = this.getHoldingItemCapability(InteractionHand.MAIN_HAND);
        CapabilityItem offhandCap = this.getAdvancedHoldingItemCapability(InteractionHand.OFF_HAND);

        mainhandCap.getLivingMotionModifier(this, InteractionHand.MAIN_HAND)
            .forEach((motion, anim) -> this.getAnimator().addLivingAnimation(motion, anim));
        offhandCap.getLivingMotionModifier(this, InteractionHand.OFF_HAND)
            .forEach((motion, anim) -> this.getAnimator().addLivingAnimation(motion, anim));

        if (this.weaponLivingMotions != null && this.weaponLivingMotions.containsKey(mainhandCap.getWeaponCategory())) {
            var byStyle = this.weaponLivingMotions.get(mainhandCap.getWeaponCategory());
            var style = mainhandCap.getStyle(this);
            if (byStyle.containsKey(style) || byStyle.containsKey(CapabilityItem.Styles.COMMON)) {
                var animModifierSet = byStyle.getOrDefault(style, byStyle.get(CapabilityItem.Styles.COMMON));
                for (var pair : animModifierSet) {
                    this.getAnimator().addLivingAnimation(pair.getFirst(), pair.getSecond());
                }
            }
        }
    }

    public OpenMatrix4f getModelMatrix(float partialTicks) {
        EntityNPCInterface npc = (EntityNPCInterface) original;
        float scale = (npc.display != null) ? npc.display.getSize() / 5f : 1f;
        return super.getModelMatrix(partialTicks).scale(scale, scale, scale);
    }

    /**
     * Adds bow / crossbow aim and reload poses. Custom NPCs fires without going through
     * item usage, so Epic Fight's own {@code isUsingItem()} checks never trigger for an NPC.
     * See {@link RangedMotionResolver}.
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
