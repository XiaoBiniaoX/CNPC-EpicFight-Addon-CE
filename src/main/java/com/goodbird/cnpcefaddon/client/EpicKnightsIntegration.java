package com.goodbird.cnpcefaddon.client;

import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 史诗骑士盔甲装饰的可选客户端兼容门面。
 * <p>
 * 本类刻意不引用任何史诗骑士类型。缺少 {@code magistuarmory} 时，JVM 不会加载真正引用
 * 史诗骑士官方骨骼层的 {@link EpicKnightsNpcLayerInstaller}，从而保持本模组独立运行。
 */
public final class EpicKnightsIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(EpicKnightsIntegration.class);
    private static final String MODID = "magistuarmory";

    private EpicKnightsIntegration() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MODID);
    }

    /**
     * 数据包创建 CNPC 渲染器后调用。官方 addPatchedLayerAlways 会覆盖旧值，因此重载可安全重复。
     */
    public static void installNpcArmorDecorationLayer() {
        if (!isLoaded()) {
            return;
        }

        try {
            EpicKnightsNpcLayerInstaller.install();
        } catch (Throwable t) {
            // 可选装饰层失败不能影响 NPC 主模型、盔甲本体或服务端。
            LOGGER.error("[cnpcef-fix] 史诗骑士可选盔甲装饰兼容失败；NPC 将继续渲染但可能缺少头盔装饰", t);
        }
    }
}
