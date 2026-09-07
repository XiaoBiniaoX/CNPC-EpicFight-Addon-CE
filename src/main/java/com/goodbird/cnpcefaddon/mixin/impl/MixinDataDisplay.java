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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.EntityPatch;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.provider.EntityPatchProvider;
import yesman.epicfight.world.entity.ai.attribute.EpicFightAttributeSupplier;

@Mixin(value = DataDisplay.class, priority = 1001)
public class MixinDataDisplay implements IDataDisplay {
    private static final Logger LOGGER = LoggerFactory.getLogger(MixinDataDisplay.class);
    @Shadow(remap = false)
    EntityNPCInterface npc;
    @Unique
    private ResourceLocation cNPC_EpicFight_Addon$efModelResLoc = null;
    @Unique
    private boolean cNPC_EpicFight_Addon$capApplied = false;
    @Unique
    private String cNPC_EpicFight_Addon$ysmModel = null;

    @Inject(method = "save", at = @At("HEAD"), remap = false)
    public void writeToNBT(CompoundTag nbttagcompound, CallbackInfoReturnable<CompoundTag> cir) {
        if (hasEFModel())
            nbttagcompound.putString("efModel", cNPC_EpicFight_Addon$efModelResLoc.toString());
        if (hasYsmModel())
            nbttagcompound.putString("cnpcefYsmModel", cNPC_EpicFight_Addon$ysmModel);
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
                    LOGGER.error("[cnpcefaddon] updateModelCap failed", e);
                }
                if (changed && npc.isKilled()) {
                    LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(npc, LivingEntityPatch.class);
                    if (patch != null)
                        patch.onDeath(new LivingDeathEvent(npc, npc.damageSources().generic()));
                }
            }
        }
        if (nbttagcompound.contains("cnpcefYsmModel")) {
            String newModel = nbttagcompound.getString("cnpcefYsmModel");
            cNPC_EpicFight_Addon$ysmModel = newModel.isEmpty() ? null : newModel;
        }
    }

    @Override
    public void setEFModel(ResourceLocation modelPath, boolean server) {
        // 这是 GUI 切换 efModel 的真实入口。旧 patch 若为 CE，其 CEBossEvent/BGM
        // 不在此处清理就会残留 —— 用户报告「切走后 BOSS 血条依然存在」的第一嫌疑。
        LivingEntityPatch<?> before = EpicFightCapabilities.getEntityPatch(npc, LivingEntityPatch.class);
        cNPC_EpicFight_Addon$efModelResLoc = modelPath;
        cNPC_EpicFight_Addon$capApplied = false;
        if (server) {
            cNPC_EpicFight_Addon$updateModelCap();
            cNPC_EpicFight_Addon$capApplied = true;
            npc.updateClient();
        }
        LivingEntityPatch<?> after = EpicFightCapabilities.getEntityPatch(npc, LivingEntityPatch.class);
    }

    @Unique
    public ResourceLocation getEFModel() {
        return cNPC_EpicFight_Addon$efModelResLoc;
    }

    @Unique
    public boolean hasEFModel() {
        return cNPC_EpicFight_Addon$efModelResLoc != null;
    }

    @Override
    public void setYsmModel(String modelPath, boolean server) {
        cNPC_EpicFight_Addon$ysmModel = (modelPath == null || modelPath.isEmpty()) ? null : modelPath;
        if (server) {
            npc.updateClient();
        }
    }

    @Unique
    public String getYsmModel() {
        return cNPC_EpicFight_Addon$ysmModel;
    }

    @Unique
    public boolean hasYsmModel() {
        return cNPC_EpicFight_Addon$ysmModel != null && !cNPC_EpicFight_Addon$ysmModel.isEmpty();
    }

    @Override
    public void refreshEFModel() {
        LivingEntityPatch<?> before = EpicFightCapabilities.getEntityPatch(npc, LivingEntityPatch.class);
        if (!hasEFModel()) {
            return;
        }
        if (before instanceof com.goodbird.cnpcefaddon.common.patch.CeNpcPatch cePatch) {
            cePatch.clearReloadState();
        }
        // Force a new provider lookup. Reusing the old patch also reuses its CE BossBar,
        // BGM packet UUID, and parsed behavior provider after /reload.
        cNPC_EpicFight_Addon$capApplied = false;
        cNPC_EpicFight_Addon$updateModelCap();
        cNPC_EpicFight_Addon$capApplied = true;
        LivingEntityPatch<?> after = EpicFightCapabilities.getEntityPatch(npc, LivingEntityPatch.class);
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
        // 复用现有 patch 之前必须确认它仍与当前 efModel 匹配。
        // 踩坑第 44 条：旧实现只判 existing != null，于是切换 efModel 后服务端永远命中
        // EXISTING → patch 永不重建 → 切到 CE 数据包时服务端仍是 NpcHumanoidPatch（无 CE 效果），
        // 切走时又残留 CeNpcPatch 的 visible BossBar 与 BGM 发包。双端日志已铁证。
        // EntityPatchProvider 构造无副作用（仅查表取实例，get() 纯返回字段），可安全预构造用于比类型。
        EntityPatchProvider expectedProvider = new EntityPatchProvider(npc);
        Object expected = expectedProvider.get();
        boolean typeMatches = existing != null && expected != null
                && existing.getClass() == expected.getClass();
        // 类型相同还不够：同一 patch 类可对应任意多份数据包 provider（5 份 CE 测试包全是
        // CeNpcPatch）。ce_test_01 → ce_test_05 切换实测被旧判据放过，服务端仍持 01 的
        // provider（无 BossBar/BGM），而客户端已重建为 05（visible=true）→ 双端不一致。
        boolean providerMatches = typeMatches;
        if (typeMatches
                && existing instanceof com.goodbird.cnpcefaddon.common.patch.CeNpcPatch currentCe
                && expected instanceof com.goodbird.cnpcefaddon.common.patch.CeNpcPatch expectedCe) {
            providerMatches = currentCe.getNpcProvider() == expectedCe.getNpcProvider();
        }
        if (existing != null && cNPC_EpicFight_Addon$capApplied && !providerMatches) {
        }
        if (existing != null && cNPC_EpicFight_Addon$capApplied && providerMatches) {
            if (existing instanceof HumanoidMobPatch<?> humanoid) {
                humanoid.setAIAsInfantry(npc.getMainHandItem().getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem);
            }
            // CE patch 不是 HumanoidMobPatch 的子类，其行为树 Goal 只在 initAI 内挂载。
            if (existing instanceof com.goodbird.cnpcefaddon.common.patch.CeNpcPatch cePatch) {
                cePatch.initAI();
            }
            if (npc.level().isClientSide() && existing instanceof NpcHumanoidPatch<?> npcPatch) {
                npcPatch.applyWeaponLivingMotions();
            }
            if (npc.level().isClientSide() && existing instanceof com.goodbird.cnpcefaddon.common.patch.CeNpcPatch cePatch) {
                cePatch.applyCeLivingMotions();
            }
            return;
        }

        // 重建前清掉旧 CE patch 的 BossBar 玩家与 BGM，否则换型后旧血条/旧音乐仍挂在客户端。
        if (existing instanceof com.goodbird.cnpcefaddon.common.patch.CeNpcPatch oldCePatch) {
            oldCePatch.clearReloadState();
        }

        EntityPatchProvider newProvider = expectedProvider;
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
        // CNPC swaps this capability after the entity already joined its level. CE puts its
        // datapack attributes (staminar/stamina_regen included) in onAddedToWorld(), so the
        // normal Forge lifecycle has already passed and must be replayed for CE only.
        if (newProvider.get() instanceof com.goodbird.cnpcefaddon.common.patch.CeNpcPatch cePatch) {
            cePatch.onAddedToWorld();
            // CE 的行为树 Goal 只在 initAI 内挂载，且必须在属性注入之后：
            // getCustomWeaponMotionBuilder 依赖手持武器 capability 与已注入的属性。
            // 缺这一步 NPC 没有 CEAnimationAttackGoal，既不出刀光也不造成伤害。
            cePatch.initAI();
        }
        if (npc.level().isClientSide() && newProvider.get() instanceof NpcHumanoidPatch<?> npcPatch) {
            npcPatch.applyWeaponLivingMotions();
        }
        if (npc.level().isClientSide() && newProvider.get() instanceof com.goodbird.cnpcefaddon.common.patch.CeNpcPatch cePatch) {
            cePatch.applyCeLivingMotions();
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
