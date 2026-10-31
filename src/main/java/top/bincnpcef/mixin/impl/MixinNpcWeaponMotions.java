package top.bincnpcef.mixin.impl;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yesman.epicfight.network.EpicFightNetworkManager;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;

@Mixin(value = HumanoidMobPatch.class, remap = false)
public class MixinNpcWeaponMotions {
    @Redirect(method = "modifyLivingMotionByCurrentItem", at = @At(value = "INVOKE",
            target = "Lyesman/epicfight/network/EpicFightNetworkManager;sendToAllPlayerTrackingThisEntity(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;Lnet/minecraft/world/entity/Entity;[Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"))
    private void cnpcef$serverBroadcast(CustomPacketPayload packet, Entity entity, CustomPacketPayload[] others) {
        if (entity instanceof EntityNPCInterface && entity.level().isClientSide()) return;
        EpicFightNetworkManager.sendToAllPlayerTrackingThisEntity(packet, entity, others);
    }
}
