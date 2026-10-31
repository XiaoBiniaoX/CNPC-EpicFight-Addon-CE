package top.bincnpcef.mixin.impl;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import noppes.npcs.client.renderer.RenderCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = RenderCustomNpc.class, remap = false)
public interface IRenderCustomNpc {
    @Accessor("renderEntity")
    LivingEntityRenderer cnpcef$getRenderEntity();
}
