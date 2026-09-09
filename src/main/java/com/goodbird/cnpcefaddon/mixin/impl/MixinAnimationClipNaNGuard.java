package com.goodbird.cnpcefaddon.mixin.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.api.animation.AnimationClip;
import yesman.epicfight.api.animation.Pose;

/**
 * 防止 {@code AnimationClip.getPoseInTime} 在 {@code time} 为 NaN 时把渲染线程锁死。
 *
 * <p>EF 该方法（1.20.1 版 {@code AnimationClip:82-100}）用二分查找定位关键帧：
 * <pre>
 * int begin = 0, end = this.bakedTimes.length - 1;
 * while (end - begin &gt; 1) {
 *     int i = begin + (end - begin) / 2;
 *     if (bakedTimes[i] &lt;= time &amp;&amp; bakedTimes[i+1] &gt; time) { ... break; }
 *     else {
 *         if (bakedTimes[i] &gt; time)        end = i;
 *         else if (bakedTimes[i+1] &lt;= time) begin = i;
 *     }
 * }
 * </pre>
 * 循环体内所有分支都是与 {@code time} 的浮点比较。**当 {@code time} 为 NaN 时，
 * 依 IEEE-754 语义全部比较恒为 false**：既不 break，也不改 {@code begin} / {@code end}，
 * 而 {@code end - begin > 1} 保持成立 —— 于是 while 永久自转。
 *
 * <p>这是活锁而非死锁：线程状态始终 RUNNABLE 且持续吃 CPU，故不产生崩溃报告、
 * 不产生异常堆栈、日志完全静默。2026-09-08 实测线程栈（连抓 3 次、间隔 2 秒）：
 * 三次栈顶完全一致地停在 {@code AnimationClip.getPoseInTime(AnimationClip.java:100)}，
 * 而 Render thread 的 cpu 时间从 85015ms → 87125ms → 89265ms 持续上涨，
 * 即「同一位置 + CPU 不断增加」，正是无出口循环的判据。
 *
 * <p>NaN 的来源在调用链上一层 {@code ConcurrentLinkAnimation.getPoseByTime}
 * （{@code :69-70}）：
 * <pre>
 * float currentElapsed = elapsed % this.currentAnimation.get().getTotalTime();
 * float nextElapsed    = elapsed % this.nextAnimation.get().getTotalTime();
 * </pre>
 * {@code getTotalTime()} 取自 {@code AnimationClip.clipTime}。若某条动画的 clipTime
 * 为 0（数据包动画未烘焙 / 资源重载后实例被换掉 / 过渡动画尚未 accept），
 * {@code x % 0} 在 Java 浮点语义下即为 NaN，随后原样传入 {@code getPoseInTime}。
 * 同一表达式还会在 {@code :73} 的 {@code time / getTotalTime()} 产出 Inf。
 *
 * <p>这解释了为何必须「新建 NPC + 设置 EF 属性 + 装备武器」三者叠加才触发：
 * 换 efModel 会重建 capability 与 animator，装备武器又触发 CE 重建行为树与
 * living 动画，恰好制造出「过渡动画的一端 totalTime 为 0」这一窗口。
 *
 * <p>处理方式：仅在 {@code time} 为非有限值（NaN / ±Inf）时接管，返回 EF 自身的
 * {@code Pose.EMPTY_POSE}，等效于「这一帧没有可用姿势」，下一帧重新计算。
 * {@code time} 正常时完全不介入，EF 原逻辑与既有渲染行为一字不动。
 *
 * <p>注意本类为公共 mixin（非 client 段）：{@code AnimationClip} 位于
 * {@code yesman.epicfight.api.animation}，两端共用，服务端的 {@code ServerAnimator}
 * 同样会走 {@code getRawPose}。本类不引用任何客户端类型，符合约法第 22 条。
 */
@Mixin(value = AnimationClip.class, remap = false)
public abstract class MixinAnimationClipNaNGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger("cnpcefaddon.poseguard");

    /** 同一进程只报一次，避免每帧刷屏（该分支一旦成立通常会连续多帧成立）。 */
    private static boolean cnpcef$reported;

    @Inject(method = "getPoseInTime", at = @At("HEAD"), cancellable = true)
    private void cnpcef$guardNonFiniteTime(float time, CallbackInfoReturnable<Pose> cir) {
        if (Float.isFinite(time)) {
            return;
        }

        if (!cnpcef$reported) {
            cnpcef$reported = true;
            LOGGER.error("[cnpcefaddon] 拦截到非有限的动画时间 {}（多为某条动画 totalTime 为 0 导致取模产生 NaN），"
                    + "本帧返回空姿势以避免 EF 二分查找死循环冻结渲染线程", time);
        }

        cir.setReturnValue(Pose.EMPTY_POSE);
    }
}
