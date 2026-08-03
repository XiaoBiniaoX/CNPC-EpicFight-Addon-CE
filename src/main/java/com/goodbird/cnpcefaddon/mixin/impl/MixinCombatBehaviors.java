package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.api.IDataMeleeAttackDesire;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.world.capabilities.entitypatch.MobPatch;
import yesman.epicfight.world.entity.ai.goal.CombatBehaviors;

import java.util.List;

/**
 * Four fixes to Epic Fight's combat behaviour selector, all of which starve the candidate
 * pool or waste decision ticks and leave an NPC standing idle mid-fight.
 *
 * <h3>1. Abandoned series keep a mid-combo cursor</h3>
 * {@code tryProceed()} moves {@code currentBehaviorPointer} to a newly chosen series without
 * resetting the old one's {@code nextBehaviorPointer}. Since {@code canBeSelected()} tests
 * {@code behaviors.get(nextBehaviorPointer)} and mid-combo behaviors usually carry
 * dash-range {@code within_distance} conditions, the abandoned series silently drops out of
 * the pool once the target is in melee range.
 *
 * <h3>2. Cooldowns freeze while the entity is acting</h3>
 * {@code CombatBehaviors.tick()} returns early when {@code getEntityState().inaction()} is
 * true, so cooldowns only advance between animations. An NPC in continuous combat spends
 * most of its time inside animations, which stretches a nominal 20-tick cooldown into
 * several seconds of wall time and drains the pool.
 *
 * <h3>3. Weight normalisation can fall through</h3>
 * {@code getRandomCombatBehaviorSeries()} accumulates {@code weight / weightSum} into
 * {@code delta} and returns the first index where {@code random < delta}. Float rounding can
 * leave the final {@code delta} just under 1.0, so a {@code random} above it matches nothing
 * and the method returns -1 -- a wasted decision tick even though valid candidates existed.
 *
 * <h3>4. Finishing a series wastes a decision tick</h3>
 * When the active series runs out ({@code loopFinished && !looping}) or its next behavior
 * fails its predicates, {@code tryProceed()} clears the pointer and returns {@code null}.
 * {@code AdvancedCombatGoal} then does nothing for that tick and only picks a fresh series on
 * the following one. The same happens after an interrupt, where the goal marks the series
 * finished but leaves {@code currentBehaviorPointer} set, costing two ticks. Because
 * {@code state.canBasicAttack()} has usually just turned true when this happens, those idle
 * ticks land exactly at the moment the NPC should be chaining its next move -- the visible
 * "swings once then freezes" pause. Selecting a replacement in the same call removes the gap.
 */
@Mixin(value = CombatBehaviors.class, remap = false)
public abstract class MixinCombatBehaviors {

    @Shadow
    private List<CombatBehaviors.BehaviorSeries<?>> behaviorSeriesList;

    @Shadow
    private int currentBehaviorPointer;

    /** Guards the re-entry into {@code selectRandomBehaviorSeries} from {@code tryProceed}. */
    @Unique
    private boolean cnpcef$selectingReplacement;

    @Inject(method = "tryProceed", at = @At("RETURN"), cancellable = true)
    private void cnpcef$chainInsteadOfIdling(CallbackInfoReturnable<CombatBehaviors.Behavior<?>> cir) {
        this.cnpcef$rewindAll();

        if (cir.getReturnValue() != null || this.cnpcef$selectingReplacement) {
            return;
        }

        // The active series just ended or was rejected; pick the next move now instead of
        // burning this decision tick.
        this.cnpcef$selectingReplacement = true;

        try {
            CombatBehaviors.Behavior<?> replacement =
                    ((CombatBehaviors<?>) (Object) this).selectRandomBehaviorSeries();

            if (replacement != null) {
                cir.setReturnValue(replacement);
            }
        } catch (Throwable ignored) {
            // Leave the vanilla null result; the goal will retry next tick.
        } finally {
            this.cnpcef$selectingReplacement = false;
        }
    }

    @Inject(method = "selectRandomBehaviorSeries", at = @At("RETURN"))
    private void cnpcef$rewindAfterSelect(CallbackInfoReturnable<CombatBehaviors.Behavior<?>> cir) {
        this.cnpcef$rewindAll();
    }

    /**
     * Keeps cooldowns advancing during animations. Vanilla bails out before the loop when
     * the entity is in an action, which is most of an active fight. Also burns cooldowns
     * faster for aggressive NPCs, so a high melee desire makes the NPC re-use its combat
     * behaviors (special moves) more often.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void cnpcef$tickCooldownsDuringAction(CallbackInfo ci) {
        int boost = this.cnpcef$desireCooldownBoost();

        for (CombatBehaviors.BehaviorSeries<?> series : this.behaviorSeriesList) {
            if (boost > 0 && series instanceof IBehaviorSeriesPointer pointer) {
                int cooldown = pointer.cnpcef$getCooldown();

                if (cooldown > 0) {
                    pointer.cnpcef$setCooldown(Math.max(0, cooldown - boost));
                }
            }

            series.tick();
        }

        ci.cancel();
    }

    /**
     * How many extra cooldown ticks per game tick an aggressive NPC gets: desire 10 burns
     * four per tick (a nominal cooldown four times shorter), desire 5 or below keeps the
     * datapack's exact behaviour, and non-NPC mobs get no boost at all.
     */
    @Unique
    private int cnpcef$desireCooldownBoost() {
        try {
            MobPatch<?> patch = ((ICombatBehaviorsOwner) this).cnpcef$getMobPatch();

            if (patch.getOriginal() instanceof EntityNPCInterface npc) {
                IDataMeleeAttackDesire melee = (IDataMeleeAttackDesire) (Object) npc.stats.getMelee();
                float desire = melee.getAttackDesire();

                if (desire <= 5.0F) {
                    return 0;
                }

                return (int) Math.floor((desire - 5.0F) * 0.8F);
            }
        } catch (Throwable ignored) {
        }

        return 0;
    }

    /**
     * Replaces a -1 result caused by float rounding with the last valid candidate.
     * Genuine "nothing is selectable" cases still return -1, because the fallback only
     * triggers when the vanilla scan found candidates.
     */
    @Inject(method = "getRandomCombatBehaviorSeries", at = @At("RETURN"), cancellable = true)
    private void cnpcef$avoidRoundingFallthrough(CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValueI() >= 0) {
            return;
        }

        int fallback = -1;
        float bestWeight = -1.0F;

        for (int i = 0; i < this.behaviorSeriesList.size(); i++) {
            if (i == this.currentBehaviorPointer) {
                continue;
            }

            CombatBehaviors.BehaviorSeries<?> series = this.behaviorSeriesList.get(i);

            if (!(series instanceof IBehaviorSeriesPointer accessor)) {
                continue;
            }

            if (!this.cnpcef$canBeSelected(series)) {
                continue;
            }

            float weight = accessor.cnpcef$getWeight();

            if (weight > bestWeight) {
                bestWeight = weight;
                fallback = i;
            }
        }

        if (fallback >= 0) {
            this.cnpcef$resetCooldown(fallback);
            cir.setReturnValue(fallback);
        }
    }

    private void cnpcef$rewindAll() {
        for (int i = 0; i < this.behaviorSeriesList.size(); i++) {
            if (i == this.currentBehaviorPointer) {
                continue;
            }

            if (this.behaviorSeriesList.get(i) instanceof IBehaviorSeriesPointer pointer) {
                if (pointer.cnpcef$getNextBehaviorPointer() != 0) {
                    pointer.cnpcef$setNextBehaviorPointer(0);
                    pointer.cnpcef$setLoopFinished(false);
                }
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean cnpcef$canBeSelected(CombatBehaviors.BehaviorSeries<?> series) {
        try {
            MobPatch<?> patch = ((ICombatBehaviorsOwner) this).cnpcef$getMobPatch();
            return ((CombatBehaviors.BehaviorSeries) series).canBeSelected(patch);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void cnpcef$resetCooldown(int index) {
        try {
            ((CombatBehaviors) (Object) this).resetCooldown(index, true);
        } catch (Throwable ignored) {
        }
    }
}
