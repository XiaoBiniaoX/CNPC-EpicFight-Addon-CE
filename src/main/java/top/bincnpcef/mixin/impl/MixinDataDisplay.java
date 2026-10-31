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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import yesman.epicfight.world.capabilities.item.CapabilityItem;

@Mixin(value = DataDisplay.class, priority = 1001)
public class MixinDataDisplay implements IDataDisplay {
    @Unique private static final Logger CNPCEF_LOGGER = LoggerFactory.getLogger("CNPC-EF-Addon");
    @Shadow(remap = false)
    EntityNPCInterface npc;

    @Unique
    private ResourceLocation cnpcef$efModel = null;

    @Unique private float cnpcef$animSpeed = NpcCombatParameters.DEFAULT_SPEED;
    @Unique private ResourceLocation cnpcef$boundModel;
    @Unique private CnpcBranchPatchProvider cnpcef$boundProvider;
    @Unique private EntityPatch<?> cnpcef$boundPatch;
    @Unique private EntityPatch<?> cnpcef$weaponPatch;
    @Unique private ItemStack cnpcef$mainhand = ItemStack.EMPTY;
    @Unique private ItemStack cnpcef$offhand = ItemStack.EMPTY;
    @Unique private CapabilityItem cnpcef$mainhandCap;
    @Unique private CapabilityItem cnpcef$offhandCap;

    @Inject(method = "save", at = @At("HEAD"), remap = false)
    private void cnpcef$onSave(CompoundTag nbt, CallbackInfoReturnable<CompoundTag> cir) {
        if (cnpcef$efModel != null) {
            nbt.putString("efModel", cnpcef$efModel.toString());
        }
        nbt.putFloat(NpcCombatParameters.SPEED_KEY, cnpcef$animSpeed);
    }

    @Inject(method = "readToNBT", at = @At("TAIL"), remap = false)
    private void cnpcef$onReadToNBT(CompoundTag nbt, CallbackInfo ci) {
        cnpcef$setAnimSpeed(NpcCombatParameters.read(nbt, NpcCombatParameters.SPEED_KEY,
                NpcCombatParameters.DEFAULT_SPEED));
        if (nbt.contains("efModel")) {
            cnpcef$efModel = ResourceLocation.tryParse(nbt.getString("efModel"));
        }
        cnpcef$updateModelCap();
        cnpcef$updateClientWeaponMotions();
    }

    @Override
    public ResourceLocation cnpcef$getEFModel() {
        return cnpcef$efModel;
    }

    @Override
    public void cnpcef$setEFModel(ResourceLocation model) {
        cnpcef$efModel = model;
        cnpcef$updateModelCap();
        cnpcef$updateClientWeaponMotions();
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

    @Override
    public void cnpcef$refreshModelCap() {
        if (cnpcef$efModel != null && cnpcef$boundProvider != NpcPatchReloadListener.providerFor(npc)) {
            cnpcef$updateModelCap();
        }
    }

    @Unique
    private void cnpcef$updateClientWeaponMotions() {
        // readSpawnData 先装武器再读 display；同模型同步也需刷新站姿，不必重建 Animator。
        if (npc.level().isClientSide() && npc.getData(EpicFightAttachmentTypes.ENTITY_PATCH).getCapability()
                instanceof HumanoidMobPatch<?> patch) {
            ItemStack mainhand = npc.getMainHandItem(), offhand = npc.getOffhandItem();
            CapabilityItem mainCap = patch.getHoldingItemCapability(InteractionHand.MAIN_HAND);
            CapabilityItem offCap = patch.getHoldingItemCapability(InteractionHand.OFF_HAND);
            if (patch == cnpcef$weaponPatch && mainCap == cnpcef$mainhandCap && offCap == cnpcef$offhandCap
                    && ItemStack.isSameItemSameComponents(mainhand, cnpcef$mainhand)
                    && ItemStack.isSameItemSameComponents(offhand, cnpcef$offhand)) return;
            patch.modifyLivingMotionByCurrentItem(false);
            cnpcef$weaponPatch = patch;
            cnpcef$mainhand = mainhand.copy();
            cnpcef$offhand = offhand.copy();
            cnpcef$mainhandCap = mainCap;
            cnpcef$offhandCap = offCap;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Unique
    private void cnpcef$updateModelCap() {
        CnpcBranchPatchProvider branch = NpcPatchReloadListener.providerFor(npc);
        try {
            AttachmentEntityPatchProvider provider = npc.getData(EpicFightAttachmentTypes.ENTITY_PATCH);
            if (provider == null) {
                return;
            }
            // CNPC 每次外观/追踪同步都会读 DataDisplay。相同模型与重载代际无需重建
            // Animator/AI/armature，否则动作被打断，且重复分配与装配随 NPC 数量放大。
            if (java.util.Objects.equals(cnpcef$efModel, cnpcef$boundModel)
                    && cnpcef$boundProvider == branch) {
                // 第三方在同代替换 patch 时不反复争抢；下一代或显式换模型再重新安装。
                if (provider.getCapability() != cnpcef$boundPatch) cnpcef$boundPatch = provider.getCapability();
                return;
            }
            if (cnpcef$efModel == null && cnpcef$boundModel == null) {
                cnpcef$boundProvider = branch;
                return;
            }
            EntityPatch<?> patch = branch.get(npc);
            // construct 失败时保留旧 attachment，不能发布没有 Animator 的半成品。
            if (patch != null) ((EntityPatch) patch).onConstructed(npc);
            EntityPatch<?> previous = provider.getCapability();
            ((IAttachmentEntityPatchProvider) (Object) provider).cnpcef$setEntityPatch(patch);
            if (patch != null) {
                // tickCount 不能证明已加入世界（新加入/GUI 预览都可能为 0）。
                // 已加入的热替换立即初始化；NBT/生成包早期安装留给 EF join 钩子。
                try {
                    if (npc.isAddedToLevel() && patch.uninitialized()) {
                        ((EntityPatch) patch).onJoinWorld(npc, npc.level(), false);
                    }
                } catch (Exception e) {
                    ((IAttachmentEntityPatchProvider) (Object) provider).cnpcef$setEntityPatch(previous);
                    throw e;
                }
                cnpcef$updateClientWeaponMotions();
            }
            cnpcef$boundModel = cnpcef$efModel;
            cnpcef$boundProvider = branch;
            cnpcef$boundPatch = patch;
        } catch (Exception e) {
            // 失败按模型/代际缓存，避免坏模型在每 tick 重试并刷日志；重载后可重试。
            cnpcef$boundModel = cnpcef$efModel;
            cnpcef$boundProvider = branch;
            CNPCEF_LOGGER.error("Failed to bind NPC EF model {} for entity {}", cnpcef$efModel, npc.getId(), e);
        }
    }
}
