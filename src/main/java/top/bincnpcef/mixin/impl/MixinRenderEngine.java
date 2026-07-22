package top.bincnpcef.mixin.impl;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bincnpcef.api.IDataDisplay;
import top.bincnpcef.client.render.RenderStorage;
import yesman.epicfight.client.events.engine.RenderEngine;
import yesman.epicfight.client.renderer.patched.entity.PatchedEntityRenderer;

/**
 * 拦截 EF {@link RenderEngine} 的渲染器查询方法，按 NPC 当前选择的 EF 模型 ID
 * 从本地 {@link RenderStorage#renderersMap} 路由到对应的 {@link PatchedEntityRenderer}。
 *
 * <p>关键原因：CNPC 所有 EF 模型共用同一个 EntityType，EF 默认按 EntityType 查询
 * 会只返回最后一个注册的渲染器（或被 {@code resetRenderers()} 清空），导致 NPC
 * 不渲染但仍有 EF 锁定红框（红框由 capability 驱动，与渲染器无关）。</p>
 */
@Mixin(value = RenderEngine.class)
public class MixinRenderEngine {

    @Inject(method = "getEntityRenderer", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcef$getEntityRenderer(Entity entity, CallbackInfoReturnable<PatchedEntityRenderer> cir) {
        if (entity instanceof EntityNPCInterface npc && npc.display != null) {
            IDataDisplay dataDisplay = (IDataDisplay) npc.display;
            ResourceLocation model = dataDisplay.cnpcef$getEFModel();
            if (model != null && RenderStorage.renderersMap.containsKey(model)) {
                PatchedEntityRenderer renderer = RenderStorage.renderersMap.get(model);
                if (renderer != null) {
                    cir.setReturnValue(renderer);
                }
            }
        }
    }

    @Inject(method = "hasRendererFor", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcef$hasRendererFor(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof EntityNPCInterface npc && npc.display != null) {
            IDataDisplay dataDisplay = (IDataDisplay) npc.display;
            ResourceLocation model = dataDisplay.cnpcef$getEFModel();
            if (model != null && RenderStorage.renderersMap.containsKey(model)) {
                cir.setReturnValue(true);
            }
        }
    }
}
