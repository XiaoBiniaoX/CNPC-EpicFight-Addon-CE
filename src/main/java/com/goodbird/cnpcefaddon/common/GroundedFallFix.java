package com.goodbird.cnpcefaddon.common;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * 修复「脚本播完带位移的 EF 动画后，NPC 已落地却仍以下落/待机姿势平移追击」。
 *
 * <p><b>根因（逐帧日志实证，非推测）</b>：
 * <pre>
 * :2044  walkSpd=0.0594  y=-60.000  dY=-1.1079  onGround=true  airborne=false  motion=FALL
 * :2061  walkSpd=0.3849  y=-60.000  dY=-1.1079  onGround=true  airborne=false  motion=FALL
 * :2100  walkSpd=0.3854  y=-60.000  dY=-1.1079  onGround=true  airborne=false  motion=FALL
 * </pre>
 * NPC <b>已经落地</b>（{@code onGround=true}、{@code y} 恒为 -60.000 不再变化），
 * 行走速度也早已远超阈值（0.3854 &gt;&gt; 0.08），
 * 但 {@code deltaMovement.y} <b>冻结在 -1.1079 连续 56 帧不动</b>。
 *
 * <p>于是 {@code MobPatch:105} 的第一个判定恒为真：
 * <pre>
 * if (this.original.getDeltaMovement().y &lt; -0.55F || this.isAirborneState())
 *     currentLivingMotion = LivingMotions.FALL;          // ← 永远停在这里
 * else if (original.walkAnimation.speed() &gt; 0.08F)      // ← 永远走不到
 *     ... CHASE / WALK
 * </pre>
 * FALL 是单帧静态姿势，视觉上就是「直立不动地滑过来」，
 * 即用户描述的「idle 姿态平移」/「最后一帧动画保持」。
 *
 * <p>注意 {@code isAirborneState()} 实测全程为 false（0 条命中），
 * 所以问题不在 airborne 标记，而在这个不再更新的 {@code deltaMovement.y}。
 *
 * <p><b>修法</b>：仅当「确实已落地」时把残留的向下速度清零，让 EF 的判定继续往下走。
 * 判据取 {@code onGround()} 且 Y 坐标本帧无变化（双条件，避免误伤真实下落）。
 * 不改 EF 的 motion 判定、不动动画层、不碰 {@code airborne}。
 */
public final class GroundedFallFix {

    /** EF 判 FALL 的阈值（{@code MobPatch:85,105}）。 */
    private static final double FALL_THRESHOLD = -0.55D;

    private GroundedFallFix() {
    }

    /**
     * 在 {@code updateMotion} 之<b>前</b>调用：清掉已落地实体上残留的向下速度。
     */
    public static void clearStaleFallVelocity(LivingEntityPatch<?> patch) {
        try {
            LivingEntity original = patch.getOriginal();

            if (original == null || original.getHealth() <= 0.0F) {
                return;
            }

            Vec3 delta = original.getDeltaMovement();

            // 只处理会让 EF 误判 FALL 的残留向下速度。
            if (delta.y >= FALL_THRESHOLD) {
                return;
            }

            // 必须确实站在地面上。
            if (!original.onGround()) {
                return;
            }

            // 双重确认：真在下落时 Y 每帧都会变；已落地则 Y 不再变化。
            // 光看 onGround 不够 —— 落地那一帧 onGround 已为真但速度仍是真实的。
            if (original.getY() != original.yo) {
                return;
            }

            // 保留水平速度，只清垂直残留。
            original.setDeltaMovement(delta.x, 0.0D, delta.z);
        } catch (Throwable ignored) {
            // 任何意外都退回 EF 原行为
        }
    }
}
