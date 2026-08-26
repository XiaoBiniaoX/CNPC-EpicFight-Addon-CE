package com.goodbird.cnpcefaddon.client;

import java.lang.reflect.Constructor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 构造 Epic Fight 的包级私有 {@code GeoModelTransformer$ChestPartTransformer$VertexWeight}。
 *
 * <p>该 record 在编译期不可见（包级私有嵌套类），只能反射。构造器缓存一次，
 * 避免每个顶点都做一次反射查找 —— 烘焙期顶点量在千级。
 *
 * <p>失败时返回 null 而不抛异常：调用方会退回 EF 原逻辑，宁可少一层修复
 * 也不能让渲染链崩溃。
 */
public final class GeoChestWeightFactory {
    private static final Logger LOGGER = LogManager.getLogger("cnpcefaddon");
    private static final String CLASS_NAME =
            "yesman.epicfight.api.client.model.transformer.GeoModelTransformer$ChestPartTransformer$VertexWeight";

    private static Constructor<?> constructor;
    private static boolean resolved;

    private GeoChestWeightFactory() {
    }

    /** @return VertexWeight 实例；无法构造时返回 null。 */
    public static Object create(float yClipCoord, float chestWeight, float torsoWeight) {
        Constructor<?> ctor = resolve();

        if (ctor == null) {
            return null;
        }

        try {
            return ctor.newInstance(yClipCoord, chestWeight, torsoWeight);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static synchronized Constructor<?> resolve() {
        if (resolved) {
            return constructor;
        }

        resolved = true;

        try {
            Class<?> cls = Class.forName(CLASS_NAME);
            Constructor<?> ctor = cls.getDeclaredConstructor(float.class, float.class, float.class);
            ctor.setAccessible(true);
            constructor = ctor;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("[cnpcefaddon] 无法解析 Epic Fight 的胸甲权重类，胸甲混合修复不生效: {}",
                    String.valueOf(e));
        }

        return constructor;
    }
}
