package top.bincnpcef.mixin.impl;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yesman.epicfight.client.renderer.patched.layer.WearableItemLayer;

import java.util.AbstractList;
import java.util.List;

/** Keeps EF's single-slot armor probe from writing into CNPC's disposable snapshot. */
@Mixin(value = WearableItemLayer.class, remap = false)
public class MixinWearableItemLayer {
    @Unique
    private static final List<EquipmentSlot> CNPCEF_ARMOR_ORDER = List.of(
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD);

    // Only this client-side bake sees the live view; normal CNPC callers keep their snapshot.
    @Redirect(method = "getArmorModel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getArmorSlots()Ljava/lang/Iterable;"), require = 1)
    private static Iterable<ItemStack> cnpcef$armorProbeSlots(LivingEntity entity) {
        if (!(entity instanceof EntityNPCInterface npc)) {
            return entity.getArmorSlots();
        }
        List<EquipmentSlot> armorOrder = CNPCEF_ARMOR_ORDER;
        return new AbstractList<>() {
            @Override
            public ItemStack get(int index) {
                return npc.getItemBySlot(armorOrder.get(index));
            }

            @Override
            public ItemStack set(int index, ItemStack element) {
                EquipmentSlot slot = armorOrder.get(index);
                ItemStack previous = npc.getItemBySlot(slot);
                npc.setItemSlot(slot, element == null ? ItemStack.EMPTY : element);
                return previous;
            }

            @Override
            public int size() {
                return armorOrder.size();
            }
        };
    }
}
