package com.goodbird.cnpcefaddon.mixin.impl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import yesman.epicfight.world.entity.ai.goal.CombatBehaviors;

/**
 * Accessors for the private combo cursor of {@code CombatBehaviors$BehaviorSeries},
 * used by {@link MixinCombatBehaviors} to rewind series abandoned mid-combo.
 */
@Mixin(value = CombatBehaviors.BehaviorSeries.class, remap = false)
public interface IBehaviorSeriesPointer {

    @Accessor("nextBehaviorPointer")
    int cnpcef$getNextBehaviorPointer();

    @Accessor("nextBehaviorPointer")
    void cnpcef$setNextBehaviorPointer(int pointer);

    @Accessor("loopFinished")
    void cnpcef$setLoopFinished(boolean loopFinished);
}
