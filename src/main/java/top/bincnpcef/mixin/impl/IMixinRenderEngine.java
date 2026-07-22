package top.bincnpcef.mixin.impl;

import com.google.common.collect.BiMap;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import yesman.epicfight.client.events.engine.RenderEngine;
import yesman.epicfight.client.renderer.patched.entity.PHumanoidRenderer;
import yesman.epicfight.client.renderer.patched.entity.PatchedEntityRenderer;

import java.util.function.Function;

/**
 * 通过 {@code @Accessor} 暴露 EF {@link RenderEngine} 的私有字段，
 * 供 {@link top.bincnpcef.client.render.RenderStorage} 构造渲染器使用。
 */
@Mixin(RenderEngine.class)
public interface IMixinRenderEngine {
    @Accessor(remap = false)
    PHumanoidRenderer<?, ?, ?, ?, ?> getBasicHumanoidRenderer();

    @Accessor(remap = false)
    BiMap<EntityType<?>, Function<EntityType<?>, PatchedEntityRenderer>> getEntityRendererProvider();
}
