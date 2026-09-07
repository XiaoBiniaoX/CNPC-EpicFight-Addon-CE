package com.goodbird.cnpcefaddon.mixin.impl;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.client.gui.EntityUI;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 临时诊断（第二轮）：定位「CE 小怪血条红色条恒空、耐力条正常」。
 *
 * <p>上一轮把埋点放在 {@code HealthBar.draw} 的 HEAD，与 CE 的 {@code HealthBarMixin}
 * 同点竞争，CE 优先级更高先 {@code ci.cancel()}，导致**一条 CeNpcPatch 样本都没采到**
 * （4 条全是 NpcHumanoidPatch / null）。
 *
 * <p>本轮改拦 {@code EntityUI.drawUIAsLevelModel} —— 它是血量背景、血量进度、
 * 耐力背景、耐力进度**四次绘制的共同出口**，与谁先 cancel 无关，必然采到。
 * 通过 U/V 区间即可区分是哪一条：
 * <ul>
 *   <li>血量背景 v=0..20，血量进度 v=21..41</li>
 *   <li>耐力背景 v=42..62，耐力进度 v=63..83，破防 v=84..104</li>
 * </ul>
 * 关键判据是血量进度那次的 {@code x1-x0}（显示宽度）与 {@code u1-u0}（UV 宽度）：
 * 若宽度为 0 或负 → 几何/UV 退化，血条画不出来即属正常结果。
 *
 * <p>只读不改，原样放行。定案后立即整类删除。
 */
@Mixin(value = EntityUI.class, remap = false)
public abstract class MixinEntityUIDrawDiag {
    private static final Logger LOGGER = LoggerFactory.getLogger("cnpcef-hpbar2-diag");
    /** 同一「纹理+UV 区间+尺寸」组合只打一次，避免每帧刷屏。 */
    private static final Map<String, Boolean> SEEN = new ConcurrentHashMap<>();

    @Inject(
            method = "drawUIAsLevelModel(Lorg/joml/Matrix4f;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/renderer/MultiBufferSource;FFFFIIIII)V",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private static void cnpcef$traceDraw(Matrix4f matrix, ResourceLocation texture,
                                         MultiBufferSource buffers,
                                         float x0, float y0, float x1, float y1,
                                         int u0, int v0, int u1, int v1, int textureSize,
                                         CallbackInfo ci) {
        // 只关心 CE 的血条纹理，避免把 EF/坚不可摧的 UI 一起打出来
        if (texture == null || !"combat_evolution".equals(texture.getNamespace())) {
            return;
        }

        String band;
        if (v0 == 0) {
            band = "healthBG";
        } else if (v0 == 21) {
            band = "healthFG";
        } else if (v0 == 42) {
            band = "staminaBG";
        } else if (v0 == 63) {
            band = "staminaFG";
        } else if (v0 == 84) {
            band = "staminaBreak";
        } else {
            band = "v" + v0;
        }

        String key = texture + "|" + band + "|" + u0 + "-" + u1
                + "|" + String.format("%.4f", x1 - x0);
        if (SEEN.putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }

        LOGGER.error("[cnpcef-hpbar2-diag] band={} tex={} u={}..{} (uvW={}) v={}..{} "
                        + "x={}..{} (dispW={}) y={}..{} (dispH={}) texSize={}",
                band, texture, u0, u1, (u1 - u0), v0, v1,
                String.format("%.4f", x0), String.format("%.4f", x1),
                String.format("%.4f", x1 - x0),
                String.format("%.4f", y0), String.format("%.4f", y1),
                String.format("%.4f", y1 - y0), textureSize);
    }
}
