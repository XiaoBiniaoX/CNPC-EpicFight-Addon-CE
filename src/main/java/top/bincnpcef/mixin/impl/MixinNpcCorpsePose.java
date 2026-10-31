package top.bincnpcef.mixin.impl;

import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.bincnpcef.client.NpcCorpsePose;

@Mixin(EntityNPCInterface.class)
public class MixinNpcCorpsePose {
    @Unique private NpcCorpsePose cnpcef$corpsePose;

    @Inject(method = "tick", at = @At("HEAD"))
    private void cnpcef$beforeTick(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (!npc.level().isClientSide()) return;
        if (cnpcef$corpsePose == null) cnpcef$corpsePose = new NpcCorpsePose();
        cnpcef$corpsePose.beforeTick(npc);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void cnpcef$afterTick(CallbackInfo ci) {
        if (cnpcef$corpsePose != null) cnpcef$corpsePose.afterTick((EntityNPCInterface) (Object) this);
    }
}
