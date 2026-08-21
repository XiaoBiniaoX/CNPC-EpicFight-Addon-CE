package com.goodbird.cnpcefaddon.client;

import com.goodbird.cnpcefaddon.client.render.RenderStorage;
import com.magistuarmory.client.render.entity.layer.ArmorDecorationLayer;
import com.magistuarmory.compat.PatchedArmorDecorationLayer;
import yesman.epicfight.api.client.model.Meshes;
import yesman.epicfight.client.mesh.HumanoidMesh;
import yesman.epicfight.client.renderer.patched.entity.PatchedEntityRenderer;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;

/**
 * 将史诗骑士的盔甲装饰层接入本模组为 CNPC 自建的史诗战斗渲染器。
 * <p>
 * 史诗骑士自身只在史诗战斗的 {@code PatchedRenderersEvent.Modify} 事件里安装
 * {@link PatchedArmorDecorationLayer}。CNPC 的渲染器由 {@link RenderStorage} 在数据包
 * 重载后另行创建，不在该事件提供的实体渲染器缓存中，导致原版渲染被史诗战斗接管后，
 * 头盔鸡冠、羽冠等 {@link ArmorDecorationLayer} 装饰层没有对应的骨骼绘制器而被跳过。
 * <p>
 * 本类复用史诗骑士官方的骨骼层，不重写任何装饰解析、贴图或骨骼绑定逻辑；只补齐缺失的
 * 注册步骤。由 {@link EpicKnightsIntegration} 在确认可选 mod 已加载后才会加载本类。
 */
final class EpicKnightsNpcLayerInstaller {
    private EpicKnightsNpcLayerInstaller() {
    }

    /** 为已创建的全部人形 CNPC 渲染器安装官方装饰层。 */
    static void install() {
        for (PatchedEntityRenderer renderer : RenderStorage.renderersMap.values()) {
            if (!(renderer instanceof PatchedLivingEntityRenderer<?, ?, ?, ?, ?> livingRenderer)
                    || !(renderer.getDefaultMesh().get() instanceof HumanoidMesh)) {
                continue;
            }

            // 泛型参数与史诗骑士自己的 ClientEpicFightCompat 注册点相同；CNPC 的人形
            // renderer 同样使用 LivingEntity / LivingEntityPatch，Java 的通配符无法表达该关系。
            @SuppressWarnings({"rawtypes", "unchecked"})
            PatchedLivingEntityRenderer rawRenderer = (PatchedLivingEntityRenderer) livingRenderer;
            rawRenderer.addPatchedLayerAlways(ArmorDecorationLayer.class,
                    new PatchedArmorDecorationLayer<>(Meshes.BIPED));
        }
    }
}
