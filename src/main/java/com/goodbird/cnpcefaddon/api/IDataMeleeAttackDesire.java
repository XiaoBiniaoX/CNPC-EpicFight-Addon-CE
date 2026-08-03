package com.goodbird.cnpcefaddon.api;

/**
 * Bridge between the addon's {@code MixinDataMelee} and the classes that consume the
 * per-NPC melee aggression value (the melee properties GUI and the attack goal). The
 * interface is implemented by the mixin on {@code DataMelee}; callers cast to it.
 * <p>
 * Lives outside the mixin package on purpose: the mixin package is owned by the mixin
 * config, and classes there cannot be referenced directly from normal code.
 */
public interface IDataMeleeAttackDesire {

    /**
     * Aggression level in {@code [0, 10]}; {@code 5} is the default. The melee attack
     * interval is scaled by {@code (5 / desire)^3}, so desire 10 strikes about eight
     * times as often, {@code 0} disables voluntary melee attacks entirely.
     */
    float getAttackDesire();

    void setAttackDesire(float attackDesire);
}