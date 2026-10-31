package top.bincnpcef.common;

import noppes.npcs.entity.EntityNPCInterface;
import top.bincnpcef.mixin.impl.IServerAnimatorQueue;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.ServerAnimator;
import yesman.epicfight.api.animation.property.AnimationProperty.ActionAnimationProperty;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/** 只退出死亡映射/死亡属性标记，不清普通脚本、连段或强塞 DEFAULT_STATE。 */
public final class NpcDeathRecovery {
    private boolean wasDead;
    private boolean signal;
    private boolean pending;

    public void beforeTick(EntityNPCInterface npc) {
        signal = wasDead || npc.deathTime > 0 || dead(npc);
    }

    public void afterTick(EntityNPCInterface npc) {
        boolean dead = dead(npc);
        boolean revive = pending || signal || npc.deathTime > 0;
        wasDead = dead;
        if (npc.isRemoved()) return;
        pending = !dead && revive;
        LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(npc, LivingEntityPatch.class);
        if (patch == null || !(patch.getAnimator() instanceof ServerAnimator animator) || dead
                || npc.currentAnimation == 14 || !(npc.getHealth() > 0.0F)) return;
        revive |= patch.currentLivingMotion == LivingMotions.DEATH || patch.currentCompositeMotion == LivingMotions.DEATH;
        if (!revive) return;
        var death = animator.getLivingAnimation(LivingMotions.DEATH, null);
        IServerAnimatorQueue queue = (IServerAnimatorQueue) animator;
        var next = queue.cnpcef$getNextAnimation();
        boolean queuedDeath = isDeath(next, death, null);
        if (isDeath(animator.animationPlayer.getRealAnimation(), death, null)) {
            // 不把第三方 DynamicAnimation 队列降级为 getRealAnimation()；原样保留给 EF tick。
            queue.cnpcef$setNextAnimation(queuedDeath ? null : next);
            animator.playAnimationInstantly(Animations.EMPTY_ANIMATION);
            animator.setHardPause(false);
        } else if (queuedDeath) {
            queue.cnpcef$setNextAnimation(null);
        }
        if (patch.currentLivingMotion == LivingMotions.DEATH) patch.currentLivingMotion = LivingMotions.IDLE;
        if (patch.currentCompositeMotion == LivingMotions.DEATH) patch.currentCompositeMotion = LivingMotions.NONE;
        if (npc.deathTime > 0) {
            npc.deathTime = 0;
            npc.refreshDimensions();
        }
        patch.updateEntityState();
        pending = false;
    }

    public static boolean dead(EntityNPCInterface npc) {
        return npc.getHealth() <= 0.0F || npc.isKilled();
    }

    public static boolean valid(AssetAccessor<? extends StaticAnimation> animation) {
        return animation != null && !AnimationManager.checkNull(animation);
    }

    public static boolean isDeath(AssetAccessor<? extends DynamicAnimation> animation,
                                  AssetAccessor<? extends StaticAnimation> death,
                                  AssetAccessor<? extends StaticAnimation> compositeDeath) {
        if (animation == null) return false;
        var real = animation.get().getRealAnimation();
        if (!valid(real)) return false;
        return (valid(death) && real.get() == death.get())
                || (valid(compositeDeath) && real.get() == compositeDeath.get())
                || real.get().getProperty(ActionAnimationProperty.IS_DEATH_ANIMATION).orElse(false);
    }
}
