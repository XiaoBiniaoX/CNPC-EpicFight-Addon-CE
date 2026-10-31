package top.bincnpcef.client;

import noppes.npcs.entity.EntityNPCInterface;
import top.bincnpcef.common.NpcDeathRecovery;
import top.bincnpcef.mixin.impl.IBaseLayerPriority;
import top.bincnpcef.mixin.impl.IClientAnimatorMotions;
import top.bincnpcef.mixin.impl.ILayerAnimationQueue;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.client.animation.ClientAnimator;
import yesman.epicfight.api.client.animation.Layer;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import static top.bincnpcef.common.NpcDeathRecovery.isDeath;
import static top.bincnpcef.common.NpcDeathRecovery.valid;

/** 每实体保存生命周期，新 Animator 最多补播一次；不扫描世界或建立全局 UUID 表。 */
public final class NpcCorpsePose {
    private ClientAnimator restoredAnimator;
    private boolean wasDead;
    private boolean signal;
    private boolean pending;

    public void beforeTick(EntityNPCInterface npc) {
        signal = wasDead || npc.deathTime > 0 || NpcDeathRecovery.dead(npc);
    }

    public void afterTick(EntityNPCInterface npc) {
        boolean dead = NpcDeathRecovery.dead(npc);
        boolean revive = pending || signal || npc.deathTime > 0;
        wasDead = dead;
        if (npc.isRemoved()) return;
        pending = !dead && revive;
        LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(npc, LivingEntityPatch.class);
        if (patch == null || !(patch.getAnimator() instanceof ClientAnimator animator)) return;
        revive |= animator.currentMotion() == LivingMotions.DEATH || patch.currentLivingMotion == LivingMotions.DEATH
                || animator.currentCompositeMotion() == LivingMotions.DEATH || patch.currentCompositeMotion == LivingMotions.DEATH;
        if (!dead && !revive) return;
        if (!dead) {
            restoredAnimator = null;
            if (npc.currentAnimation == 14 || !(npc.getHealth() > 0.0F)) return;
            if (npc.deathTime > 0) {
                npc.deathTime = 0;
                npc.refreshDimensions();
            }
            recover(patch, animator);
            pending = false;
        } else if (npc.stats.hideKilledBody) {
            restoredAnimator = null;
            pending = false;
        } else if (restoredAnimator != animator) {
            var death = animator.getLivingAnimation(LivingMotions.DEATH, null);
            if (!valid(death)) death = animator.getCompositeLivingMotion(LivingMotions.DEATH);
            if (!valid(death)) death = Animations.BIPED_DEATH;
            if (!valid(death)) return;
            Layer layer = death.get().getLayerType() == Layer.LayerType.BASE_LAYER
                    ? animator.baseLayer : animator.getCompositeLayer(death.get().getPriority());
            if (!isDeath(layer.animationPlayer.getRealAnimation(), death, null)
                    && !isDeath(layer.getNextAnimation(), death, null)) animator.playAnimation(death, 0.0F);
            patch.currentLivingMotion = LivingMotions.DEATH;
            ((IClientAnimatorMotions) animator).cnpcef$setCurrentMotion(LivingMotions.DEATH);
            restoredAnimator = animator;
            pending = false;
        }
    }

    private static void recover(LivingEntityPatch<?> patch, ClientAnimator animator) {
        var death = animator.getLivingAnimation(LivingMotions.DEATH, Animations.BIPED_DEATH);
        var compositeDeath = animator.getCompositeLivingMotion(LivingMotions.DEATH);
        animator.iterAllLayers(layer -> {
            boolean currentDeath = isDeath(layer.animationPlayer.getRealAnimation(), death, compositeDeath);
            var next = layer.getNextAnimation();
            boolean queuedDeath = isDeath(next, death, compositeDeath);
            if (!currentDeath) {
                if (queuedDeath) ((ILayerAnimationQueue) layer).cnpcef$setNextAnimation(null);
                return;
            }
            var replacement = next != null && !queuedDeath ? next : layer == animator.baseLayer
                    ? animator.getLivingAnimation(LivingMotions.IDLE, Animations.EMPTY_ANIMATION) : Animations.EMPTY_ANIMATION;
            if (replacement == null || isDeath(replacement, death, compositeDeath)) replacement = Animations.EMPTY_ANIMATION;
            layer.playAnimationInstantly(replacement, patch);
            if (layer == animator.baseLayer) {
                ((IBaseLayerPriority) animator.baseLayer).cnpcef$setBaseLayerPriority(
                        replacement.get().getRealAnimation().get().getPriority());
            }
            if (layer != animator.baseLayer && replacement == Animations.EMPTY_ANIMATION) layer.disableLayer();
        });
        IClientAnimatorMotions motions = (IClientAnimatorMotions) animator;
        if (animator.currentMotion() == LivingMotions.DEATH) motions.cnpcef$setCurrentMotion(LivingMotions.IDLE);
        if (animator.currentCompositeMotion() == LivingMotions.DEATH) motions.cnpcef$setCurrentCompositeMotion(LivingMotions.NONE);
        // updateMotion 前同时清 patch 的死亡标记，保留普通脚本状态锁。
        if (patch.currentLivingMotion == LivingMotions.DEATH) patch.currentLivingMotion = LivingMotions.IDLE;
        if (patch.currentCompositeMotion == LivingMotions.DEATH) patch.currentCompositeMotion = LivingMotions.NONE;
        patch.updateEntityState();
        patch.updateMotion(true);
    }
}
