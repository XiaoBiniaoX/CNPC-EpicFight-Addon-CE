package top.bincnpcef.mixin.impl;

import noppes.npcs.ai.EntityAIAttackTarget;
import noppes.npcs.entity.data.DataMelee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.bincnpcef.api.IDataMeleeAttackDesire;
import top.bincnpcef.common.NpcCombatParameters;

@Mixin(EntityAIAttackTarget.class)
public class MixinEntityAIAttackTarget {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/data/DataMelee;getDelay()I"))
    private int cnpcef$delay(DataMelee melee) {
        return NpcCombatParameters.adjustedDelay(melee.getDelay(),
                ((IDataMeleeAttackDesire) melee).cnpcef$getAttackDesire());
    }
}
