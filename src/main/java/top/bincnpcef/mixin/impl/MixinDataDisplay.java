package top.bincnpcef.mixin.impl;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bincnpcef.common.NpcPatchReloadListener;
import top.bincnpcef.common.NpcCombatParameters;
import top.bincnpcef.common.CnpcBranchPatchProvider;
import top.bincnpcef.mixin.IAttachmentEntityPatchProvider;
import top.bincnpcef.api.IDataDisplay;
import yesman.epicfight.registry.entries.EpicFightAttachmentTypes;
import yesman.epicfight.world.capabilities.entitypatch.EntityPatch;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;
import yesman.epicfight.world.capabilities.provider.AttachmentEntityPatchProvider;

@Mixin(value = DataDisplay.class, priority = 1001)
public class MixinDataDisplay implements IDataDisplay {
    @Shadow(remap = false)
    EntityNPCInterface npc;

    @Unique
    private ResourceLocation cnpcef$efModel = null;

    @Unique private float cnpcef$animSpeed = NpcCombatParameters.DEFAULT_SPEED;
    @Unique private ResourceLocation cnpcef$boundModel;
    @Unique private CnpcBranchPatchProvider cnpcef$boundProvider;

    @Inject(method = "save", at = @At("HEAD"), remap = false)
    private void cnpcef$onSave(CompoundTag nbt, CallbackInfoReturnable<CompoundTag> cir) {
        if (cnpcef$efModel != null) {
            nbt.putString("efModel", cnpcef$efModel.toString());
        }
        nbt.putFloat(NpcCombatParameters.SPEED_KEY, cnpcef$animSpeed);
    }

    @Inject(method = "readToNBT", at = @At("HEAD"), remap = false)
    private void cnpcef$onReadToNBT(CompoundTag nbt, CallbackInfo ci) {
        cnpcef$setAnimSpeed(NpcCombatParameters.read(nbt, NpcCombatParameters.SPEED_KEY,
                NpcCombatParameters.DEFAULT_SPEED));
        if (nbt.contains("efModel")) {
            cnpcef$efModel = ResourceLocation.parse(nbt.getString("efModel"));
            cnpcef$updateModelCap();
        }
    }

    @Override
    public ResourceLocation cnpcef$getEFModel() {
        return cnpcef$efModel;
    }

    @Override
    public void cnpcef$setEFModel(ResourceLocation model) {
        cnpcef$efModel = model;
        cnpcef$updateModelCap();
        // 不调用 npc.updateClient()——该方法是服务端专用（内部调 PacketDistributor.sendToPlayersTrackingEntity），
        // 在客户端 GUI 回调中调用会崩溃。
        // efModel 的持久化和同步由 CNPC 的 Save 流程自动处理：
        //   1. 用户点 Save → DataDisplay.save() → 本 Mixin 的 cnpcef$onSave 写入 efModel 到 NBT
        //   2. CNPC 发 SPacketMenuSave 到服务端 → 服务端 readToNBT() → 本 Mixin 的 cnpcef$onReadToNBT 读取 efModel
        //   3. 服务端 npc.updateClient=true → 下一 tick aiStep() 中 updateClient() 广播给客户端
    }

    @Override
    public boolean cnpcef$hasEFModel() {
        return cnpcef$efModel != null;
    }

    @Override
    public float cnpcef$getAnimSpeed() {
        return cnpcef$animSpeed;
    }

    @Override
    public void cnpcef$setAnimSpeed(float value) {
        cnpcef$animSpeed = NpcCombatParameters.sanitizeSpeed(value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Unique
    private void cnpcef$updateModelCap() {
        try {
            AttachmentEntityPatchProvider provider = npc.getData(EpicFightAttachmentTypes.ENTITY_PATCH);
            if (provider == null) {
                return;
            }
            // CNPC 每次外观/追踪同步都会读 DataDisplay。相同模型与重载代际无需重建
            // Animator/AI/armature，否则动作被打断，且重复分配与装配随 NPC 数量放大。
            if (java.util.Objects.equals(cnpcef$efModel, cnpcef$boundModel)
                    && cnpcef$boundProvider == NpcPatchReloadListener.branchPatchProvider
                    && provider.getCapability() != null) {
                return;
            }
            EntityPatch<?> patch = NpcPatchReloadListener.branchPatchProvider.get(npc);
            ((IAttachmentEntityPatchProvider) (Object) provider).cnpcef$setEntityPatch(patch);
            if (patch != null) {
                ((EntityPatch) patch).onConstructed(npc);
                // onJoinWorld 只在 NPC 已在世界中时调用（GUI 修改 efModel 的场景）
                // 首次从 NBT 加载时由 EF 事件钩子自动调用
                if (npc.level() != null && npc.isAlive() && npc.tickCount > 0) {
                    ((EntityPatch) patch).onJoinWorld(npc, npc.level(), false);
                }
                // 客户端 patch 需要主动应用武器动画。服务端在 addRegularEntries 中通过
                // MixinEntityNpcInterface.cnpcef$setEFCombatAI 调用 modifyLivingMotionByCurrentItem，
                // 但客户端不执行该路径。退出重进时若不在此处应用，客户端会退化为空手站姿。
                if (npc.level() != null && npc.level().isClientSide() && patch instanceof HumanoidMobPatch<?> humanoidPatch) {
                    try {
                        humanoidPatch.modifyLivingMotionByCurrentItem(true);
                    } catch (Exception ignored) {
                    }
                }
            }
            cnpcef$boundModel = cnpcef$efModel;
            cnpcef$boundProvider = NpcPatchReloadListener.branchPatchProvider;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
