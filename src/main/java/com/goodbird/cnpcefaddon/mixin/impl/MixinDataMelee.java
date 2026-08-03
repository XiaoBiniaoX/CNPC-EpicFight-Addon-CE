package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.api.IDataMeleeAttackDesire;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.data.DataMelee;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds the melee aggression value to Custom NPCs' {@link DataMelee}, persisted with the
 * rest of the melee data under its own NBT key so the GUI edit survives a save / load.
 */
@Mixin(DataMelee.class)
public abstract class MixinDataMelee implements IDataMeleeAttackDesire {

    private static final Logger LOGGER = LogManager.getLogger("cnpcefaddon");

    @Unique
    private float cnpcef$attackDesire = 5.0F;

    @Override
    public float getAttackDesire() {
        return this.cnpcef$attackDesire;
    }

    @Override
    public void setAttackDesire(float attackDesire) {
        this.cnpcef$attackDesire = attackDesire;
    }

    @Inject(method = "load", at = @At("TAIL"), remap = false)
    private void cnpcef$loadAttackDesire(CompoundTag compound, CallbackInfo ci) {
        if (compound.contains("MeleeAttackDesire")) {
            this.cnpcef$attackDesire = compound.getFloat("MeleeAttackDesire");
            LOGGER.info("cnpcef-ai: loaded desire={}", this.cnpcef$attackDesire);
        }
    }

    @Inject(method = "save", at = @At("TAIL"), remap = false)
    private void cnpcef$saveAttackDesire(CompoundTag compound, CallbackInfoReturnable<CompoundTag> cir) {
        compound.putFloat("MeleeAttackDesire", this.cnpcef$attackDesire);
    }
}
