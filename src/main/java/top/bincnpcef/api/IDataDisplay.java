package top.bincnpcef.api;

import net.minecraft.resources.ResourceLocation;

/**
 * Duck-typing interface implemented by {@link top.bincnpcef.mixin.impl.MixinDataDisplay}
 * to expose the persisted EpicFight model field on CNPC's {@code DataDisplay}.
 *
 * <p>注意：此接口必须位于普通包（非 mixin 声明的包），因为它被非 mixin 代码
 * （如 {@code CnpcBranchPatchProvider}）直接引用。若放在 mixin 包下，运行时会抛出
 * {@code IllegalClassLoadError}。
 */
public interface IDataDisplay {
    ResourceLocation cnpcef$getEFModel();

    void cnpcef$setEFModel(ResourceLocation model);

    boolean cnpcef$hasEFModel();
}
