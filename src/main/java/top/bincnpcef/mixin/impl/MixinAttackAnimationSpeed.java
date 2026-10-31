package top.bincnpcef.mixin.impl;

import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bincnpcef.api.IDataDisplay;
import top.bincnpcef.common.NpcCombatParameters;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

@Mixin(value = AttackAnimation.class, remap = false)
public class MixinAttackAnimationSpeed {
    @Inject(method = "getPlaySpeed", at = @At("RETURN"), cancellable = true)
    private void cnpcef$scale(LivingEntityPatch<?> patch, DynamicAnimation animation,
                              CallbackInfoReturnable<Float> cir) {
        if (patch == null || !(patch.getOriginal() instanceof EntityNPCInterface npc) || npc.display == null) return;
        float factor = NpcCombatParameters.animationFactor(NpcCombatParameters.desire(npc))
                * ((IDataDisplay) npc.display).cnpcef$getAnimSpeed();
        float base = cir.getReturnValueF();
        if (factor != 1.0F && Float.isFinite(base) && Float.isFinite(base * factor)) {
            cir.setReturnValue(base * factor);
        }
    }
}
