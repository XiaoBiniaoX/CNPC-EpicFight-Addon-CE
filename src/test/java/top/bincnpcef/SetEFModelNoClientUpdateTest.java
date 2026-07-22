package top.bincnpcef;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 回归测试：确保 {@code cnpcef$setEFModel} 不调用 {@code npc.updateClient()} 方法。
 *
 * <p>背景：{@code EntityNPCInterface.updateClient()} 内部调用
 * {@code PacketDistributor.sendToPlayersTrackingEntity()}，这是服务端专用 API。
 * 在客户端 GUI 回调中调用会导致
 * {@code IllegalStateException: Cannot send clientbound payloads on the client} 崩溃。
 *
 * <p>正确做法：不手动调 {@code updateClient()}。CNPC 的 Save 按钮会自动通过
 * {@code DataDisplay.save()} → {@code SPacketMenuSave} → 服务端 {@code readToNBT()}
 * → {@code npc.updateClient = true} 字段 → 下一 tick 服务端 {@code aiStep()} 中
 * 调用 {@code updateClient()} 方法广播给客户端。efModel 通过 Mixin 注入的
 * {@code save}/{@code readToNBT} 自动同步。
 */
class SetEFModelNoClientUpdateTest {

    @Test
    void setEFModelMustNotCallUpdateClientMethod() throws IOException {
        Path source = Paths.get("src/main/java/top/bincnpcef/mixin/impl/MixinDataDisplay.java");
        assertTrue(Files.exists(source), "MixinDataDisplay.java 源码文件不存在");

        String content = Files.readString(source);

        // 提取 cnpcef$setEFModel 方法体
        int methodStart = content.indexOf("void cnpcef$setEFModel(");
        assertTrue(methodStart > 0, "找不到 cnpcef$setEFModel 方法定义");

        // 从方法开始找下一个方法或文件结尾
        int methodEnd = content.indexOf("@Override", methodStart + 1);
        if (methodEnd < 0) {
            methodEnd = content.length();
        }

        String methodBody = content.substring(methodStart, methodEnd);

        // 只检查非注释行（跳过 // 和 * 开头的行），避免注释中的文字被误判
        String[] lines = methodBody.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("*")) {
                continue;
            }
            assertFalse(
                trimmed.contains("npc.updateClient()"),
                "cnpcef$setEFModel 不得调用 npc.updateClient() 方法——" +
                "该方法是服务端专用，在客户端 GUI 回调中调用会导致 " +
                "IllegalStateException: Cannot send clientbound payloads on the client。" +
                "违规行: " + trimmed
            );
        }
    }
}
