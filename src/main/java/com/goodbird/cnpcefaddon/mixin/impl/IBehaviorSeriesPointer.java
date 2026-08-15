package com.goodbird.cnpcefaddon.mixin.impl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import yesman.epicfight.world.entity.ai.goal.CombatBehaviors;

/**
 * Accessors for the private fields of {@code CombatBehaviors$BehaviorSeries}, used by
 * {@link MixinCombatBehaviors} to rewind series abandoned mid-combo and to pick a weighted
 * fallback when float rounding makes the vanilla scan fall through.
 */
@Mixin(value = CombatBehaviors.BehaviorSeries.class, remap = false)
public interface IBehaviorSeriesPointer {

    @Accessor("nextBehaviorPointer")
    int cnpcef$getNextBehaviorPointer();

    @Accessor("nextBehaviorPointer")
    void cnpcef$setNextBehaviorPointer(int pointer);

    @Accessor("loopFinished")
    void cnpcef$setLoopFinished(boolean loopFinished);

    @Accessor("weight")
    float cnpcef$getWeight();

    @Accessor("cooldown")
    int cnpcef$getCooldown();

    @Accessor("cooldown")
    void cnpcef$setCooldown(int cooldown);
}
