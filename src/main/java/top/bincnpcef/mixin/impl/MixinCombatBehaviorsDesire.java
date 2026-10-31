package top.bincnpcef.mixin.impl;

import java.util.List;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.bincnpcef.common.NpcCombatParameters;
import yesman.epicfight.world.capabilities.entitypatch.MobPatch;
import yesman.epicfight.world.entity.ai.goal.CombatBehaviors;

/** 只借鉴兄弟项目的欲望冷却系数，不移植全局选择器/技能逻辑。 */
@Mixin(value = CombatBehaviors.class, remap = false)
public class MixinCombatBehaviorsDesire {
    @Shadow @Final private MobPatch<?> mobpatch;
    @Shadow @Final private List<CombatBehaviors.BehaviorSeries<?>> behaviorSeriesList;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void cnpcef$cooldowns(CallbackInfo ci) {
        if (!(mobpatch.getOriginal() instanceof EntityNPCInterface npc)) return;
        int boost = (int) Math.floor((NpcCombatParameters.desire(npc) - 5.0F) * 0.8F);
        if (boost <= 0) return; // 默认及低欲望完全保留原有动作期冷却语义。
        for (var series : behaviorSeriesList) {
            for (int i = 0; i <= boost; i++) series.tick();
        }
        ci.cancel();
    }
}
