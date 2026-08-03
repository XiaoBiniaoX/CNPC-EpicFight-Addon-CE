package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.common.PlayerStunHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.damagesource.StunType;

/**
 * Adjusts the stun a target receives from an NPC attack:
 * <ul>
 *   <li>the {@code stunType} argument is downgraded when the attacker's impact is low
 *       (fixed-length types like LONG/KNOCKDOWN cannot be shortened in place);</li>
 *   <li>the {@code stunTime} argument is scaled by the combined armour + impact factor.
 *       Note that {@code applyStun} forwards {@code 0.0F} instead of {@code stunTime} for
 *       stun types with {@code hasFixedStunTime()} (LONG, KNOCKDOWN, NEUTRALIZE, FALL),
 *       whose length is baked into the animation; duration scaling therefore only affects
 *       SHORT and HOLD stuns, which is exactly why the type is downgraded separately.</li>
 * </ul>
 * The factors are staged by {@link PlayerStunHandler} while handling
 * {@code EntityStunEvent}, which Epic Fight fires immediately before this call in the same
 * synchronous block. Anything else -- vanilla mobs, PvP, non-player targets -- gets neutral
 * values and is therefore untouched.
 */
@Mixin(value = LivingEntityPatch.class, remap = false)
public abstract class MixinLivingEntityPatchStun {

    @ModifyVariable(method = "applyStun", at = @At("HEAD"), argsOnly = true, index = 1)
    private StunType cnpcef$downgradeStunType(StunType stunType) {
        return PlayerStunHandler.applyImpactDowngrade(this, stunType);
    }

    @ModifyVariable(method = "applyStun", at = @At("HEAD"), argsOnly = true, index = 2)
    private float cnpcef$scaleStunTime(float stunTime) {
        float scale = PlayerStunHandler.consumeStunScale(this);

        if (scale == 1.0F) {
            return stunTime;
        }

        return stunTime * scale;
    }
}
