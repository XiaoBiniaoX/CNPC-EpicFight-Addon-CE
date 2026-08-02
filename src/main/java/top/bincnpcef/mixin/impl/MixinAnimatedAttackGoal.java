package top.bincnpcef.mixin.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.api.animation.types.EntityState;
import yesman.epicfight.world.capabilities.entitypatch.MobPatch;
import yesman.epicfight.world.entity.ai.goal.AnimatedAttackGoal;
import yesman.epicfight.world.entity.ai.goal.CombatBehaviors;

/**
 * 问题4（NPC 不攻击/呆傻）运行时诊断：
 * <ul>
 *   <li>每 100 tick 记录一次攻击状态（目标/inaction/canBasicAttack/是否已激活招式）；</li>
 *   <li>每次行为执行（Behavior.execute）记录一行。</li>
 * </ul>
 * 通过 latest.log 可定位卡点：
 * <ul>
 *   <li>无日志 → goal 未注册/未 tick（setAIAsInfantry 失败或 patch 未附加）；</li>
 *   <li>一直 inaction=true → 状态卡死（动画未结束）；</li>
 *   <li>hasMove=false 且 !inaction 且 target!=null → 行为选择持续失败（条件不满足）；</li>
 *   <li>有 execute 日志 → 行为正常执行。</li>
 * </ul>
 * 诊断完成后可整体删除本类及 mixins.json 中的注册。
 */
@Mixin(value = AnimatedAttackGoal.class, remap = false)
public abstract class MixinAnimatedAttackGoal {
    private static final Logger LOGGER = LoggerFactory.getLogger("CNPC-EF-Addon");

    @Shadow
    @Final
    private MobPatch<?> mobpatch;

    @Shadow
    @Final
    private CombatBehaviors<?> combatBehaviors;

    private long lastStateLog = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void cnpcef$diagnoseAttack(CallbackInfo ci) {
        var original = this.mobpatch.getOriginal();
        long now = System.currentTimeMillis();
        boolean targetMissing = this.mobpatch.getTarget() == null;

        if (original == null) {
            return;
        }
        if (!targetMissing && now - lastStateLog < 5000) {
            return;
        }
        if (targetMissing && now - lastStateLog < 1000) {
            return;
        }
        lastStateLog = now;

        EntityState state = this.mobpatch.getEntityState();
        LOGGER.info("[diag-attack] {}: target={}, dist={}, inaction={}, canBasicAttack={}, hasActivatedMove={}",
            original.getName().getString(),
            this.mobpatch.getTarget() == null ? "null" : this.mobpatch.getTarget().getName().getString(),
            this.mobpatch.getTarget() == null ? "-" : String.format("%.1f", original.distanceTo(this.mobpatch.getTarget())),
            state.inaction(), state.canBasicAttack(),
            this.combatBehaviors.hasActivatedMove());
    }

    @Inject(method = "tick",
        at = @At(value = "INVOKE",
            target = "Lyesman/epicfight/world/entity/ai/goal/CombatBehaviors$Behavior;execute(Lyesman/epicfight/world/capabilities/entitypatch/MobPatch;)V"))
    private void cnpcef$diagnoseExecute(CallbackInfo ci) {
        var original = this.mobpatch.getOriginal();
        if (original != null) {
            LOGGER.info("[diag-attack] {} executes an attack behavior (hasActivatedMove={})",
                original.getName().getString(), this.combatBehaviors.hasActivatedMove());
        }
    }
}
