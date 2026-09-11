package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.common.AnimSpeedFactor;
import com.goodbird.cnpcefaddon.common.CapabilityCacheRefresher;
import com.goodbird.cnpcefaddon.common.CeNpcPatchOptional;
import com.goodbird.cnpcefaddon.common.patch.INpcPatch;
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
    @Unique
    private float cNPC_EpicFight_Addon$animSpeedFactor = AnimSpeedFactor.DEFAULT;

    @Inject(method = "save", at = @At("HEAD"), remap = false)
    public void writeToNBT(CompoundTag nbttagcompound, CallbackInfoReturnable<CompoundTag> cir) {
        if (hasEFModel())
            nbttagcompound.putString("efModel", cNPC_EpicFight_Addon$efModelResLoc.toString());
        if (hasYsmModel())
            nbttagcompound.putString("cnpcefYsmModel", cNPC_EpicFight_Addon$ysmModel);
        // 默认值不写盘：旧世界与未调过此项的 NPC 保持原样，NBT 不因本功能增大。
        if (cNPC_EpicFight_Addon$animSpeedFactor != AnimSpeedFactor.DEFAULT)
            nbttagcompound.putFloat(AnimSpeedFactor.NBT_KEY, cNPC_EpicFight_Addon$animSpeedFactor);
    }

    @Inject(method = "readToNBT", at = @At("HEAD"), remap = false)
    public void readFromNBT(CompoundTag nbttagcompound, CallbackInfo ci) {
        if (nbttagcompound.contains("efModel")) {
            ResourceLocation newModel = ResourceLocation.parse(nbttagcompound.getString("efModel"));
            boolean changed = cNPC_EpicFight_Addon$efModelResLoc == null || !cNPC_EpicFight_Addon$efModelResLoc.equals(newModel);
            cNPC_EpicFight_Addon$efModelResLoc = newModel;
            if (changed || !cNPC_EpicFight_Addon$capApplied) {
                try {
                    cNPC_EpicFight_Addon$capApplied = cNPC_EpicFight_Addon$updateModelCap();
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
        // 缺字段 = 旧世界/旧 NBT，回落默认 1.0（约法第 9 条）。
        // 读取走 AnimSpeedFactor.read：字符串型 NBT 与 NaN/无穷都会被挡掉，
        // 否则 getFloat 会静默返回 0.0 并让动画彻底冻结（踩坑第 9 条）。
        cNPC_EpicFight_Addon$animSpeedFactor = AnimSpeedFactor.read(nbttagcompound);
    }

    /** GUI 切换 efModel 的入口；服务端侧立即重建 capability 并同步客户端。 */
    @Override
    public void setEFModel(ResourceLocation modelPath, boolean server) {
        cNPC_EpicFight_Addon$efModelResLoc = modelPath;
        cNPC_EpicFight_Addon$capApplied = false;
        if (server) {
            cNPC_EpicFight_Addon$capApplied = cNPC_EpicFight_Addon$updateModelCap();
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

    @Unique
    public float getAnimSpeedFactor() {
        return cNPC_EpicFight_Addon$animSpeedFactor;
    }

    @Override
    public void setAnimSpeedFactor(float factor) {
        cNPC_EpicFight_Addon$animSpeedFactor = AnimSpeedFactor.sanitize(factor);
    }

    @Override
    public void refreshEFModel() {
        LivingEntityPatch<?> before = EpicFightCapabilities.getEntityPatch(npc, LivingEntityPatch.class);
        if (!hasEFModel()) {
            return;
        }
        CeNpcPatchOptional.invokeOnCePatch(before, "clearReloadState");
        // Force a new provider lookup. Reusing the old patch also reuses its CE BossBar,
        // BGM packet UUID, and parsed behavior provider after /reload.
        cNPC_EpicFight_Addon$capApplied = false;
        // capApplied 只有在 capability 真的换成新 provider 后才能置真：
        // 旧实现在 updateModelCap 的两个提前 return 分支（dispatcher 为空、provider.get() 为 null）
        // 之后仍无条件置真，于是「没换成」被标记为「已换成」，后续复用判据据此放过错误的旧 patch。
        cNPC_EpicFight_Addon$capApplied = cNPC_EpicFight_Addon$updateModelCap();
    }

    /**
     * @return 是否确实完成了 capability 提交（复用现有 patch 也算成功）。
     *         返回 {@code false} 表示什么都没换，调用方不得把状态标记为已应用。
     */
    @Unique
    private boolean cNPC_EpicFight_Addon$updateModelCap() {
        CapabilityDispatcher dispatcher = ((MixinCapabilityProvider) npc).invokeGetCapabilities();
        if (dispatcher == null) {
            ((MixinCapabilityProvider) npc).invokeGatherCapabilities();
            dispatcher = ((MixinCapabilityProvider) npc).invokeGetCapabilities();
            if (dispatcher == null) return false;
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
        // CE 侧用无链接门面反射比 provider（不解析 CE 类型）；其余口味走 INpcPatch 的
        // getPatchProviderIdentity()。旧实现只有 CE 走深比较，普通/高级 patch 仍只比类，
        // 于是「同一 patch 类、不同数据包 provider」的切换会被当成可复用而放过。
        boolean providerMatches = typeMatches;
        if (typeMatches) {
            if (CeNpcPatchOptional.isCePatch(existing)) {
                providerMatches = CeNpcPatchOptional.sameCeProvider(existing, expected);
            } else if (existing instanceof INpcPatch existingNpc && expected instanceof INpcPatch expectedNpc) {
                Object existingId = existingNpc.getPatchProviderIdentity();
                Object expectedId = expectedNpc.getPatchProviderIdentity();
                providerMatches = existingId != null && existingId == expectedId;
            }
        }
        if (existing != null && cNPC_EpicFight_Addon$capApplied && providerMatches) {
            if (existing instanceof HumanoidMobPatch<?> humanoid) {
                humanoid.setAIAsInfantry(npc.getMainHandItem().getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem);
            }
            // CE patch 不是 HumanoidMobPatch 的子类，其行为树 Goal 只在 initAI 内挂载。
            CeNpcPatchOptional.invokeOnCePatch(existing, "initAI");
            if (npc.level().isClientSide() && existing instanceof NpcHumanoidPatch<?> npcPatch) {
                npcPatch.applyWeaponLivingMotions();
            }
            if (npc.level().isClientSide()) {
                CeNpcPatchOptional.invokeOnCePatch(existing, "applyCeLivingMotions");
            }
            return true;
        }

        EntityPatchProvider newProvider = expectedProvider;
        // 先确认新 provider 可用，再动旧状态：旧实现先 clearReloadState 清掉旧 CE 的
        // BossBar/BGM，然后才判 newProvider.get() == null 并 return，于是查不到新 patch 时
        // 旧血条与旧音乐已经被清掉、capability 却还是旧的，NPC 停在半清理状态。
        if (newProvider.get() == null) return false;

        // 重建前清掉旧 CE patch 的 BossBar 玩家与 BGM，否则换型后旧血条/旧音乐仍挂在客户端。
        CeNpcPatchOptional.invokeOnCePatch(existing, "clearReloadState");

        ((IAttributeMap) npc.getAttributes()).setSupplier(new EpicFightAttributeSupplier(((IAttributeMap) npc.getAttributes()).getSupplier()));
        try {
            ((EntityPatch) newProvider.get()).onConstructed(npc);
        } catch (IllegalArgumentException e) {
            // EF / 坚不可摧的 SynchedEntityData 重复注册只抛这一种异常且没有专用类型或错误码，
            // 只能按消息文本识别（同一手法已在 AdvNpcPatch:46）。非该消息一律上抛，
            // 由调用方的刷新循环记录，不静默继续。
            if (e.getMessage() == null || !e.getMessage().contains("Duplicate id")) {
                throw e;
            }
        }
        ((EntityPatch) newProvider.get()).onJoinWorld(npc, new EntityJoinLevelEvent(npc, npc.level()));
        // CNPC swaps this capability after the entity already joined its level. CE puts its
        // datapack attributes (staminar/stamina_regen included) in onAddedToWorld(), so the
        // normal Forge lifecycle has already passed and must be replayed for CE only.
        CeNpcPatchOptional.invokeOnCePatch(newProvider.get(), "onAddedToWorld");
        // CE 的行为树 Goal 只在 initAI 内挂载，且必须在属性注入之后：
        // getCustomWeaponMotionBuilder 依赖手持武器 capability 与已注入的属性。
        // 缺这一步 NPC 没有 CEAnimationAttackGoal，既不出刀光也不造成伤害。
        CeNpcPatchOptional.invokeOnCePatch(newProvider.get(), "initAI");
        if (npc.level().isClientSide() && newProvider.get() instanceof NpcHumanoidPatch<?> npcPatch) {
            npcPatch.applyWeaponLivingMotions();
        }
        if (npc.level().isClientSide()) {
            CeNpcPatchOptional.invokeOnCePatch(newProvider.get(), "applyCeLivingMotions");
        }
        if (!newProvider.hasCapability()) {
            // 没有可挂载的 capability，等于什么都没提交，不能标记为已应用。
            return false;
        }

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
        return true;
    }
}
