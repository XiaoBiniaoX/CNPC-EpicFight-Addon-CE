package top.bincnpcef.mixin.impl;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bincnpcef.common.AddonConfig;
import yesman.epicfight.world.damagesource.EpicFightDamageSource;
import yesman.epicfight.world.damagesource.EpicFightDamageTypeTags;

@Mixin(value = EpicFightDamageSource.class, remap = false)
public class MixinEpicFightDamageSource {
    @Inject(method = "calculateDamageAgainst", at = @At("RETURN"), cancellable = true)
    private void cnpcef$meleeStrength(Entity owner, LivingEntity target, float baseDamage,
                                     CallbackInfoReturnable<Float> cir) {
        EpicFightDamageSource self = (EpicFightDamageSource) (Object) this;
        if (owner instanceof EntityNPCInterface npc && self.getDirectEntity() == npc
                && !self.is(EpicFightDamageTypeTags.EXECUTION)
                && AddonConfig.NPC_DAMAGE_USE_MELEE_STRENGTH.get()
                && npc.stats != null && npc.stats.melee != null) {
            cir.setReturnValue((float) Math.max(0, npc.stats.melee.getStrength()));
        }
    }
}
