package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.common.AnimSpeedFactor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * 把单个 NPC 的动画攻击速度倍率（{@link AnimSpeedFactor}）乘进攻击动画的播放速度。
 *
 * <p><b>为什么落在这里</b>：{@code AttackAnimation.getPlaySpeed} 是攻击动画速度的唯一来源，
 * 且同时被三处消费 —— {@code AnimationPlayer:32}（推进 elapsedTime，即画面速度）、
 * {@code AttackAnimation:229}（{@code getCollidingEntities} 的判定窗口）、
 * {@code AttackAnimation:423}（{@code ATTACKING} 状态判定）。在这里改，动画与判定同步缩放，
 * 不会出现「动作变快但判定框还按原速走」。
 *
 * <p>不改 {@code EntityPlaySpeedManager.applyNpcSpeed}（PLAY_SPEED_MODIFIER 链）的原因：
 * 那条链只挂在<b>已经调用过 {@code ensureModifier}</b> 的动画上，而 {@code ensureModifier}
 * 仅对数据包里配了 {@code play_speed} 的动画和两条 ranged 拉弓动画调用
 * （调用点：{@code PlaySpeedCache:66,187,278}、{@code EntityPlaySpeedManager:144}）。
 * 未配 {@code play_speed} 的攻击动画根本没有 modifier，倍率会静默失效。
 *
 * <p><b>与 play_speed 的关系是相乘</b>：EF 先在 {@code AnimationPlayer:32} 取本方法的返回值，
 * 再于 {@code :36} 应用 PLAY_SPEED_MODIFIER（其中 {@code applyNpcSpeed} 乘上数据包的
 * {@code play_speed}）。两者是相继相乘的两个环节，故数据包 6.0 配合输入框 2.0 得到 12.0。
 *
 * <p>只作用于 CNPC 的 NPC：{@link AnimSpeedFactor#of} 对玩家与原版生物恒返回 1.0，
 * 玩家的攻速上限逻辑（本方法在 {@code PlayerPatch} 分支算出的 correctedSpeed）不受影响
 * （踩坑第 26 条：动 EF 共用类必须先限定实体）。
 *
 * <p>{@code epicfight} 是必选依赖，故无需 {@code require = 0}。目标签名已用
 * {@code javap -p} 核对：{@code public float getPlaySpeed(LivingEntityPatch, DynamicAnimation)}
 * （约法第 15 条）。
 */
@Mixin(AttackAnimation.class)
public abstract class MixinAttackAnimationSpeed {

    @Inject(method = "getPlaySpeed", at = @At("RETURN"), cancellable = true, remap = false)
    private void cnpcef$scaleNpcAttackSpeed(LivingEntityPatch<?> entitypatch, DynamicAnimation animation,
                                            CallbackInfoReturnable<Float> cir) {
        if (entitypatch == null) {
            return;
        }
        float factor = AnimSpeedFactor.of(entitypatch.getOriginal());
        if (factor == AnimSpeedFactor.DEFAULT) {
            return;
        }
        float base = cir.getReturnValueF();
        // EF 自己会对异常速度做钳制，但这里先挡一层：非有限值乘出来会污染
        // AnimationClip 的二分查找（踩坑第 51 条的 NaN 死循环就是这么来的）。
        if (!Float.isFinite(base)) {
            return;
        }
        cir.setReturnValue(base * factor);
    }
}
