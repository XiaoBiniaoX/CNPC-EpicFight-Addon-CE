package top.bincnpcef.mixin.impl;

import java.util.function.Function;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bincnpcef.common.NpcMeleeRange;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.world.capabilities.entitypatch.MobPatch;
import yesman.epicfight.world.entity.ai.goal.CombatBehaviors;

/** Checked before selection/count/cooldown and again for the next combo behavior. */
@Mixin(value = CombatBehaviors.Behavior.Builder.class, remap = false)
public abstract class MixinCombatBehaviorMeleeRange<T extends MobPatch<?>> {
    @Shadow public abstract CombatBehaviors.Behavior.Builder<T> custom(Function<T, Boolean> predicate);

    @Inject(method = "animationBehavior", at = @At("RETURN"))
    private void cnpcef$meleeRange(AnimationAccessor<? extends StaticAnimation> motion,
                                  CallbackInfoReturnable<CombatBehaviors.Behavior.Builder<T>> cir) {
        custom(patch -> NpcMeleeRange.canSelect(patch, motion));
    }
}
