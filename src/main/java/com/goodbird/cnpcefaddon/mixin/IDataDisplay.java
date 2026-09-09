package com.goodbird.cnpcefaddon.mixin;

import net.minecraft.resources.ResourceLocation;

public interface IDataDisplay {
    default void setEFModel(ResourceLocation modelPath){
        setEFModel(modelPath, true);
    }
    void setEFModel(ResourceLocation modelPath, boolean server);
    ResourceLocation getEFModel();

    boolean hasEFModel();

    default void setYsmModel(String modelPath) {
        setYsmModel(modelPath, true);
    }
    void setYsmModel(String modelPath, boolean server);
    String getYsmModel();

    boolean hasYsmModel();

    /**
     * 本 NPC 的动画攻击速度倍率，范围 {@code [0.01, 10.0]}，默认 {@code 1.0}。
     * <p>
     * 与数据包 {@code play_speed} 相乘而非替换：数据包写 6.0、此处填 2.0 时实际为 12.0。
     */
    float getAnimSpeedFactor();

    void setAnimSpeedFactor(float factor);

    /** Rebinds an already loaded NPC to the provider after a datapack reload. */
    void refreshEFModel();
}
