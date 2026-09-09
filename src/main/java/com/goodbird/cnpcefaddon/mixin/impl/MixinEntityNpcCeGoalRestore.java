package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.common.patch.INpcPatch;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * 让 CombatEvolution 的行为树 Goal 在 CNPC 重建 AI 后存活。
 *
 * <p>CNPC 的 {@code updateTasks()} 会先 {@code clearTasks()} 清空整个 goalSelector，
 * 再只重建自己的 AI；而 CE 的 {@code CEAnimationAttackGoal} / {@code CommonChasingGoal}
 * 是在 patch 的 {@code initAI()} 里挂的，于是被整体抹掉。
 * {@code updateAI = true} 由切换武器、受伤、reset 等多处触发，所以 CE 的 NPC 表现为
 * 「追过来但基本不攻击」——只有第一次重建之前的极短窗口能打出一次攻击。
 *
 * <p>这里在 {@code updateTasks} 结束后补一次 CE 的 {@code initAI()}，仅对持有
 * {@code CeNpcPatch} 的 NPC 生效；旧的三种 patch 口味完全不经过这里。
 */
@Mixin(EntityNPCInterface.class)
public abstract class MixinEntityNpcCeGoalRestore {

    @Inject(method = "updateTasks", at = @At("TAIL"), remap = false, require = 0)
    private void cnpcef$restoreCeGoals(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;

        if (npc.isKilled()) {
            return;
        }

        try {
            LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(npc, LivingEntityPatch.class);

            if (patch instanceof INpcPatch npcPatch) {
                npcPatch.refreshCombatAI();
            }
        } catch (Throwable ignored) {
            // patch 缺失或 CE 未装时保持 CNPC 原生 AI
        }
    }
}
