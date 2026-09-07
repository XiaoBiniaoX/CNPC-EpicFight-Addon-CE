package com.goodbird.cnpcefaddon.common.patch;

import yesman.epicfight.api.model.Armature;

public interface INpcPatch {
    Armature getArmature();

    /**
     * Signifies to the YSM Epic Fight Compat mod that an NPC with an Epic Fight patch is in
     * battle mode. YSMBattleMode queries this method reflectively for non-player entities;
     * without it the compat mod treats the NPC as idling and its molang script animations
     * override Epic Fight's combat animations (visible as detached/rotated decoration bones
     * such as tails and ears). An NPC that uses an Epic Fight model is always fighting.
     *
     * <p>Kept on the interface so every NPC patch flavour ({@code NpcHumanoidPatch},
     * {@code AdvNpcPatch}, {@code NpcPatch}, presets) answers true. Player patches are not
     * {@code INpcPatch} implementations and are unaffected.
     */
    default boolean isFightMode() {
        return true;
    }

    /**
     * Whether the native CustomNPCs melee goal must be suppressed. CE patches only suppress
     * it while their own CE attack goal is actually installed; this preserves the native
     * fallback when a CE datapack has no behavior for the held weapon or desire is zero.
     */
    default boolean suppressNativeAttack() {
        return true;
    }

    default void refreshCombatAI() {
    }
}
