package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.common.patch.INpcPatch;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.entitypatch.MobPatch;
import yesman.epicfight.world.damagesource.EpicFightDamageSource;

/**
 * Suppresses the native Custom NPCs melee hit for NPCs that run Epic Fight combat.
 * <p>
 * Both goal sets live in the same {@code goalSelector}: this addon installs Epic Fight's
 * {@code AnimatedAttackGoal} from {@code addRegularEntries}, while Custom NPCs registers its
 * own {@code EntityAIAttackTarget} later in {@code setResponse}. Epic Fight's
 * {@code MobPatch.selectGoalToRemove} only strips vanilla {@code MeleeAttackGoal} /
 * {@code AnimatedAttackGoal} / {@code RangedAttackGoal} / {@code TargetChasingGoal}, so the
 * Custom NPCs goal survives and keeps its own attack timer. Whenever the Epic Fight goal is
 * momentarily unable to run (cooldown, distance, no behaviour matched), the native goal wins
 * the tick and calls {@code doHurtTarget} directly - a hit with no wind-up and no animation
 * that still carries Epic Fight stun, because {@link MixinLivingEntityHurt} converts every
 * mob damage source into an {@code EpicFightDamageSource}.
 * <p>
 * Epic Fight's own attacks also route through {@code doHurtTarget}, but only from
 * {@code MobPatch.attack}, which sets {@code epicFightDamageSource} on the patch immediately
 * before the call and clears it right after. That flag is what separates the two paths, so
 * this mixin cancels only when it is absent.
 * <p>
 * NPCs without an Epic Fight model keep the native attack untouched: the check requires the
 * patch to be one of this addon's {@link INpcPatch} implementations, which are only attached
 * when {@code DataDisplay} selects an EF model. A {@code GlobalMobPatch} (from the
 * {@code GLOBAL_STUN} game rule) is not an {@code INpcPatch} and therefore never matches.
 * <p>
 * Ranged attacks are untouched: they run through {@code performRangedAttack}, not this method.
 */
@Mixin(EntityNPCInterface.class)
public abstract class MixinEntityNpcNativeAttack {

    @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
    private void cnpcef$blockNativeAttackForEfNpc(Entity target, CallbackInfoReturnable<Boolean> cir) {
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;

        LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(self, LivingEntityPatch.class);

        if (!(patch instanceof INpcPatch)) {
            // No Epic Fight model on this NPC: native Custom NPCs behaviour stays as-is.
            return;
        }

        if (patch instanceof MobPatch<?> mobPatch) {
            EpicFightDamageSource efSource = mobPatch.getEpicFightDamageSource();

            if (efSource != null) {
                // This call comes from MobPatch.attack, i.e. an actual Epic Fight attack animation.
                return;
            }
        }

        cir.setReturnValue(false);
    }
}
