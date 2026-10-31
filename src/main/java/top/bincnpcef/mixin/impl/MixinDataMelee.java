package top.bincnpcef.mixin.impl;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.data.DataMelee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bincnpcef.api.IDataMeleeAttackDesire;
import top.bincnpcef.common.NpcCombatParameters;

@Mixin(value = DataMelee.class, remap = false)
public class MixinDataMelee implements IDataMeleeAttackDesire {
    @Unique private float cnpcef$attackDesire = NpcCombatParameters.DEFAULT_DESIRE;

    @Override
    public float cnpcef$getAttackDesire() {
        return cnpcef$attackDesire;
    }

    @Override
    public void cnpcef$setAttackDesire(float value) {
        cnpcef$attackDesire = NpcCombatParameters.sanitizeDesire(value);
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void cnpcef$load(CompoundTag tag, CallbackInfo ci) {
        cnpcef$setAttackDesire(NpcCombatParameters.read(tag, NpcCombatParameters.DESIRE_KEY,
                NpcCombatParameters.DEFAULT_DESIRE));
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void cnpcef$save(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        cir.getReturnValue().putFloat(NpcCombatParameters.DESIRE_KEY, cnpcef$attackDesire);
    }
}
