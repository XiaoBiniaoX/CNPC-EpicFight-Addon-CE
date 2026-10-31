package top.bincnpcef.mixin.impl;

import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bincnpcef.api.IDataDisplay;
import top.bincnpcef.common.NpcPatchReloadListener;
import yesman.epicfight.world.capabilities.entitypatch.EntityPatch;
import yesman.epicfight.world.capabilities.provider.CommonEntityPatchProvider;

/** 两端热发布只替换本附属 snapshot，不写 EF 单机共享的 HashMap 注册表。 */
@Mixin(value = CommonEntityPatchProvider.class, remap = false)
public class MixinNpcPatchRouting {
    @Inject(method = "getCapability", at = @At("HEAD"), cancellable = true)
    private void cnpcef$route(Entity entity, CallbackInfoReturnable<EntityPatch<?>> cir) {
        if (entity instanceof EntityNPCInterface npc && npc.display != null
                && ((IDataDisplay) npc.display).cnpcef$hasEFModel()) {
            cir.setReturnValue(NpcPatchReloadListener.providerFor(entity).get(entity));
        }
    }
}
