package com.goodbird.cnpcefaddon.mixin.impl;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 CNPC 近战选项里的「攻击效果」只在伤害真正落地时才施加。
 *
 * <p><b>问题</b>：玩家在 Epic Fight 下闪避成功、或格挡、或处于无敌帧时，NPC 仍会给玩家上 buff。
 *
 * <p><b>根因（字节码已核）</b>：CNPC 的 {@code doHurtTarget}（srg {@code m_7327_}）把
 * {@code Entity.hurt} 的返回值存进局部变量 4（偏移 {@code 106 invokevirtual m_6469_} →
 * {@code 109 istore 4}），最后也是 {@code 378 iload 4 / 380 ireturn} 返回它。
 * 但中间的效果施加段（偏移 285~375）<b>完全不读这个变量</b>：
 * <pre>
 *   if (this.stats.melee.getEffectType() == 0) return var4;
 *   if (this.stats.melee.getEffectType() != 666) {
 *       ((LivingEntity) target).addEffect(new MobEffectInstance(...));   // 无条件
 *   } else {
 *       target.setSecondsOnFire(...);                                   // 无条件
 *   }
 * </pre>
 * 即「只要这个方法被调用过」就上 buff，与是否真的造成伤害无关。
 *
 * <p><b>为什么在 EF 下特别容易触发</b>：EF 的判定链是
 * {@code AttackAnimation.hurtCollidingEntities:229} 取碰撞到的实体 →
 * {@code :248 entitypatch.attack(...)} → {@code MobPatch.attack:164
 * this.original.doHurtTarget(target)}。
 * 第 164 行是<b>无条件调用</b>的，EF 只在 {@code :251} 用
 * {@code attackResult.resultType.dealtDamage()} 判断要不要放命中音效和粒子。
 * 也就是说：碰撞箱一接触就会进 {@code doHurtTarget}，闪避 / 格挡 / 无敌帧只让
 * {@code hurt} 返回 false，而 CNPC 那段 buff 代码看不到这个 false。
 *
 * <p><b>修法</b>：{@code @Redirect} 拦住效果施加的那两个调用，只有当本次
 * {@code hurt} 确实返回过 true 时才放行。{@code hurt} 的返回值用
 * {@code @Redirect} 同步记录到一个 {@code @Unique} 字段，
 * 不依赖读取 CNPC 的局部变量槽（局部变量编号会随反编译/重编译变化，不可靠）。
 *
 * <p>该字段每次进入 {@code doHurtTarget} 时在 HEAD 处复位，因此不会跨调用残留；
 * 同一实体的多次攻击各自独立。
 *
 * <p><b>影响面</b>：只改「效果是否施加」这一个判断，伤害数值、击退、
 * aggro、任务触发、返回值全部不变。非 EF 的普通 CNPC 也一并修正 —— 原生用
 * {@code EntityAIAttackTarget} 时若目标处于无敌帧，同样不该上 buff。
 * 数据包与 NBT 零改动。
 */
@Mixin(EntityNPCInterface.class)
public abstract class MixinEntityNpcAttackEffect {

    /** 本次 {@code doHurtTarget} 调用中 {@code hurt} 是否返回过 true。 */
    @Unique
    private boolean cnpcef$damageLanded;

    @Inject(method = "doHurtTarget", at = @At("HEAD"))
    private void cnpcef$resetDamageLanded(Entity target, CallbackInfoReturnable<Boolean> cir) {
        this.cnpcef$damageLanded = false;
    }

    /**
     * 记录 {@code hurt} 的真实结果并原样转发。
     *
     * <p>{@code Entity.hurt} 是原版方法，运行时为混淆名 {@code m_6469_}，
     * 因此这条 {@code @At} 必须走默认 remap（踩坑第 36 条：目标类是第三方类时
     * 整体 {@code remap=false} 会让原版方法名不被重映射，注入静默不命中）。
     */
    @Redirect(method = "doHurtTarget",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean cnpcef$recordDamageResult(Entity instance,
                                              net.minecraft.world.damagesource.DamageSource source, float amount) {
        boolean landed = instance.hurt(source, amount);
        this.cnpcef$damageLanded = landed;
        return landed;
    }

    /** 药水效果：伤害没落地就不施加，并按 vanilla 语义返回 false。 */
    @Redirect(method = "doHurtTarget",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z"))
    private boolean cnpcef$onlyEffectOnRealHit(LivingEntity instance, MobEffectInstance effect) {
        if (!this.cnpcef$damageLanded) {
            return false;
        }
        return instance.addEffect(effect);
    }

    /**
     * 点燃（effectType 666）走另一个调用，同样要判。
     *
     * <p><b>方法名必须是 {@code setRemainingFireTicks}</b>：CNPC 字节码里那句是
     * {@code Entity.m_7311_:(I)V}，而 tsrg 映射为
     * {@code setRemainingFireTicks (I)V m_7311_}。
     * 首版误写成 {@code setSecondsOnFire}，它的 SRG 是 {@code m_20254_} —— 是另一个方法，
     * refmap 会正常生成映射、构建也成功，但运行时找不到该调用，注入静默失效
     * （踩坑第 36/54 条同族：构建成功 ≠ 注入命中）。
     */
    @Redirect(method = "doHurtTarget",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setRemainingFireTicks(I)V"))
    private void cnpcef$onlyIgniteOnRealHit(Entity instance, int ticks) {
        if (!this.cnpcef$damageLanded) {
            return;
        }
        instance.setRemainingFireTicks(ticks);
    }
}
