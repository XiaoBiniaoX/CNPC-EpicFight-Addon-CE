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
    private static final String PATCH_CLASS = "com.goodbird.cnpcefaddon.common.patch.CeNpcPatch";
    private static final Logger LOGGER = LoggerFactory.getLogger(CeNpcPatchOptional.class);

    private CeNpcPatchOptional() {
    }

    public static boolean isCeTag(CompoundTag tag) {
        return tag != null && PATCH_TYPE.equals(tag.getString("cnpcefPatchType"));
    }

    /**
     * 在一个 CE patch 上调用无参方法；对象不是 CE patch 时什么都不做。
     *
     * <p>调用点原本写的是 {@code if (p instanceof CeNpcPatch ce) ce.initAI();}，
     * 这会让 {@code MixinDataDisplay} 在 CE 缺失时于每个 EF NPC 上抛
     * {@code NoClassDefFoundError}（原因见 {@link #isCePatch}）。反射按名调用不解析 CE 类型。
     *
     * <p>方法名仅来自本类调用点的字面量，不来自数据包或网络。
     */
    public static void invokeOnCePatch(Object patch, String method) {
        if (!isCePatch(patch)) {
            return;
        }
        try {
            patch.getClass().getMethod(method).invoke(patch);
        } catch (Throwable error) {
            LOGGER.error("CombatEvolution NPC patch call failed: {}", method, error);
        }
    }

    /**
     * 比较两个 CE patch 的数据包 provider 是否为同一实例（踩坑第 46 条的复用判据）。
     * 任一方不是 CE patch 时返回 {@code false}，调用方据此走重建分支。
     */
    public static boolean sameCeProvider(Object left, Object right) {
        if (!isCePatch(left) || !isCePatch(right)) {
            return false;
        }
        try {
            Object leftProvider = left.getClass().getMethod("getNpcProvider").invoke(left);
            Object rightProvider = right.getClass().getMethod("getNpcProvider").invoke(right);
            return leftProvider == rightProvider;
        } catch (Throwable error) {
            LOGGER.error("CombatEvolution NPC provider comparison failed", error);
            return false;
        }
    }

    /**
     * 判断一个 patch 是否为我方的 CE patch，且不解析任何 CE 类。
     *
     * <p>不能写成 {@code patch instanceof CeNpcPatch}：{@code CeNpcPatch} 继承
     * {@code net.shelmarow.combat_evolution.ai.CEDatapackMobPatch}，解析它就会连带解析该超类。
     * JVM 对 <b>非 null</b> 左操作数的 {@code instanceof} 一定会解析右侧类型（本机 JDK 17.0.11
     * 实测：null 操作数返回 false，非 null 操作数抛 {@code NoClassDefFoundError: Missing}），
     * 因此 CE 未安装时该表达式会在普通 NPC 身上抛错，而 {@code NoClassDefFoundError} 属
     * {@code Error}、不被 {@code catch (Exception)} 拦住。
     *
     * <p>按类名比较则只做字符串相等，永不触发类加载；同一做法已在
     * {@code MixinEntityNpcNativeAttack} 里用于识别 CE 的 Goal。
     */
    public static boolean isCePatch(Object patch) {
        return patch != null && PATCH_CLASS.equals(patch.getClass().getName());
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
