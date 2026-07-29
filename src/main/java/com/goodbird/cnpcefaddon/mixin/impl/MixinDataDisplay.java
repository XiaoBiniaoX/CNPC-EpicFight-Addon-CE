package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.common.CapabilityCacheRefresher;
import com.goodbird.cnpcefaddon.common.patch.NpcHumanoidPatch;
import com.goodbird.cnpcefaddon.mixin.IAttributeMap;
import com.goodbird.cnpcefaddon.mixin.IDataDisplay;
import com.goodbird.cnpcefaddon.mixin.IMixinCapabilityDispatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.EntityPatch;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.provider.EntityPatchProvider;
import yesman.epicfight.world.entity.ai.attribute.EpicFightAttributeSupplier;

@Mixin(value = DataDisplay.class, priority = 1001)
public class MixinDataDisplay implements IDataDisplay {
    @Shadow(remap = false)
    EntityNPCInterface npc;
    @Unique
    private ResourceLocation cNPC_EpicFight_Addon$efModelResLoc = null;
    @Unique
    private boolean cNPC_EpicFight_Addon$capApplied = false;

    @Inject(method = "save", at = @At("HEAD"), remap = false)
    public void writeToNBT(CompoundTag nbttagcompound, CallbackInfoReturnable<CompoundTag> cir) {
        if (hasEFModel())
            nbttagcompound.putString("efModel", cNPC_EpicFight_Addon$efModelResLoc.toString());
    }

    @Inject(method = "readToNBT", at = @At("HEAD"), remap = false)
    public void readFromNBT(CompoundTag nbttagcompound, CallbackInfo ci) {
        if (nbttagcompound.contains("efModel")) {
            ResourceLocation newModel = ResourceLocation.parse(nbttagcompound.getString("efModel"));
            boolean changed = cNPC_EpicFight_Addon$efModelResLoc == null || !cNPC_EpicFight_Addon$efModelResLoc.equals(newModel);
            cNPC_EpicFight_Addon$efModelResLoc = newModel;
            if (changed || !cNPC_EpicFight_Addon$capApplied) {
                try {
                    cNPC_EpicFight_Addon$updateModelCap();
                    cNPC_EpicFight_Addon$capApplied = true;
                } catch (Exception e) {
                    System.err.println("[CNPCEF] updateModelCap failed: " + e.getMessage());
                }
                if (changed && npc.isKilled()) {
                    LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(npc, LivingEntityPatch.class);
                    if (patch != null)
                        patch.onDeath(new LivingDeathEvent(npc, npc.damageSources().generic()));
                }
            }
        }
    }

    @Override
    public void setEFModel(ResourceLocation modelPath, boolean server) {
        cNPC_EpicFight_Addon$efModelResLoc = modelPath;
        cNPC_EpicFight_Addon$capApplied = false;
        if (server) {
            cNPC_EpicFight_Addon$updateModelCap();
            cNPC_EpicFight_Addon$capApplied = true;
            npc.updateClient();
        }
    }

    @Unique
    public ResourceLocation getEFModel() {
        return cNPC_EpicFight_Addon$efModelResLoc;
    }

    @Unique
    public boolean hasEFModel() {
        return cNPC_EpicFight_Addon$efModelResLoc != null;
    }

    @Unique
    private void cNPC_EpicFight_Addon$updateModelCap() {
        CapabilityDispatcher dispatcher = ((MixinCapabilityProvider) npc).invokeGetCapabilities();
        if (dispatcher == null) {
            ((MixinCapabilityProvider) npc).invokeGatherCapabilities();
            dispatcher = ((MixinCapabilityProvider) npc).invokeGetCapabilities();
            if (dispatcher == null) return;
        }
        ICapabilityProvider[] caps = ((IMixinCapabilityDispatcher) (Object) dispatcher).getCaps();

        LivingEntityPatch<?> existing = EpicFightCapabilities.getEntityPatch(npc, LivingEntityPatch.class);
        if (existing != null && cNPC_EpicFight_Addon$capApplied) {
            if (existing instanceof HumanoidMobPatch<?> humanoid) {
                humanoid.setAIAsInfantry(npc.getMainHandItem().getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem);
            }
            if (npc.level().isClientSide() && existing instanceof NpcHumanoidPatch<?> npcPatch) {
                npcPatch.applyWeaponLivingMotions();
            }
            return;
        }

        EntityPatchProvider newProvider = new EntityPatchProvider(npc);
        if (newProvider.get() == null) return;
        ((IAttributeMap) npc.getAttributes()).setSupplier(new EpicFightAttributeSupplier(((IAttributeMap) npc.getAttributes()).getSupplier()));
        try {
            ((EntityPatch) newProvider.get()).onConstructed(npc);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() == null || !e.getMessage().contains("Duplicate id")) {
                throw e;
            }
        }
        ((EntityPatch) newProvider.get()).onJoinWorld(npc, new EntityJoinLevelEvent(npc, npc.level()));
        if (npc.level().isClientSide() && newProvider.get() instanceof NpcHumanoidPatch<?> npcPatch) {
            npcPatch.applyWeaponLivingMotions();
        }
        if (newProvider.hasCapability()) {
            boolean hasFoundAny = false;
            for (int i = 0; i < caps.length; i++) {
                if (caps[i] instanceof EntityPatchProvider) {
                    caps[i] = newProvider;
                    hasFoundAny = true;
                    break;
                }
            }
            if (!hasFoundAny) {
                ICapabilityProvider[] newCaps = new ICapabilityProvider[caps.length + 1];
                System.arraycopy(caps, 0, newCaps, 0, caps.length);
                newCaps[caps.length] = newProvider;
                ((IMixinCapabilityDispatcher) (Object) dispatcher).setCaps(newCaps);
                caps = newCaps;
            }
            CapabilityCacheRefresher.refresh(dispatcher, caps);
        }
    }
}
