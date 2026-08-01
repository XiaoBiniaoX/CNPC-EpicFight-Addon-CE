package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.common.PlayerStunHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * Scales the stun duration a player receives from an NPC attack, per
 * {@link com.goodbird.cnpcefaddon.common.AddonConfig}.
 * <p>
 * The factor is staged by {@link PlayerStunHandler} while handling
 * {@code EntityStunEvent}, which Epic Fight fires immediately before this call in the same
 * synchronous block. Anything else -- vanilla mobs, PvP, non-player targets -- gets a
 * neutral 1.0 factor and is therefore untouched.
 * <p>
 * Note that {@code applyStun} forwards {@code 0.0F} instead of {@code stunTime} for stun
 * types with {@code hasFixedStunTime()} (LONG, KNOCKDOWN, NEUTRALIZE, FALL), whose length is
 * baked into the animation. Duration scaling therefore only affects SHORT and HOLD stuns;
 * the armour threshold immunity still covers every type because it cancels the event
 * outright.
 */
@Mixin(value = LivingEntityPatch.class, remap = false)
public abstract class MixinLivingEntityPatchStun {

    @ModifyVariable(method = "applyStun", at = @At("HEAD"), argsOnly = true, index = 2)
    private float cnpcef$scaleStunTime(float stunTime) {
        float scale = PlayerStunHandler.consumeStunScale(this);

        if (scale == 1.0F) {
            return stunTime;
        }

        return stunTime * scale;
    }
}
