package top.bincnpcef.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import noppes.npcs.CustomEntities;
import top.bincnpcef.common.CnpcBranchPatchProvider;
import top.bincnpcef.mixin.impl.IMixinRenderEngine;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.model.Meshes;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.client.events.engine.RenderEngine;
import yesman.epicfight.client.mesh.HumanoidMesh;
import yesman.epicfight.client.renderer.patched.entity.PCustomEntityRenderer;
import yesman.epicfight.client.renderer.patched.entity.PCustomHumanoidEntityRenderer;
import yesman.epicfight.client.renderer.patched.entity.PatchedEntityRenderer;

import java.util.HashMap;
import java.util.Map;

/**
 * 按 EF 模型 ID 存储渲染器。
 *
 * <p>关键说明：1.21.1 EF 的 {@code RenderEngine#entityRendererCache} 按 {@code EntityType} 存储渲染器，
 * 但 CNPC 所有模型都共用同一个 EntityType（{@link CustomEntities#entityCustomNpc}），
 * 直接注册到 entityRendererCache 只能让最后一个模型生效，且 {@code resetRenderers()} 会清空缓存。
 * 因此本类参考 1.20.1 实现，在本地按模型 ID 存储渲染器，并通过
 * {@link top.bincnpcef.mixin.impl.MixinRenderEngine} 在 {@code getEntityRenderer}/{@code hasRendererFor}
 * 调用时按 NPC 当前选择的 EF 模型 ID 路由到对应渲染器。</p>
 */
public class RenderStorage {
    public static Map<ResourceLocation, PatchedEntityRenderer> renderersMap = new HashMap<>();

    public static void registerRenderers(CnpcBranchPatchProvider provider, Map<ResourceLocation, CompoundTag> tags) {
        renderersMap.clear();
        for (Map.Entry<ResourceLocation, CompoundTag> entry : tags.entrySet()) {
            ResourceLocation rl = entry.getKey();
            CompoundTag tag = entry.getValue();
            if (tag.contains("renderer")) {
                String rendererName = tag.getString("renderer");
                try {
                    registerRenderer(rl, rendererName, tag);
                } catch (Exception e) {
                    System.err.println("[CNPC-EF-Addon] Failed to register renderer for " + rl + ": " + e.getMessage());
                }
            }
        }
    }

    public static void registerRenderer(ResourceLocation resourceLocation, String renderer, CompoundTag compound) {
        RenderEngine engine = RenderEngine.getInstance();
        IMixinRenderEngine renderEngine = (IMixinRenderEngine) engine;
        if ("".equals(renderer)) {
            return;
        }
        if ("player".equals(renderer)) {
            renderersMap.put(resourceLocation, renderEngine.getBasicHumanoidRenderer());
        } else if ("epicfight:custom".equals(renderer)) {
            Minecraft mc = engine.minecraft;
            EntityRenderDispatcher erd = mc.getEntityRenderDispatcher();
            EntityRendererProvider.Context context = new EntityRendererProvider.Context(erd, mc.getItemRenderer(), mc.getBlockRenderer(), erd.getItemInHandRenderer(), mc.getResourceManager(), mc.getEntityModels(), mc.font);
            if (compound.getBoolean("humanoid") || compound.getBoolean("isHumanoid")) {
                AssetAccessor<HumanoidMesh> mesh = Meshes.getOrCreate(ResourceLocation.parse(compound.getString("model")), (jsonAssetLoader) -> jsonAssetLoader.loadSkinnedMesh(HumanoidMesh::new));
                renderersMap.put(resourceLocation, new PCustomHumanoidEntityRenderer<>(mesh, context, CustomEntities.entityCustomNpc));
            } else {
                AssetAccessor<SkinnedMesh> mesh = Meshes.getOrCreate(ResourceLocation.parse(compound.getString("model")), (jsonAssetLoader) -> jsonAssetLoader.loadSkinnedMesh(SkinnedMesh::new));
                renderersMap.put(resourceLocation, new PCustomEntityRenderer(mesh, context));
            }
        } else {
            // preset renderer（如 "zombie"、"skeleton" 等）
            EntityType<?> presetEntityType = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(renderer));
            if (renderEngine.getEntityRendererProvider().containsKey(presetEntityType)) {
                renderersMap.put(resourceLocation, renderEngine.getEntityRendererProvider().get(presetEntityType).apply(presetEntityType));
            } else {
                throw new IllegalArgumentException("Datapack Mob Patch Crash: Invalid Renderer type " + renderer);
            }
        }
    }
}
