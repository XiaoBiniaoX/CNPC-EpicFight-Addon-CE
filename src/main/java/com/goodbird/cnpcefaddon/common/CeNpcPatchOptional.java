package com.goodbird.cnpcefaddon.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yesman.epicfight.api.data.reloader.MobPatchReloadListener;

/**
 * CombatEvolution 的无链接门面。
 *
 * <p>此类不能 import CE 类型：NpcPatchReloadListener 在 CE 缺失时仍会加载。只有 CE 已加载且
 * 服务端同步标签带专用 marker 时，才通过反射触碰 CE bridge 的类签名。
 */
public final class CeNpcPatchOptional {
    public static final String PATCH_TYPE = "COMBAT_EVOLUTION";
    private static final String CE_MODID = "combat_evolution";
    private static final String RELOADER_CLASS = "com.goodbird.cnpcefaddon.common.CeNpcPatchReloader";
    private static final Logger LOGGER = LoggerFactory.getLogger(CeNpcPatchOptional.class);

    private CeNpcPatchOptional() {
    }

    public static boolean isCeTag(CompoundTag tag) {
        return tag != null && PATCH_TYPE.equals(tag.getString("cnpcefPatchType"));
    }

    public static MobPatchReloadListener.AbstractMobPatchProvider deserializeClient(CompoundTag tag) {
        if (!ModList.get().isLoaded(CE_MODID) || !isCeTag(tag)) {
            return null;
        }
        try {
            MobPatchReloadListener.AbstractMobPatchProvider provider =
                    (MobPatchReloadListener.AbstractMobPatchProvider) Class.forName(RELOADER_CLASS)
                            .getMethod("deserializeClient", CompoundTag.class)
                            .invoke(null, tag);
            if (provider != null) {
                setClientArmature(provider, tag);
            }
            return provider;
        } catch (Throwable error) {
            LOGGER.error("CombatEvolution NPC datapack compatibility failed; skipped CE entry", error);
            return null;
        }
    }

    private static void setClientArmature(MobPatchReloadListener.AbstractMobPatchProvider provider, CompoundTag tag) throws Exception {
        net.minecraft.resources.ResourceLocation armatureLoc = net.minecraft.resources.ResourceLocation.parse(tag.getString("armature"));
        armatureLoc = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                armatureLoc.getNamespace(), "animmodels/" + armatureLoc.getPath() + ".json");
        yesman.epicfight.api.asset.AssetAccessor<? extends yesman.epicfight.api.model.Armature> armature =
                yesman.epicfight.gameasset.Armatures.getOrCreate(armatureLoc,
                        tag.getBoolean("humanoid")
                                ? yesman.epicfight.model.armature.HumanoidArmature::new
                                : yesman.epicfight.api.model.Armature::new);
        provider.getClass().getField("armature").set(provider, armature);
    }
}
