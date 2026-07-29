package com.goodbird.cnpcefaddon.mixin.impl;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yesman.epicfight.api.animation.types.EntityState.StateFactor;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

/**
 * Generic guard for {@code StateSpectrum$ConditionalStatesInTime}.
 * <p>
 * Animations registered via {@code newConditionalTimePair(...)} carry a metadata function
 * that Epic Fight invokes for whatever entity plays the animation. Epic Fight addons
 * routinely write that function assuming a player, e.g. Weapons of Miracles uses
 * {@code entitypatch -> ((PlayerPatch)entitypatch).isHoldingAny() ? 1 : 0} on every
 * {@code *_buster_windup} animation. When an NPC plays one, the cast throws
 * ClassCastException inside the entity tick and kills the server thread.
 * <p>
 * Two failure modes are handled:
 * <ul>
 *   <li>the metadata function throwing (bad cast, player-only state access);</li>
 *   <li>the function returning a metadata key that was never registered, which makes
 *       vanilla Epic Fight dereference a null map.</li>
 * </ul>
 * On failure the lowest registered metadata bucket is used, so the animation keeps its
 * declared states instead of crashing. Only the offending conditional pair degrades;
 * every other time pair on the animation is untouched.
 */
@Mixin(targets = "yesman.epicfight.api.animation.types.StateSpectrum$ConditionalStatesInTime", remap = false)
public abstract class MixinConditionalStatesInTime {

    @Redirect(
            method = "getStates",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;"
            )
    )
    @SuppressWarnings("unchecked")
    private Object cnpcef$safeMetadata(Function<LivingEntityPatch<?>, Integer> condition, Object entitypatch) {
        try {
            return condition.apply((LivingEntityPatch<?>) entitypatch);
        } catch (ClassCastException | NullPointerException | IllegalArgumentException
                 | IndexOutOfBoundsException | UnsupportedOperationException ignored) {
            // Addon assumed a player patch. Let the lookup below choose a fallback bucket.
            return null;
        }
    }

    @Redirect(
            method = "getStates",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;get(Ljava/lang/Object;)Ljava/lang/Object;"
            )
    )
    private Object cnpcef$safeLookup(Int2ObjectMap<Map<StateFactor<?>, Object>> conditionalStates, Object metadata) {
        if (metadata != null) {
            Map<StateFactor<?>, Object> states = conditionalStates.get(metadata);

            if (states != null) {
                return states;
            }
        }

        return cnpcef$fallbackBucket(conditionalStates);
    }

    private static Map<StateFactor<?>, Object> cnpcef$fallbackBucket(
            Int2ObjectMap<Map<StateFactor<?>, Object>> conditionalStates) {
        int lowest = Integer.MAX_VALUE;
        boolean found = false;

        for (int key : conditionalStates.keySet()) {
            if (key < lowest) {
                lowest = key;
                found = true;
            }
        }

        if (found) {
            Map<StateFactor<?>, Object> states = conditionalStates.get(lowest);

            if (states != null) {
                return states;
            }
        }

        return Collections.emptyMap();
    }
}
