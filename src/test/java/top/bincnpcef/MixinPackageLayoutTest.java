package top.bincnpcef;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 结构约束测试：duck-typing 接口不能放在 mixin 声明的包下。
 *
 * <p>背景：{@code cnpcef.mixins.json} 声明了 {@code "package": "top.bincnpcef.mixin"}，
 * Mixin 系统会将该包视为 mixin 领地，禁止非 mixin 代码直接引用其中的类。
 * 若被非 mixin 代码（如 {@code CnpcBranchPatchProvider}）引用，运行时会抛出
 * {@code IllegalClassLoadError}，导致生成 NPC 时游戏崩溃。
 *
 * <p>因此，被外部普通代码引用的 duck-typing 接口必须放在普通包（如 {@code top.bincnpcef.api}）下。
 */
class MixinPackageLayoutTest {

    @Test
    void iDataDisplayMustNotBeInMixinPackage() {
        Class<?> cls;
        try {
            cls = Class.forName("top.bincnpcef.api.IDataDisplay");
        } catch (ClassNotFoundException e) {
            fail("IDataDisplay 必须位于 top.bincnpcef.api 包（而非 mixin 包），" +
                "否则非 mixin 代码引用它会在运行时抛出 IllegalClassLoadError。" + e.getMessage());
            return;
        }
        assertNotEquals("top.bincnpcef.mixin", cls.getPackageName(),
            "IDataDisplay 不得位于 mixin 声明的包 top.bincnpcef.mixin 下");
    }
}
