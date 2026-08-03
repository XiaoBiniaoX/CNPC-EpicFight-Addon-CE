package com.goodbird.cnpcefaddon.mixin.impl;

import com.nameless.indestructible.world.ai.goal.GuardGoal;
import com.nameless.indestructible.world.capability.Utils.IAdvancedCapability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.world.capabilities.entitypatch.MobPatch;

/**
 * Caps how long an NPC may stay inside a single block, so sustained pressure cannot freeze it.
 * <p>
 * {@code GuardGoal} only ends when {@code targetInactiontime > getBlockTick()}, and its own
 * tick resets that counter to zero on every tick the target is mid-attack
 * ({@code phase > 0 && phase < 3}) or drawing a projectile weapon. A player who keeps
 * swinging therefore renews the block indefinitely. Because
 * {@code AdvancedCombatGoal.tick()} treats {@code isBlocking()} as "in action" and returns
 * before selecting a move, the NPC stops attacking for exactly as long as that lasts -- the
 * "hit it once and it freezes for a moment" pause. Two NPCs fighting each other do the same
 * thing to one another.
 * <p>
 * A ceiling of {@value #MAX_BLOCK_TICKS} ticks is imposed on top of the datapack's own
 * {@code guard_time}. Guarding still works and still absorbs hits; it just cannot be extended
 * without limit. Datapacks asking for a shorter guard are unaffected, since the original
 * end condition still applies first.
 * <p>
 * On cap, the {@code blocking} flag is dropped and {@code interrupted} is raised. The latter
 * makes {@code AdvancedCombatGoal} reset its current behavior series on its next tick, so the
 * guard move Indestructible is still executing does not leave the combat goal stuck in the
 * "activated move" branch -- the NPC picks a fresh move instead. Interrupted is only ever
 * raised here while the NPC is actually blocking, so normal attack series are unaffected, and
 * {@code AdvancedCombatGoal} clears the flag itself after handling it.
 */
@Mixin(GuardGoal.class)
public abstract class MixinGuardGoal {

    /** 0.75 seconds: a deliberate block, without stalling the fight. */
    @Unique
    private static final int MAX_BLOCK_TICKS = 15;

    @Shadow(remap = false)
    private MobPatch<?> mobPatch;

    @Unique
    private int cnpcef$blockTicks;

    @Inject(method = "canContinueToUse", at = @At("RETURN"), cancellable = true)
    private void cnpcef$stopAfterCap(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && this.cnpcef$blockTicks >= MAX_BLOCK_TICKS) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "start", at = @At("HEAD"))
    private void cnpcef$resetCounter(CallbackInfo ci) {
        this.cnpcef$blockTicks = 0;
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void cnpcef$clearCounter(CallbackInfo ci) {
        this.cnpcef$blockTicks = 0;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void cnpcef$countAndRelease(CallbackInfo ci) {
        if (++this.cnpcef$blockTicks < MAX_BLOCK_TICKS) {
            return;
        }

        if (this.mobPatch instanceof IAdvancedCapability capability) {
            // Drop the flag now so AdvancedCombatGoal can act on this very tick; stop() would
            // otherwise only run after the goal selector re-evaluates.
            capability.setBlocking(false);
            // Force the combat goal to reset its series: its active guard move would otherwise
            // keep AdvancedCombatGoal in the "activated move" branch, which picks no new move.
            capability.setInterrupted(true);
        }
    }
}
