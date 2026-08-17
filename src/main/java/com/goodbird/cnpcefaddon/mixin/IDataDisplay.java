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
}