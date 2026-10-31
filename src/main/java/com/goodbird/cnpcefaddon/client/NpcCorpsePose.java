package com.goodbird.cnpcefaddon.client;

import com.goodbird.cnpcefaddon.common.patch.INpcPatch;
import com.goodbird.cnpcefaddon.mixin.impl.IAnimatorRevival;
import com.goodbird.cnpcefaddon.mixin.impl.IBaseLayerPriority;
import com.goodbird.cnpcefaddon.mixin.impl.ILayerAnimationQueue;
import noppes.npcs.api.constants.AnimationType;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.property.AnimationProperty.ActionAnimationProperty;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.animation.ClientAnimator;
import yesman.epicfight.api.client.animation.Layer;
import yesman.epicfight.gameasset.Animations;

/**
 * 客户端每个 EF NPC 的死亡生命周期：新 Animator 只补播一次，复活只退出死亡层。
 * 不扫描全世界、不保存 UUID 全局表；不改真实尸体计时、隐藏尸体或显式脚本 DEATH。
 */
public final class NpcCorpsePose {
    private static final Logger LOGGER = LoggerFactory.getLogger(NpcCorpsePose.class);
    private ClientAnimator restoredAnimator;
    private boolean wasDead;
    private boolean deathSignalAtStart;
    private boolean recoveryPending;

    public void beforeTick(EntityNPCInterface npc) {
        // CNPC 自己可能在 super.tick 之后清计时，必须先捕获，不能只看 tick 末尾。
        deathSignalAtStart = wasDead || npc.deathTime > 0 || npc.getHealth() <= 0.0F || npc.isKilled();
    }

    public void afterTick(EntityNPCInterface npc) {
        boolean dead = npc.getHealth() <= 0.0F || npc.isKilled();
        boolean revive = recoveryPending || deathSignalAtStart || npc.deathTime > 0;
        wasDead = dead;
        if (npc.isRemoved()) return;
        // 重绑定 capability 可能晚一 tick；不能在拿到 patch 前丢掉复活信号。
        recoveryPending = !dead && revive;

        LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(npc, LivingEntityPatch.class);
        if (!(patch instanceof INpcPatch) || !(patch.getAnimator() instanceof ClientAnimator animator)) return;
        // 计时已被上游清掉时，EF 自身仍为 DEATH 也是死亡生命周期信号。
        revive |= animator.currentMotion() == LivingMotions.DEATH || patch.currentLivingMotion == LivingMotions.DEATH
                || animator.currentCompositeMotion() == LivingMotions.DEATH || patch.currentCompositeMotion == LivingMotions.DEATH;
        if (!dead && !revive) return;
        try {
            if (!dead) {
                restoredAnimator = null;
                // 14 是 CNPC 显式脚本死亡姿态，不是复活计时残留；不清该脚本的动画。
                if (npc.currentAnimation == AnimationType.DEATH || !(npc.getHealth() > 0.0F)) return;
                recoveryPending = true;
                if (npc.deathTime > 0) {
                    npc.deathTime = 0;
                    npc.refreshDimensions();
                }
                recoverLivingPose(patch, animator);
                recoveryPending = false;
            } else if (npc.stats.hideKilledBody) {
                recoveryPending = false;
                restoredAnimator = null;
            } else if (npc.getHealth() <= 0.0F && restoredAnimator != animator) {
                recoveryPending = false;
                var death = animator.getLivingAnimation(LivingMotions.DEATH, null);
                if (!valid(death)) death = animator.getCompositeLivingMotion(LivingMotions.DEATH);
                if (!valid(death)) return; // 缺映射仍交给 EF，不硬塞另一种骨架的动画。
                Layer layer = death.get().getLayerType() == Layer.LayerType.BASE_LAYER
                        ? animator.baseLayer : animator.getCompositeLayer(death.get().getPriority());
                // 死亡事件已播/排队时不再从零补播；第三方不必声明 IS_DEATH_ANIMATION。
                if (!isDeath(layer.animationPlayer.getRealAnimation(), death, null)
                        && !isDeath(layer.getNextAnimation(), death, null)) {
                    animator.playAnimation(death, 0.0F);
                }
                patch.currentLivingMotion = LivingMotions.DEATH;
                ((IAnimatorRevival) animator).cnpcef$setCurrentMotion(LivingMotions.DEATH);
                restoredAnimator = animator;
            }
        } catch (Throwable t) {
            LOGGER.error("[cnpcef-fix] 死亡 NPC 姿势补播失败：entityId={} uuid={} hideKilledBody={} deathTime={}",
                    npc.getId(), npc.getUUID(), npc.stats.hideKilledBody, npc.deathTime, t);
        }
    }

    private static void recoverLivingPose(LivingEntityPatch<?> patch, ClientAnimator animator) {
        var death = animator.getLivingAnimation(LivingMotions.DEATH, null);
        var compositeDeath = animator.getCompositeLivingMotion(LivingMotions.DEATH);
        animator.iterAllLayers(layer -> {
            boolean currentDeath = isDeath(layer.animationPlayer.getRealAnimation(), death, compositeDeath);
            var next = layer.getNextAnimation();
            boolean queuedDeath = isDeath(next, death, compositeDeath);
            if (!currentDeath) {
                if (queuedDeath) ((ILayerAnimationQueue) layer).cnpcef$setNextAnimation(null);
                return; // 当前脚本动作照常推进，只撤掉过期死亡队列。
            }
            var replacement = next != null && !queuedDeath ? next : layer == animator.baseLayer
                    ? animator.getLivingAnimation(LivingMotions.IDLE, Animations.EMPTY_ANIMATION)
                    : Animations.EMPTY_ANIMATION;
            if (replacement == null || isDeath(replacement, death, compositeDeath)) replacement = Animations.EMPTY_ANIMATION;
            layer.playAnimationInstantly(replacement, patch); // 同时 end / begin / 清队列 / 更新 EntityState。
            if (layer != animator.baseLayer && replacement == Animations.EMPTY_ANIMATION) {
                layer.disableLayer();
            }
        });
        // 上游可能已换走死亡片段，却留下 HIGHEST；按实际当前动画恢复，不强降脚本动作。
        ((IBaseLayerPriority) animator.baseLayer).cnpcef$setBaseLayerPriority(
                animator.baseLayer.animationPlayer.getRealAnimation().get().getPriority());
        IAnimatorRevival motions = (IAnimatorRevival) animator;
        if (animator.currentMotion() == LivingMotions.DEATH) motions.cnpcef$setCurrentMotion(LivingMotions.IDLE);
        if (animator.currentCompositeMotion() == LivingMotions.DEATH) motions.cnpcef$setCurrentCompositeMotion(LivingMotions.NONE);
        patch.updateEntityState(); // 保留非死亡动作的锁定，不强塞 DEFAULT_STATE。
        patch.updateMotion(true);
    }

    private static boolean valid(AssetAccessor<? extends StaticAnimation> animation) {
        return animation != null && !AnimationManager.checkNull(animation);
    }

    private static boolean isDeath(AssetAccessor<? extends DynamicAnimation> animation,
                                   AssetAccessor<? extends StaticAnimation> death,
                                   AssetAccessor<? extends StaticAnimation> compositeDeath) {
        if (animation == null) return false;
        var real = animation.get().getRealAnimation();
        if (real == null || AnimationManager.checkNull(real)) return false;
        return (valid(death) && real.get() == death.get())
                || (valid(compositeDeath) && real.get() == compositeDeath.get())
                || real.get().getProperty(ActionAnimationProperty.IS_DEATH_ANIMATION).orElse(false);
    }
}
