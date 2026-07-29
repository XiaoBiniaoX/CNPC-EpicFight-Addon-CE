package com.goodbird.cnpcefaddon.mixin.impl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.world.capabilities.entitypatch.MobPatch;
import yesman.epicfight.world.entity.ai.goal.CombatBehaviors;

import java.util.List;

/**
 * Fixes a behaviour-series leak in {@code CombatBehaviors.tryProceed()}.
 * <p>
 * When an interruptible series is abandoned mid-combo, Epic Fight moves
 * {@code currentBehaviorPointer} to the newly chosen series but never resets the old
 * series' {@code nextBehaviorPointer}:
 * <pre>
 * if (currentBehaviorSeries.canBeInterrupted) {
 *     int seriesPointer = this.getRandomCombatBehaviorSeries();
 *     if (seriesPointer >= 0 &amp;&amp; this.currentBehaviorPointer != seriesPointer) {
 *         this.currentBehaviorPointer = seriesPointer;      // old series left dangling
 *         return newCombatBehaviorSeries.behaviors.get(...);
 *     }
 * }
 * </pre>
 * The abandoned series stays parked on a mid-combo behavior forever. Since
 * {@code canBeSelected()} tests {@code behaviors.get(nextBehaviorPointer)} rather than
 * {@code behaviors.get(0)}, and mid-combo behaviors typically carry dash-range
 * {@code within_distance} conditions, the series silently drops out of the candidate pool
 * once the target is in melee range. Combo and skill series die off one by one until only
 * single-behavior series remain selectable, which is why an NPC degenerates into spamming
 * one evade.
 * <p>
 * Resetting every non-current series back to its entry behavior after each decision keeps
 * the pool intact. Series are re-entered from the start rather than resumed mid-combo,
 * which matches how {@code selectRandomBehaviorSeries()} already treats them.
 */
@Mixin(value = CombatBehaviors.class, remap = false)
public abstract class MixinCombatBehaviors {

    @Shadow
    private List<CombatBehaviors.BehaviorSeries<?>> behaviorSeriesList;

    @Shadow
    private int currentBehaviorPointer;

    @Inject(method = "tryProceed", at = @At("RETURN"))
    private void cnpcef$rewindAbandonedSeries(CallbackInfoReturnable<CombatBehaviors.Behavior<?>> cir) {
        for (int i = 0; i < this.behaviorSeriesList.size(); i++) {
            if (i == this.currentBehaviorPointer) {
                continue;
            }

            CombatBehaviors.BehaviorSeries<?> series = this.behaviorSeriesList.get(i);

            if (series instanceof IBehaviorSeriesPointer pointer) {
                if (pointer.cnpcef$getNextBehaviorPointer() != 0) {
                    pointer.cnpcef$setNextBehaviorPointer(0);
                    pointer.cnpcef$setLoopFinished(false);
                }
            }
        }
    }
}
