package com.goodbird.cnpcefaddon.common;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.resources.ResourceLocation;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.property.AnimationProperty.PlaybackSpeedModifier;
import yesman.epicfight.api.animation.types.StaticAnimation;

import static yesman.epicfight.api.animation.property.AnimationProperty.StaticAnimationProperty.PLAY_SPEED_MODIFIER;

public class EntityPlaySpeedManager {
    public static final PlaybackSpeedModifier MODIFIER = (self, entitypatch, baseSpeed, prevElapsedTime, elapsedTime) -> {
        ResourceLocation entityType = EntityType.getKey(entitypatch.getOriginal().getType());
        ResourceLocation animKey = self.getRealAnimation().get().getRegistryName();
        if (entityType == null || animKey == null) return baseSpeed;
        float cached = PlaySpeedCache.getSpeed(entityType, animKey);
        return cached != 1.0F ? cached * baseSpeed : baseSpeed;
    };

    public static void ensureModifier(ResourceLocation animKey) {
        AnimationAccessor<StaticAnimation> accessor = AnimationManager.byKey(animKey);
        if (accessor != null && accessor.get() != null) {
            accessor.get().addProperty(PLAY_SPEED_MODIFIER, MODIFIER);
        }
    }
}
