package com.goodbird.cnpcefaddon.mixin.impl;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.AbstractList;

/**
 * 让 CNPC 的 {@code getArmorSlots()} 返回可写回实体的实时视图。
 *
 * <p>原实现每次调用都新建 {@code ArrayList} 拷贝一份护甲快照后返回
 * （{@code EntityNPCInterface.m_6168_}），调用方对返回列表的 {@code set()} 会写进这个
 * 用完即弃的副本，对 NPC 本身没有任何影响。原版 {@code LivingEntity} 返回的是实体内部
 * 的实时列表，因此依赖「就地改、用完还原」这一契约的第三方代码在 NPC 上会静默失效。
 *
 * <p>受影响的真实案例：Epic Fight 烘焙 GeckoLib 盔甲网格前，会先把其余三个槽位临时置空、
 * 只留当前槽位那件，再离屏渲染一次以探测该槽位应显示哪些骨骼，随后把四个槽位写回原值
 * （{@code WearableItemLayer#getArmorModel}）。由于置空写在了副本上，探测时 NPC 身上四件
 * 盔甲仍然都在；而 GeckoLib 的盔甲渲染器是按物品共享的单例，用实例字段保存
 * {@code currentSlot} 等上下文，连续被多个槽位覆写后按错误的槽位隐藏了骨骼，最终烘焙出
 * 一个「零部件」的空网格。EF 又按物品把网格永久缓存，于是表现为某些盔甲（最常见是胸甲）
 * 在 NPC 身上永远不渲染，而玩家穿戴完全正常。
 *
 * <p>这里只补齐「返回实时视图」这一原版契约，不改变任何护甲数据的存储方式：读写都转发到
 * 实体自己的 {@code getItemBySlot}/{@code setItemSlot}，因此顺序、索引换算、脚本可见性
 * 与 NBT 结构全部沿用 CNPC 原有实现。列表长度固定为 4，不支持增删，与原版一致。
 *
 * <p>公共 mixin（非客户端专属）：{@code getArmorSlots} 在服务端也会被调用（掉落、附魔、
 * 属性统计等），返回实时视图对两端语义一致且更贴近原版；本类不引用任何客户端类型。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNpcArmorSlots {

    /**
     * CNPC 原实现的护甲顺序为 FEET、LEGS、CHEST、HEAD（即 {@code 3 - slot.getIndex()}），
     * 与原版 {@code LivingEntity#getArmorSlots} 一致，这里保持不变。
     */
    private static final EquipmentSlot[] CNPCEF_ARMOR_ORDER = {
            EquipmentSlot.FEET,
            EquipmentSlot.LEGS,
            EquipmentSlot.CHEST,
            EquipmentSlot.HEAD
    };

    @Inject(method = "m_6168_", at = @At("RETURN"), cancellable = true)
    private void cnpcef$liveArmorView(CallbackInfoReturnable<Iterable<ItemStack>> cir) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;

        cir.setReturnValue(new AbstractList<>() {
            @Override
            public ItemStack get(int index) {
                return npc.getItemBySlot(CNPCEF_ARMOR_ORDER[index]);
            }

            @Override
            public ItemStack set(int index, ItemStack element) {
                EquipmentSlot slot = CNPCEF_ARMOR_ORDER[index];
                ItemStack previous = npc.getItemBySlot(slot);
                npc.setItemSlot(slot, element == null ? ItemStack.EMPTY : element);
                return previous;
            }

            @Override
            public int size() {
                return CNPCEF_ARMOR_ORDER.length;
            }
        });
    }
}
