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

    /**
     * 本 patch 实例背后的数据包 provider，用于判断「能否复用现有 patch」。
     *
     * <p>只比 patch 类不够：同一个 patch 类可对应任意多份数据包 provider（CE 的 5 份测试包
     * 全是 {@code CeNpcPatch}，普通/高级口味同样是一类多配置）。切换 efModel 后若只比类，
     * 复用判据会放过旧 patch，导致服务端仍跑旧配置而客户端已重建 → 双端不一致。
     *
     * <p>返回 {@code null} 表示该口味无法提供身份，调用方按「不可复用」处理（保守重建）。
     * CE patch 不走这里：它由 {@code CeNpcPatchOptional.sameCeProvider} 反射比较，
     * 以免在 CE 未安装时解析 CE 类型。
     */
    default Object getPatchProviderIdentity() {
        return null;
    }
}
