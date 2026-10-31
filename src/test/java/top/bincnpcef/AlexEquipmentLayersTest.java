package top.bincnpcef;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.asm.mixin.Mixin;

import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Compiles the production selector against API stand-ins; no Mixin loading or game rendering. */
class AlexEquipmentLayersTest {
    @TempDir Path temporary;

    @Test
    void productionSelectorHandlesModelSwitchesVisibilityAndFallbacks() throws Exception {
        Map<String, String> fixtures = Map.ofEntries(
                Map.entry("com.mojang.blaze3d.vertex.PoseStack", "public class PoseStack {}"),
                Map.entry("net.minecraft.client.renderer.MultiBufferSource", "public interface MultiBufferSource {}"),
                Map.entry("net.minecraft.client.renderer.entity.LivingEntityRenderer", "public class LivingEntityRenderer {}"),
                Map.entry("net.minecraft.world.entity.LivingEntity", "public class LivingEntity {}"),
                Map.entry("yesman.epicfight.api.utils.math.OpenMatrix4f", "public class OpenMatrix4f {}"),
                Map.entry("yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch", "public class LivingEntityPatch {}"),
                Map.entry("yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer",
                        "public abstract class PatchedLivingEntityRenderer {}"),
                Map.entry("noppes.npcs.entity.EntityCustomNpc", """
                        public class EntityCustomNpc extends net.minecraft.world.entity.LivingEntity {
                            public final Display display = new Display();
                            public static class Display {
                                public boolean show = true;
                                public boolean isShowingLayers() { return show; }
                            }
                        }
                        """),
                Map.entry("noppes.npcs.client.renderer.RenderCustomNpc", """
                        public class RenderCustomNpc extends net.minecraft.client.renderer.entity.LivingEntityRenderer
                                implements top.bincnpcef.mixin.impl.IRenderCustomNpc {
                            public net.minecraft.client.renderer.entity.LivingEntityRenderer renderEntity;
                            public final java.util.List<Object> npclayers = new java.util.ArrayList<>();
                            public net.minecraft.client.renderer.entity.LivingEntityRenderer cnpcef$getRenderEntity() {
                                return renderEntity;
                            }
                        }
                        """),
                Map.entry("fixture.Regression", REGRESSION));
        var arguments = new ArrayList<String>();
        Path classes = Files.createDirectory(temporary.resolve("classes"));
        Path annotations = Path.of(Mixin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        arguments.addAll(java.util.List.of("-proc:none", "-encoding", "UTF-8", "-classpath",
                annotations.toString(), "-d", classes.toString()));
        for (var fixture : fixtures.entrySet()) {
            Path file = temporary.resolve(fixture.getKey().replace('.', '/') + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, "package " + fixture.getKey().substring(0, fixture.getKey().lastIndexOf('.'))
                    + ";\n" + fixture.getValue());
            arguments.add(file.toString());
        }
        for (String name : new String[]{"IRenderCustomNpc", "MixinPatchedLivingEntityRenderer"}) {
            arguments.add(Path.of("src/main/java/top/bincnpcef/mixin/impl", name + ".java").toString());
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Regression requires the project's JDK 21, not a JRE");
        assertEquals(0, compiler.run(null, null, null, arguments.toArray(String[]::new)));
        try (var loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            loader.loadClass("fixture.Regression").getMethod("run").invoke(null);
        }
    }

    @Test
    void redirectIsClientOnlyAndKeepsOriginalRenderArguments() throws Exception {
        var config = JsonParser.parseString(Files.readString(Path.of("src/main/resources/cnpcef.mixins.json"))).getAsJsonObject();
        for (String name : new String[]{"impl.IRenderCustomNpc", "impl.MixinPatchedLivingEntityRenderer"}) {
            assertTrue(config.getAsJsonArray("client").contains(new com.google.gson.JsonPrimitive(name)));
            assertFalse(config.getAsJsonArray("mixins").contains(new com.google.gson.JsonPrimitive(name)));
            assertFalse(config.getAsJsonArray("server").contains(new com.google.gson.JsonPrimitive(name)));
        }
        String source = Files.readString(Path.of("src/main/java/top/bincnpcef/mixin/impl/MixinPatchedLivingEntityRenderer.java"));
        assertEquals(1, source.split("@Redirect", -1).length - 1);
        assertTrue(source.contains("PatchedLivingEntityRenderer.class, remap = false"));
        assertTrue(source.contains("target = \"Ljava/util/List;iterator()Ljava/util/Iterator;\", ordinal = 0"));
        assertTrue(source.contains("require = 1"));
        assertFalse(source.contains("@Inject"));
        assertFalse(source.contains(".renderLayer("));
        assertFalse(source.contains(".clear("));
        assertFalse(source.contains(".add("));
        // The selector never consumes/replaces original equipment, patch, poses, or buffers.
        String body = source.substring(source.indexOf("if (entity instanceof"));
        for (String forbidden : new String[]{"entitypatch", "poses", "buffer", "poseStack", "packedLight",
                "partialTicks", "getEntity(", "getItem", "setItem", "new ", "entity =", "renderer ="}) {
            assertFalse(body.contains(forbidden), forbidden);
        }
        String accessor = Files.readString(Path.of("src/main/java/top/bincnpcef/mixin/impl/IRenderCustomNpc.java"));
        assertTrue(accessor.contains("RenderCustomNpc.class, remap = false"));
        assertTrue(accessor.contains("@Accessor(\"renderEntity\")"));
        assertTrue(accessor.contains("LivingEntityRenderer cnpcef$getRenderEntity()"));
    }

    private static final String REGRESSION = """
            import java.util.*;
            import com.mojang.blaze3d.vertex.PoseStack;
            import net.minecraft.client.renderer.MultiBufferSource;
            import net.minecraft.client.renderer.entity.LivingEntityRenderer;
            import net.minecraft.world.entity.LivingEntity;
            import noppes.npcs.entity.EntityCustomNpc;
            import noppes.npcs.client.renderer.RenderCustomNpc;
            import yesman.epicfight.api.utils.math.OpenMatrix4f;
            import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
            import top.bincnpcef.mixin.impl.MixinPatchedLivingEntityRenderer;
            public class Regression {
                public static void run() throws Exception {
                    var method = MixinPatchedLivingEntityRenderer.class.getDeclaredMethod("cnpcef$modelLayers",
                            List.class, LivingEntityRenderer.class, LivingEntityPatch.class, LivingEntity.class,
                            OpenMatrix4f[].class, MultiBufferSource.class, PoseStack.class, int.class, float.class);
                    method.setAccessible(true);
                    var selector = new MixinPatchedLivingEntityRenderer();
                    var npc = new EntityCustomNpc();
                    var renderer = new RenderCustomNpc();
                    var alex = new RenderCustomNpc();
                    var wide = new RenderCustomNpc();
                    Object wrapped = new Object(), mainHand = new Object(), offHand = new Object();
                    var original = new ArrayList<Object>(List.of(wrapped));
                    alex.npclayers.addAll(List.of(mainHand, offHand, "head", "chest", "legs", "feet"));
                    wide.npclayers.addAll(List.of("wide-main", "wide-armor"));
                    var originalCopy = List.copyOf(original);
                    var alexCopy = List.copyOf(alex.npclayers);
                    var wideCopy = List.copyOf(wide.npclayers);
                    // Deliberately distinct from the selected renderer's layers.
                    renderer.npclayers.add("outer-renderer");
                    Object[] context = {original, renderer, new LivingEntityPatch(), npc,
                            new OpenMatrix4f[]{new OpenMatrix4f()}, new MultiBufferSource(){}, new PoseStack(), 123, 0.5f};
                    Object[] savedContext = context.clone();
                    check(read(method.invoke(selector, context)).equals(originalCopy), "default/no proxy");
                    renderer.renderEntity = alex;
                    check(read(method.invoke(selector, context)).equals(alexCopy), "Alex uses true model layers once");
                    npc.display.show = false;
                    check(read(method.invoke(selector, context)).isEmpty(), "hidden Alex layers");
                    npc.display.show = true;
                    renderer.renderEntity = wide;
                    check(read(method.invoke(selector, context)).equals(wideCopy), "Alex to ordinary model");
                    renderer.renderEntity = null;
                    check(read(method.invoke(selector, context)).equals(originalCopy), "return to default");
                    renderer.renderEntity = new LivingEntityRenderer();
                    check(read(method.invoke(selector, context)).equals(originalCopy), "non-CNPC proxy fallback");
                    renderer.renderEntity = alex;
                    context[3] = new LivingEntity();
                    check(read(method.invoke(selector, context)).equals(originalCopy), "non-CNPC entity fallback");
                    context[3] = npc;
                    context[1] = new LivingEntityRenderer();
                    check(read(method.invoke(selector, context)).equals(originalCopy), "non-CNPC renderer fallback");
                    context[1] = renderer;
                    check(Arrays.equals(context, savedContext), "original NPC/patch/bones/buffer/light/time unchanged");
                    // No cached selection or visibility leaks between NPCs sharing this renderer.
                    npc.display.show = false;
                    context[3] = new EntityCustomNpc();
                    check(read(method.invoke(selector, context)).equals(alexCopy), "second NPC remains visible");
                    context[3] = npc;
                    check(read(method.invoke(selector, context)).isEmpty(), "first NPC remains hidden");
                    check(original.equals(originalCopy), "wrapped input list not mutated");
                    check(alex.npclayers.equals(alexCopy), "Alex shared layers not mutated");
                    check(wide.npclayers.equals(wideCopy), "ordinary shared layers not mutated");
                    check(renderer.npclayers.equals(List.of("outer-renderer")), "outer layers not mutated");
                }
                private static List<Object> read(Object value) {
                    var result = new ArrayList<Object>();
                    ((Iterator<?>) value).forEachRemaining(result::add);
                    return result;
                }
                private static void check(boolean condition, String message) {
                    if (!condition) throw new AssertionError(message);
                }
            }
            """;
}
