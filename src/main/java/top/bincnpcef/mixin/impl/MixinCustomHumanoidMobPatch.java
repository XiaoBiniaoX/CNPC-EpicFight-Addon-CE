package top.bincnpcef.mixin.impl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import noppes.npcs.entity.EntityNPCInterface;
import top.bincnpcef.common.NpcDeathRecovery;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.world.capabilities.entitypatch.CustomHumanoidMobPatch;

/**
 * 修复 NPC 僵直动画被 IDLE 动画覆盖的问题。
 *
 * 根因：MobPatch.commonAggressiveMobUpdateMotion 在 state.inaction() 时，
 * 将 currentLivingMotion 设为 IDLE。但 IDLE 有对应的 living animation，
 * 会覆盖 HitAnimation，导致僵直"只有一瞬间"。
 *
 * 对比玩家：AbstractClientPlayerPatch.updateMotion 使用 !state.updateLivingMotion()
 * 检查，HitAnimation 期间 UPDATE_LIVING_MOTION=false，进入 INACTION 分支，
 * currentLivingMotion 设为 INACTION（无对应动画），不覆盖 HitAnimation。
 *
 * 修复：在 updateMotion TAIL 处，如果 !state.updateLivingMotion()（HitAnimation 期间），
 * 将 currentLivingMotion 和 currentCompositeMotion 改为 INACTION。
 *
 * 注意：state/currentLivingMotion/currentCompositeMotion 都在父类 LivingEntityPatch 中，
 * 无法用 @Shadow。通过 getEntityState() 和 public 字段直接访问。
 */
@Mixin(value = CustomHumanoidMobPatch.class, remap = false)
public class MixinCustomHumanoidMobPatch {
    @Inject(method = "updateMotion", at = @At("TAIL"))
    private void cnpcef$fixStunMotion(boolean considerInaction, CallbackInfo ci) {
        CustomHumanoidMobPatch<?> self = (CustomHumanoidMobPatch<?>) (Object) this;
        // CNPC 的 isKilled 也代表死亡；不能让下方僵直修复把尸体 motion 改为 INACTION。
        if (self.getOriginal() instanceof EntityNPCInterface npc && NpcDeathRecovery.dead(npc)) {
            self.currentLivingMotion = LivingMotions.DEATH;
            self.currentCompositeMotion = LivingMotions.DEATH;
            return;
        }
        if (!self.getEntityState().updateLivingMotion() && considerInaction) {
            self.currentLivingMotion = LivingMotions.INACTION;
            self.currentCompositeMotion = LivingMotions.INACTION;
        }
    }
}
