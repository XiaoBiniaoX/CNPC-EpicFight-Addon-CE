package top.bincnpcef.mixin.impl;

import org.spongepowered.asm.mixin.Mixin;
import yesman.epicfight.api.data.reloader.MobPatchReloadListener;

@Mixin(value = MobPatchReloadListener.CustomMobPatchProvider.class)
public interface ICustomMobPatchProvider {
}
