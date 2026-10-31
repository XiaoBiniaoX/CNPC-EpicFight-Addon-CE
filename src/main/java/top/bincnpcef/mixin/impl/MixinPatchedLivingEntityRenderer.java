package top.bincnpcef.mixin.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.client.renderer.RenderCustomNpc;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

@Mixin(value = PatchedLivingEntityRenderer.class, remap = false)
public class MixinPatchedLivingEntityRenderer {
    // CNPC外观模型的匿名装备转发层无法被EF精确匹配；仅替换原版层枚举来源。
    // 原NPC/patch/骨架参数保持，第二个customLayers迭代器不拦截、不重复绘制。
    @Redirect(method = "renderLayer",
            at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;", ordinal = 0),
            require = 1)
    private Iterator<?> cnpcef$modelLayers(
            List<?> layers, LivingEntityRenderer renderer, LivingEntityPatch entitypatch,
            LivingEntity entity, OpenMatrix4f[] poses, MultiBufferSource buffer,
            PoseStack poseStack, int packedLight, float partialTicks) {
        if (entity instanceof EntityCustomNpc npc && renderer instanceof RenderCustomNpc
                && ((IRenderCustomNpc) renderer).cnpcef$getRenderEntity() instanceof RenderCustomNpc modelRenderer) {
            return npc.display.isShowingLayers() ? modelRenderer.npclayers.iterator() : Collections.emptyIterator();
        }
        return layers.iterator();
    }
}
