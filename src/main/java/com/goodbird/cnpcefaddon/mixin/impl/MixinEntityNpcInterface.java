package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.common.AddonConfig;
import com.goodbird.cnpcefaddon.common.NpcBowDrawFlow;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import noppes.npcs.ai.EntityAIRangedAttack;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

@Mixin(EntityNPCInterface.class)
public class MixinEntityNpcInterface extends PathfinderMob {

    protected MixinEntityNpcInterface(EntityType<? extends PathfinderMob> p_21683_, Level p_21684_) {
        super(p_21683_, p_21684_);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void cnpcef$keepCrossbowLoaded(CallbackInfo ci) {
        NpcBowDrawFlow.tickKeepLoaded((EntityNPCInterface) (Object) this);
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void cnpcef$preventFactionFriendlyFire(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!AddonConfig.NPC_FACTION_FRIENDLY_FIRE.get()) return;
        Entity attacker = source.getEntity();
        if (!(attacker instanceof EntityNPCInterface npcAttacker)) return;
        if (npcAttacker.faction == null) return;
        EntityNPCInterface self = (EntityNPCInterface)(Object)this;
        if (self.faction == null) return;
        if (!npcAttacker.faction.isAggressiveToNpc(self)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "addRegularEntries", at=@At("TAIL"), remap = false)
    public void addRegularEntries(CallbackInfo ci) {
        LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(this, LivingEntityPatch.class);
        if(patch instanceof HumanoidMobPatch){
            ((HumanoidMobPatch<?>) patch).setAIAsInfantry(this.getMainHandItem().getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem);
        }
    }

    /**
     * Restores ranged attacks for NPCs that hold a melee weapon but carry ammo.
     * <p>
     * Custom NPCs registers its ranged goal well after {@code addRegularEntries}, at a
     * priority far below the Epic Fight goals installed above. Both that goal and
     * {@code AdvancedChasingGoal} claim {@code Goal.Flag.MOVE}, and the chasing goal sits
     * at priority 1, so it wins the flag for as long as a target exists and the ranged goal
     * never gets a tick -- the NPC just walks up and melees while holding usable ammo.
     * <p>
     * Re-registering the ranged goal at priority 0 lets it claim the flag first. It yields
     * on its own once the target is inside the configured melee range
     * ({@code EntityAIRangedAttack.canUse} returns false there), so melee still takes over
     * up close. NPCs with no projectile keep behaving exactly as before, since the goal is
     * only created when the projectile slot is filled.
     */
    @Inject(method = "setResponse", at = @At("TAIL"), remap = false)
    private void cnpcef$prioritiseRangedGoal(CallbackInfo ci) {
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        EntityAIRangedAttack ranged = self.getRangedTask();

        if (ranged == null) {
            return;
        }

        this.goalSelector.removeGoal(ranged);
        this.goalSelector.addGoal(0, ranged);
    }
}
