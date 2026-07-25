package com.goodbird.cnpcefaddon.common;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.property.AnimationProperty.PlaybackSpeedModifier;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import static yesman.epicfight.api.animation.property.AnimationProperty.StaticAnimationProperty.PLAY_SPEED_MODIFIER;

/**
 * NPC datapack play_speed support.
 * <p>
 * EF animations are shared globals. Installing a PLAY_SPEED_MODIFIER must
 * chain-preserve any existing modifier (attack-speed caps from EF / epicfight_extra).
 * Never replace the property outright — that was causing player weapons to ignore
 * attack-speed limits when this addon loaded.
 */
public class EntityPlaySpeedManager {

    private static final class ChainedModifier implements PlaybackSpeedModifier {
        private final PlaybackSpeedModifier previous;

        private ChainedModifier(PlaybackSpeedModifier previous) {
            this.previous = unwrap(previous);
        }

        private static PlaybackSpeedModifier unwrap(PlaybackSpeedModifier modifier) {
            PlaybackSpeedModifier current = modifier;
            while (current instanceof ChainedModifier chained) {
                current = chained.previous;
            }
            return current;
        }

        @Override
        public float modify(DynamicAnimation self, LivingEntityPatch<?> entitypatch,
                            float baseSpeed, float prevElapsedTime, float elapsedTime) {
            float speed = baseSpeed;
            if (previous != null) {
                try {
                    speed = previous.modify(self, entitypatch, speed, prevElapsedTime, elapsedTime);
                } catch (Throwable ignored) {
                }
            }
            return applyNpcSpeed(self, entitypatch, speed);
        }
    }

    public static final PlaybackSpeedModifier MODIFIER =
            (self, entitypatch, baseSpeed, prevElapsedTime, elapsedTime) ->
                    applyNpcSpeed(self, entitypatch, baseSpeed);

    private static float applyNpcSpeed(DynamicAnimation self, LivingEntityPatch<?> entitypatch, float baseSpeed) {
        try {
            Entity original = entitypatch.getOriginal();
            if (original == null) {
                return baseSpeed;
            }
            ResourceLocation entityType = EntityType.getKey(original.getType());
            ResourceLocation animKey = resolveAnimKey(self);
            if (entityType == null || animKey == null) {
                return baseSpeed;
            }
            float cached = PlaySpeedCache.getSpeed(entityType, animKey);
            if (cached == 1.0F) {
                return baseSpeed;
            }
            return cached * baseSpeed;
        } catch (Throwable t) {
            return baseSpeed;
        }
    }

    private static ResourceLocation resolveAnimKey(DynamicAnimation self) {
        try {
            AssetAccessor<? extends StaticAnimation> real = self.getRealAnimation();
            if (real == null) {
                return null;
            }
            StaticAnimation anim = real.get();
            if (anim == null) {
                return null;
            }
            return anim.getRegistryName();
        } catch (Throwable t) {
            return null;
        }
    }

    public static void ensureModifier(ResourceLocation animKey) {
        if (animKey == null) {
            return;
        }
        AnimationAccessor<? extends StaticAnimation> accessor = AnimationManager.byKey(animKey);
        if (accessor == null) {
            return;
        }
        StaticAnimation anim = accessor.get();
        if (anim == null) {
            return;
        }

        PlaybackSpeedModifier current = anim.getProperty(PLAY_SPEED_MODIFIER).orElse(null);
        // Idempotent: re-wrap only peels existing ChainedModifier and keeps foreign previous once.
        anim.addProperty(PLAY_SPEED_MODIFIER, new ChainedModifier(current));
    }

    public static void clearPatched() {
        // no-op retained for call sites; chaining is idempotent via unwrap
    }
}
