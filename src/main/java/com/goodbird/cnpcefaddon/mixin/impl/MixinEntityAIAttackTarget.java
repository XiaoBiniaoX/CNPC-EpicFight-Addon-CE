package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.api.IDataMeleeAttackDesire;
import noppes.npcs.ai.EntityAIAttackTarget;
import noppes.npcs.entity.data.DataMelee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Scales the melee attack interval of NPCs by their aggression value. The interval is
 * {@code getDelay() * (5 / desire)^3}: desire 5 keeps the configured attack speed exactly
 * (so old worlds and datapacks without the field behave unchanged), 10 cuts the interval
 * to an eighth (about eight times as aggressive), and 0 disables voluntary melee attacks
 * entirely.
 */
@Mixin(EntityAIAttackTarget.class)
public abstract class MixinEntityAIAttackTarget {

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnoppes/npcs/entity/data/DataMelee;getDelay()I"
            )
    )
    private int cnpcef$desireAdjustedDelay(DataMelee melee) {
        float desire = ((IDataMeleeAttackDesire) (Object) melee).getAttackDesire();
        int delay = melee.getDelay();

        if (desire <= 0.0F) {
            return Integer.MAX_VALUE;
        }

        return Math.max(1, (int) Math.round(delay * Math.pow(5.0F / desire, 3.0)));
    }
}
