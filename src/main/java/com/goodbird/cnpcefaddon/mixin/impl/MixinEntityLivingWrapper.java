package com.goodbird.cnpcefaddon.mixin.impl;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.api.wrapper.EntityLivingBaseWrapper;
import noppes.npcs.api.wrapper.EntityWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

@Mixin(EntityLivingBaseWrapper.class)
public class MixinEntityLivingWrapper<T extends LivingEntity> extends EntityWrapper<T> {
    public MixinEntityLivingWrapper(T entity) {
        super(entity);
    }

    @Unique
    public void playEFAnimation(String animPath){
        AssetAccessor<StaticAnimation> anim = AnimationManager.byKey(ResourceLocation.parse(animPath));
        LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(entity, LivingEntityPatch.class);
        patch.playAnimationSynchronized(anim, 0.0F);
    }
}
