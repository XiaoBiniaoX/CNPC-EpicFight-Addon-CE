package com.goodbird.cnpcefaddon.common.provider;

import com.goodbird.cnpcefaddon.common.patch.AdvNpcPatch;
import com.mojang.datafixers.util.Pair;
import com.nameless.indestructible.api.animation.types.LivingEntityPatchEvent;
import com.nameless.indestructible.data.AdvancedMobpatchReloader;
import com.nameless.indestructible.world.ai.CombatBehaviors.GuardMotion;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.model.armature.HumanoidArmature;
import yesman.epicfight.particle.HitParticleType;
import yesman.epicfight.world.capabilities.entitypatch.EntityPatch;
import yesman.epicfight.world.capabilities.entitypatch.Faction;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;
import yesman.epicfight.world.capabilities.item.Style;
import yesman.epicfight.world.capabilities.item.WeaponCategory;
import yesman.epicfight.world.damagesource.StunType;
import yesman.epicfight.world.entity.ai.goal.CombatBehaviors;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class AdvNpcPatchProvider extends AdvancedMobpatchReloader.AdvancedCustomHumanoidMobPatchProvider implements INpcPatchProvider {
    public HumanoidArmature armature;

    public void setAHCombatBehaviors(Map<WeaponCategory, Map<Style, CombatBehaviors.Builder<HumanoidMobPatch<?>>>> val) {
        this.AHCombatBehaviors = val;
    }

    public void setAHWeaponMotions(Map<WeaponCategory, Map<Style, Set<Pair<LivingMotion, AnimationManager.AnimationAccessor<? extends StaticAnimation>>>>> val) {
        this.AHWeaponMotions = val;
    }

    public void setGuardMotions(Map<WeaponCategory, Map<Style, GuardMotion>> val) {
        this.guardMotions = val;
    }

    public void setRegenStaminaStandbyTime(int val) {
        this.regenStaminaStandbyTime = val;
    }

    public void setHasStunReduction(boolean val) {
        this.hasStunReduction = val;
    }

    public void setMaxStunShield(float val) {
        this.maxStunShield = val;
    }

    public void setReganShieldStandbyTime(int val) {
        this.reganShieldStandbyTime = val;
    }

    public void setReganShieldMultiply(float val) {
        this.reganShieldMultiply = val;
    }

    public void setStaminaLoseMultiply(float val) {
        this.staminaLoseMultiply = val;
    }

    public void setGuardRadius(float val) {
        this.guardRadius = val;
    }

    public void setDefaultAnimations(List<Pair<LivingMotion, AnimationManager.AnimationAccessor<? extends StaticAnimation>>> val) {
        this.defaultAnimations = val;
    }

    public void setStunAnimations(Map<StunType, AnimationManager.AnimationAccessor<? extends StaticAnimation>> val) {
        this.stunAnimations = val;
    }

    public void setAttributeValues(Map<Attribute, Double> val) {
        this.attributeValues = val;
    }

    public void setFaction(Faction val) {
        this.faction = val;
    }

    public void setChasingSpeed(double val) {
        this.chasingSpeed = val;
    }

    public void setScale(float val) {
        this.scale = val;
    }

    public void setStunEvent(List<LivingEntityPatchEvent.StunEvent> val) {
        this.stunEvent = val;
    }


    public EntityPatch<?> get(Entity entity) {
        return new AdvNpcPatch<>(this.faction, this);
    }

    @Override
    public void setArmature(Armature armature) {
        this.armature = (HumanoidArmature) armature;
    }

    public void setHasBossBar(boolean val){
        this.hasBossBar = val;
    }
    public void setName(String val){
        this.name = val;
    }

    public void setBossBar(ResourceLocation val){
        this.bossBar = val;
    }

    public void setAttackRadius(float val){
        this.attackRadius = val;
    }

    public void setSwingSound(SoundEvent event){
        this.swingSound = event;
    }

    public void setHitSound(SoundEvent event){
        this.hitSound = event;
    }

    public void setHitParticle(HitParticleType type){
        this.hitParticle = type;
    }
}
