package com.goodbird.cnpcefaddon.client.render;

import com.goodbird.cnpcefaddon.client.CuriosIntegration;
import com.goodbird.cnpcefaddon.client.EpicKnightsIntegration;
import com.goodbird.cnpcefaddon.mixin.impl.IMixinRenderEngine;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;
import noppes.npcs.CustomEntities;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.model.Meshes;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.client.ClientEngine;
import yesman.epicfight.client.events.engine.RenderEngine;
import yesman.epicfight.client.mesh.HumanoidMesh;
import yesman.epicfight.client.renderer.patched.entity.PCustomEntityRenderer;
import yesman.epicfight.client.renderer.patched.entity.PCustomHumanoidEntityRenderer;
import yesman.epicfight.client.renderer.patched.entity.PatchedEntityRenderer;

import java.util.HashMap;
import java.util.Map;

public class RenderStorage {
    public static Map<ResourceLocation, PatchedEntityRenderer> renderersMap = new HashMap<>();

    public static void registerRenderer(ResourceLocation resourceLocation, String renderer, CompoundTag compound) {
        RenderEngine engine = ClientEngine.getInstance().renderEngine;
        IMixinRenderEngine renderEngine = (IMixinRenderEngine) ClientEngine.getInstance().renderEngine;
        if ("".equals(renderer))
            return;
        ResourceLocation rendererId = ResourceLocation.tryParse(renderer);
        String rendererName = rendererId != null ? rendererId.getPath() : renderer;
        if ("player".equals(rendererName) || ("zombie".equals(rendererName) && "epicfight:entity/biped".equals(compound.getString("model")))) {
            renderersMap.put(resourceLocation, renderEngine.getBasicHumanoidRenderer());
        } else if ("epicfight:custom".equals(renderer)) {
            EntityRenderDispatcher erd = engine.minecraft.getEntityRenderDispatcher();
            EntityRendererProvider.Context context = new EntityRendererProvider.Context(erd, engine.minecraft.getItemRenderer(), engine.minecraft.getBlockRenderer(), erd.getItemInHandRenderer(), engine.minecraft.getResourceManager(), engine.minecraft.getEntityModels(), engine.minecraft.font);
            if (compound.getBoolean("isHumanoid")) {
                AssetAccessor<HumanoidMesh> mesh = Meshes.getOrCreate(ResourceLocation.parse(compound.getString("model")), (jsonAssetLoader) -> jsonAssetLoader.loadSkinnedMesh(HumanoidMesh::new));
                renderersMap.put(resourceLocation, new PCustomHumanoidEntityRenderer(mesh, context, CustomEntities.entityCustomNpc));
            } else {
                AssetAccessor<SkinnedMesh> mesh = Meshes.getOrCreate(ResourceLocation.parse(compound.getString("model")), (jsonAssetLoader) -> jsonAssetLoader.loadSkinnedMesh(SkinnedMesh::new));
                renderersMap.put(resourceLocation, new PCustomEntityRenderer(mesh, context));
            }
        }  else {
            EntityType<?> presetEntityType = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse(renderer));
            if (renderEngine.getEntityRendererProvider().containsKey(presetEntityType)) {
                renderersMap.put(resourceLocation, renderEngine.getEntityRendererProvider().get(presetEntityType).apply(presetEntityType));
            } else {
                throw new IllegalArgumentException("Datapack Mob Patch Crash: Invalid Renderer type " + renderer);
            }
        }

        // Epic Fight installs its Curios accessory adapter on the player renderer only, so the
        // renderer just built would show no accessories. Done here rather than at the four
        // reload call sites, so every registration path is covered once.
        CuriosIntegration.installNpcAccessoryLayer();
        // 史诗骑士的鸡冠、羽冠等装饰不是 HumanoidArmorLayer 的一部分；官方只给
        // Epic Fight 内置 renderer 注册对应骨骼层，CNPC 的数据包 renderer 必须在此补装。
        EpicKnightsIntegration.installNpcArmorDecorationLayer();
    }


}
