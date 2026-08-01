package com.goodbird.cnpcefaddon.mixin.impl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import yesman.epicfight.world.capabilities.entitypatch.MobPatch;
import yesman.epicfight.world.entity.ai.goal.CombatBehaviors;

/**
 * Exposes the owning mob patch of a {@code CombatBehaviors} instance so
 * {@link MixinCombatBehaviors} can re-evaluate {@code canBeSelected} for its fallback pick.
 */
@Mixin(value = CombatBehaviors.class, remap = false)
public interface ICombatBehaviorsOwner {

    @Accessor("mobpatch")
    MobPatch<?> cnpcef$getMobPatch();
}
