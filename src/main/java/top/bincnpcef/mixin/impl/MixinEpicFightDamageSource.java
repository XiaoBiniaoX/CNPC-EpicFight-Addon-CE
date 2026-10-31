package top.bincnpcef.mixin.impl;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bincnpcef.common.NpcDamageModel;
import yesman.epicfight.world.damagesource.EpicFightDamageSource;

@Mixin(value = EpicFightDamageSource.class, remap = false)
public class MixinEpicFightDamageSource {
    @Inject(method = "calculateDamageAgainst", at = @At("HEAD"), cancellable = true)
    private void cnpcef$meleeStrength(Entity owner, LivingEntity target, float baseDamage,
                                     CallbackInfoReturnable<Float> cir) {
        EpicFightDamageSource self = (EpicFightDamageSource) (Object) this;
        if (NpcDamageModel.useMeleeStrength(self)) {
            // CNPC doHurtTarget 已以 strength 进入 hurt；NeoForge 此处已过护甲/抗性。
            // 保留该值，绝不能重新写 strength 抹掉减伤，只跳过 EF source 的伤害叠加。
            cir.setReturnValue(baseDamage);
        }
    }
}
