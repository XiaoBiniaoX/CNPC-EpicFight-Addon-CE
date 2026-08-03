package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.api.IDataMeleeAttackDesire;
import noppes.npcs.ai.EntityAIAttackTarget;
import noppes.npcs.entity.data.DataMelee;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    private static final Logger LOGGER = LogManager.getLogger("cnpcefaddon");

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

        int scaled = Math.max(1, (int) Math.round(delay * Math.pow(5.0F / desire, 3.0)));

        if (desire != 5.0F) {
            LOGGER.info("cnpcef-ai: desire={} delay={} scaled={}", desire, delay, scaled);
        }

        return scaled;
    }
}
