package com.goodbird.cnpcefaddon.common;

import com.goodbird.cnpcefaddon.client.render.RenderStorage;
import com.goodbird.cnpcefaddon.common.patch.INpcPatch;
import com.goodbird.cnpcefaddon.common.provider.AdvNpcPatchProvider;
import com.goodbird.cnpcefaddon.common.provider.INpcPatchProvider;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.nameless.indestructible.api.animation.types.LivingEntityPatchEvent;
import com.nameless.indestructible.data.AdvancedMobpatchReloader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.registries.ForgeRegistries;
import noppes.npcs.CustomEntities;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.model.Meshes;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.data.reloader.MobPatchReloadListener;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.client.mesh.HumanoidMesh;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.main.EpicFightMod;
import yesman.epicfight.main.EpicFightSharedConstants;
import yesman.epicfight.model.armature.HumanoidArmature;
import yesman.epicfight.particle.HitParticleType;
import yesman.epicfight.world.capabilities.entitypatch.Faction;
import yesman.epicfight.world.capabilities.provider.EntityPatchProvider;
import yesman.epicfight.world.damagesource.StunType;

import com.mojang.datafixers.util.Pair;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;

public class AdvNpcPatchReloader  extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = (new GsonBuilder()).create();
    private static final Logger LOGGER = LoggerFactory.getLogger(AdvNpcPatchReloader.class);

    public AdvNpcPatchReloader() {
        super(GSON, "adv_npc_epicfight_mobpatch");
    }

    protected void apply(Map<ResourceLocation, JsonElement> objectIn, ResourceManager resourceManagerIn, ProfilerFiller profilerIn) {
        LOGGER.info("AdvNpcPatchReloader.apply() start - objectIn size: {}", objectIn.size());

        List<Pair<ResLocPredicate, MobPatchReloadListener.AbstractMobPatchProvider>> tempProviders = Lists.newArrayList();
        Set<ResourceLocation> tempModels = new HashSet<>();
        Map<ResourceLocation, CompoundTag> tempTags = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : objectIn.entrySet()) {
            CompoundTag tag = null;
            try {
                tag = TagParser.parseTag((entry.getValue()).toString());
            } catch (CommandSyntaxException e) {
                LOGGER.error("Failed to parse Adv NPC EpicFight mobpatch data for {}: {}", entry.getKey(), e.getMessage());
                NpcPatchReloadListener.loadErrors.put(entry.getKey(), e.getMessage());
            }
            if (tag != null) {
                try {
                    AdvNpcPatchProvider provider = deserializeMobPatchProvider(resourceManagerIn, tag, false);
                    CompoundTag filteredTag = MobPatchReloadListener.filterClientData(tag);
                    filteredTag.putString("patchType", "ADVANCED");
                    filteredTag.putString("id", entry.getKey().toString());
                    if (EpicFightSharedConstants.isPhysicalClient())
                        RenderStorage.registerRenderer(entry.getKey(), tag.contains("preset") ? tag.getString("preset") : tag.getString("renderer"), tag);
                    tempProviders.add(new Pair<>(new ResLocPredicate(entry.getKey()), provider));
                    tempModels.add(entry.getKey());
                    tempTags.put(entry.getKey(), filteredTag);
                } catch (Exception e) {
                    LOGGER.error("Failed to load Adv NPC EpicFight mobpatch for {}: {}", entry.getKey(), e.getMessage());
                    NpcPatchReloadListener.loadErrors.put(entry.getKey(), e.getMessage());
                }
            }
        }

        ResourceLocation samuraiKey = ResourceLocation.parse("customnpcs:samurai");
        if (!tempModels.contains(samuraiKey)) {
            LOGGER.warn("Built-in adv model {} not loaded from datapack, attempting fallback", samuraiKey);
            try {
                ResourceLocation filePath = ResourceLocation.fromNamespaceAndPath(
                    samuraiKey.getNamespace(), "adv_npc_epicfight_mobpatch/" + samuraiKey.getPath() + ".json");
                List<Resource> stack = resourceManagerIn.getResourceStack(filePath);
                for (int i = stack.size() - 1; i >= 0; i--) {
                    try (Reader reader = stack.get(i).openAsReader()) {
                        JsonElement element = GsonHelper.fromJson(GSON, reader, JsonElement.class);
                        CompoundTag tag = TagParser.parseTag(element.toString());
                        AdvNpcPatchProvider provider = deserializeMobPatchProvider(resourceManagerIn, tag, false);
                        CompoundTag filteredTag = MobPatchReloadListener.filterClientData(tag);
                        filteredTag.putString("patchType", "ADVANCED");
                        filteredTag.putString("id", samuraiKey.toString());
                        if (EpicFightSharedConstants.isPhysicalClient())
                            RenderStorage.registerRenderer(samuraiKey, tag.contains("preset") ? tag.getString("preset") : tag.getString("renderer"), tag);
                        tempProviders.add(new Pair<>(new ResLocPredicate(samuraiKey), provider));
                        tempModels.add(samuraiKey);
                        tempTags.put(samuraiKey, filteredTag);
                        LOGGER.info("Built-in adv model {} loaded from fallback", samuraiKey);
                        break;
                    } catch (Exception e) {
                        LOGGER.warn("Fallback attempt for {} failed: {}", samuraiKey, e.getMessage());
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to find any resource for built-in adv model {}: {}", samuraiKey, e.getMessage());
            }
        }

        for (var p : tempProviders) {
            NpcPatchReloadListener.branchPatchProvider.addProvider(p.getFirst().resourceLocation, p.getSecond());
        }
        NpcPatchReloadListener.AVAILABLE_MODELS.addAll(tempModels);
        NpcPatchReloadListener.TAGMAP.putAll(tempTags);

        EntityPatchProvider.putCustomEntityPatch(CustomEntities.entityCustomNpc, entity -> ()->NpcPatchReloadListener.branchPatchProvider.get(entity));
        LOGGER.info("AdvNpcPatchReloader.apply() end - added {} entries, AVAILABLE_MODELS now: {}",
            tempModels.size(), NpcPatchReloadListener.AVAILABLE_MODELS);
    }

    public static AdvNpcPatchProvider deserializeMobPatchProvider(ResourceManager resourceManagerIn, CompoundTag tag, boolean clientSide) {
        AdvNpcPatchProvider provider = new AdvNpcPatchProvider();
        provider.setAttributeValues(AdvancedMobpatchReloader.deserializeAdvancedAttributes(tag.getCompound("attributes")));
        ResourceLocation modelLocation = ResourceLocation.parse(tag.getString("model"));
        ResourceLocation armatureLocation = ResourceLocation.parse(tag.getString("armature"));
        if (EpicFightSharedConstants.isPhysicalClient()) {
            Meshes.getOrCreate(modelLocation, (jsonAssetLoader) -> jsonAssetLoader.loadSkinnedMesh(SkinnedMesh::new));
            provider.setHasBossBar(tag.contains("boss_bar") && tag.getBoolean("boss_bar"));
            provider.setName(tag.contains("boss_bar") && tag.contains("custom_name") ? tag.getString("custom_name") : null);
        }

        AssetAccessor<Armature> armature = Armatures.getOrCreate(armatureLocation, HumanoidArmature::new);
        ((INpcPatchProvider)provider).setArmature(armature.get());

        provider.setBossBar(tag.contains("boss_bar") && tag.contains("custom_texture") ? ResourceLocation.tryParse(tag.getString("custom_texture")) : null);
        provider.setDefaultAnimations(MobPatchReloadListener.deserializeDefaultAnimations(tag.getCompound("default_livingmotions")));
        provider.setFaction(Faction.ENUM_MANAGER.getOrThrow(tag.getString("faction")));
        provider.setScale(tag.getCompound("attributes").contains("scale") ? (float)tag.getCompound("attributes").getDouble("scale") : 1.0F);
        provider.setMaxStunShield(tag.getCompound("attributes").contains("max_stun_shield") ? (float)tag.getCompound("attributes").getDouble("max_stun_shield") : 0.0F);
        if (tag.contains("swing_sound"))
            provider.setSwingSound(ForgeRegistries.SOUND_EVENTS.getValue(ResourceLocation.parse(tag.getString("swing_sound"))));
        if (tag.contains("hit_sound"))
            provider.setHitSound(ForgeRegistries.SOUND_EVENTS.getValue(ResourceLocation.parse(tag.getString("hit_sound"))));
        if (tag.contains("hit_particle"))
            provider.setHitParticle((HitParticleType)ForgeRegistries.PARTICLE_TYPES.getValue(ResourceLocation.parse(tag.getString("hit_particle"))));
        if (!clientSide) {
            provider.setStunAnimations(MobPatchReloadListener.deserializeStunAnimations(tag.getCompound("stun_animations")));
            provider.setChasingSpeed(tag.getCompound("attributes").getDouble("chasing_speed"));
            provider.setAHCombatBehaviors(AdvancedMobpatchReloader.deserializeAdvancedHumanoidCombatBehaviors(tag.getList("combat_behavior", 10)));
            provider.setAHWeaponMotions(MobPatchReloadListener.deserializeHumanoidWeaponMotions(tag.getList("humanoid_weapon_motions", 10)));
            provider.setGuardMotions(AdvancedMobpatchReloader.deserializeHumanoidGuardMotions(tag.getList("custom_guard_motion", 10)));
            provider.setRegenStaminaStandbyTime(tag.getCompound("attributes").contains("stamina_regan_delay") ? tag.getCompound("attributes").getInt("stamina_regan_delay") : 30);
            provider.setHasStunReduction(!tag.getCompound("attributes").contains("has_stun_reduction") || tag.getCompound("attributes").getBoolean("has_stun_reduction"));
            provider.setReganShieldStandbyTime(tag.getCompound("attributes").contains("stun_shield_regan_delay") ? tag.getCompound("attributes").getInt("stun_shield_regan_delay") : 30);
            provider.setReganShieldMultiply(tag.getCompound("attributes").contains("stun_shield_regan_multiply") ? (float)tag.getCompound("attributes").getDouble("stun_shield_multiply") : 1.0F);
            provider.setStaminaLoseMultiply(tag.getCompound("attributes").contains("stamina_lose_multiply") ? (float)tag.getCompound("attributes").getDouble("stamina_lose_multiply") : 0.0F);
            provider.setAttackRadius(tag.getCompound("attributes").contains("attack_radius") ? (float)tag.getCompound("attributes").getDouble("attack_radius") : 1.5F);
            provider.setGuardRadius(tag.getCompound("attributes").contains("guard_radius") ? (float)tag.getCompound("attributes").getDouble("guard_radius") : 3.0F);
            provider.setStunEvent(deserializeStunCommandList(tag.getList("stun_command_list", 10)));
        }

        return provider;
    }

    private static List<LivingEntityPatchEvent.StunEvent> deserializeStunCommandList(ListTag args) {
        List<LivingEntityPatchEvent.StunEvent> list = Lists.newArrayList();

        for(int k = 0; k < args.size(); ++k) {
            CompoundTag command = args.getCompound(k);
            boolean execute_at_target = command.contains("execute_at_target") && command.getBoolean("execute_at_target");
            LivingEntityPatchEvent.StunEvent event = LivingEntityPatchEvent.StunEvent.CreateStunCommandEvent(command.getString("command"), execute_at_target, StunType.valueOf(command.getString("stun_type").toUpperCase(Locale.ROOT)));
            list.add(event);
        }

        return list;
    }
}
