package com.goodbird.cnpcefaddon.mixin.impl;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 修复 Epic Fight 的 Geo 盔甲胸甲不随躯干弯曲（表现为 NPC 躯干顶出盔甲）。
 *
 * <p><b>根因（实测数据定案）</b>：{@code ChestPartTransformer} 内部两套坐标单位不一致。
 * <ul>
 *   <li>{@code yClipCoord} = 1.125F、{@code noneAttachmentArea} 中心 y = 1.125
 *       —— <b>方块单位</b>（{@code GeoModelTransformer:52}）</li>
 *   <li>{@code WEIGHT_ALONG_Y} = 13.6666 / 15.8333 / 18.0 / 20.1666 / 22.3333
 *       —— <b>像素单位</b>（{@code :255}，与 {@code VanillaModelTransformer:238} 同值）</li>
 * </ul>
 * 而喂给 {@code getYClipWeight(y)} 的 y 来自 {@code getTranslatedVertex} 的顶点坐标，
 * 实测为方块单位（{@code getCenterOfCube} 实测 0.1956~1.1250）。于是所有 y 都
 * {@code < 13.6666}，{@code getYClipWeight}（{@code :401-404}）恒定返回
 * {@code {chest=0, torso=1}} —— <b>整块胸甲 100% 绑到 Torso(7)</b>。
 *
 * <p>而 NPC 躯干本体（{@code biped.json} 的 torso 部件）实测是双关节混合，
 * 上部以 Chest(8) 为主（0.764~0.770）。躯干跟着 Chest 弯、盔甲跟着 Torso 不动，
 * 两者错开 → 躯干顶出盔甲。原版胸甲走 {@code VanillaModelTransformer}
 * （其顶点坐标本就是像素单位，与权重表一致），故不复现。
 *
 * <p><b>修法</b>：把传入 {@code getYClipWeight} 的 y 从方块单位换算为像素单位，
 * 让 EF 自己的权重表正常工作。同时修 {@code getYClipWeight} 自身的空循环缺陷
 * （{@code :406-409} 的 for 体为空，{@code index} 恒 -1，插值从未执行）。
 *
 * <p>不改权重数值、不动 {@code LimbPartTransformer}（手臂/腿正常）、
 * 不碰幻想盔甲资源、不读写 NBT / 数据包 / play_speed。
 * 对所有 GeckoLib 盔甲一致生效。
 */
@Mixin(targets = "yesman.epicfight.api.client.model.transformer.GeoModelTransformer$ChestPartTransformer", remap = false)
public class MixinGeoChestUnitFix {

    /** 方块 → 像素。EF 的 Geo 顶点是方块单位，而 WEIGHT_ALONG_Y 是像素单位。 */
    private static final float BLOCK_TO_PIXEL = 16.0F;

    /**
     * 胸甲混合判定的 z 轴总深度（方块单位）。EF 原值 0.45（±0.225）只够扁平的原版胸甲；
     * 立体装饰的 Geo 盔甲实测前凸到 z=0.6133，会落到「整块绑单关节」分支。
     * ponytail: 固定容差，前凸超过 0.8 的模型需按实测再调。
     */
    private static final double CHEST_DEPTH_TOLERANCE = 1.6D;

    // EF 侧为包级私有 final（javap 已核，无 private），@Shadow 可见性必须一致。
    @Shadow @Final @Mutable
    AABB noneAttachmentArea;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cnpcef$widenChestBlendArea(int upperJoint, int lowerJoint, float yBasis,
                                            AABB originalArea, CallbackInfo ci) {
        AABB before = this.noneAttachmentArea;

        // 只放宽 z 半径让前凸装饰参与混合；x/y/yClipCoord 全部沿用 EF 原值。
        this.noneAttachmentArea = AABB.ofSize(before.getCenter(),
                before.getXsize(),
                before.getYsize(),
                Math.max(before.getZsize(), CHEST_DEPTH_TOLERANCE));
    }

    /**
     * 核心修复：y 换算到像素单位 + 补上 EF 空循环缺失的插值。
     *
     * <p>返回类型是 EF 的包级私有 record，编译期不可见，用反射构造。
     * 构造失败时不拦截，退回 EF 原逻辑，绝不因修复导致崩溃。
     */
    @Inject(method = "getYClipWeight", at = @At("HEAD"), cancellable = true)
    private static void cnpcef$fixChestWeight(float y, CallbackInfoReturnable<Object> cir) {
        // EF 权重表（:255）原值，不改数值。
        final float[] coords = {13.6666F, 15.8333F, 18.0F, 20.1666F, 22.3333F};
        final float[] chest = {0.230F, 0.254F, 0.500F, 0.744F, 0.770F};
        final float[] torso = {0.770F, 0.746F, 0.500F, 0.256F, 0.230F};

        // 关键：传入的 y 是方块单位（实测 0.1956~1.1250），权重表是像素单位。
        float pixelY = y * BLOCK_TO_PIXEL;

        float chestWeight;
        float torsoWeight;

        if (pixelY < coords[0]) {
            chestWeight = 0.0F;
            torsoWeight = 1.0F;
        } else if (pixelY >= coords[coords.length - 1]) {
            chestWeight = 1.0F;
            torsoWeight = 0.0F;
        } else {
            int i = 0;
            while (i + 1 < coords.length && pixelY >= coords[i + 1]) {
                i++;
            }
            float span = coords[i + 1] - coords[i];
            float ratio = span == 0.0F ? 0.0F : (pixelY - coords[i]) / span;
            chestWeight = chest[i] + (chest[i + 1] - chest[i]) * ratio;
            torsoWeight = torso[i] + (torso[i + 1] - torso[i]) * ratio;
        }

        // yClipCoord 字段保留原始 y（方块单位），只有权重被修正。
        Object weight = com.goodbird.cnpcefaddon.client.GeoChestWeightFactory
                .create(y, chestWeight, torsoWeight);

        if (weight != null) {
            cir.setReturnValue(weight);
        }
    }

    /**
     * 同一处单位缺陷的第二个受害者：{@code getMiddleYClipWeights(minY, maxY)}（{@code :419-427}）
     * 用像素单位的 {@code WEIGHT_ALONG_Y} 去比方块单位的 minY/maxY，条件
     * {@code yClipCoord > minY && maxY >= yClipCoord} 恒不成立 → 从不插入中间切分点，
     * 胸甲在躯干中段没有过渡带。
     *
     * <p>返回的 {@code yClipCoord} 会被 {@code bakeCube:343} 的 {@code getClipPoint}
     * 当作几何裁剪高度使用，故必须换回<b>方块单位</b>；只有权重按像素表取值。
     */
    @Inject(method = "getMiddleYClipWeights", at = @At("HEAD"), cancellable = true)
    private static void cnpcef$fixMiddleWeights(float minY, float maxY,
                                                CallbackInfoReturnable<java.util.List<Object>> cir) {
        final float[] coords = {13.6666F, 15.8333F, 18.0F, 20.1666F, 22.3333F};
        final float[] chest = {0.230F, 0.254F, 0.500F, 0.744F, 0.770F};
        final float[] torso = {0.770F, 0.746F, 0.500F, 0.256F, 0.230F};

        java.util.List<Object> result = new java.util.ArrayList<>();

        for (int i = 0; i < coords.length; i++) {
            // 切分高度换回方块单位，供 getClipPoint 做几何裁剪。
            float blockY = coords[i] / BLOCK_TO_PIXEL;

            if (blockY > minY && maxY >= blockY) {
                Object w = com.goodbird.cnpcefaddon.client.GeoChestWeightFactory
                        .create(blockY, chest[i], torso[i]);

                if (w == null) {
                    return; // 构造失败，交回 EF 原逻辑
                }

                result.add(w);
            }
        }

        cir.setReturnValue(result);
    }
}
