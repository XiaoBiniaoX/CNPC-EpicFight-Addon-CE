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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the production view against API stand-ins, including EF's probe/cache sequence. */
class ArmorProbeSlotsTest {
    @TempDir Path temporary;

    @Test
    void liveViewIsolatesEverySlotRestoresItemsAndPreventsSharedEmptyCache() throws Exception {
        Map<String, String> fixtures = Map.of(
                "net.minecraft.world.entity.EquipmentSlot", """
                        public enum EquipmentSlot {
                            MAINHAND, OFFHAND, FEET, LEGS, CHEST, HEAD;
                            public int getIndex() { return ordinal() - 2; }
                        }
                        """,
                "net.minecraft.world.item.ItemStack", """
                        public class ItemStack {
                            public static final ItemStack EMPTY = new ItemStack("empty");
                            public final String item;
                            public final String components;
                            public ItemStack(String components) {
                                this.item = components.split(":")[0];
                                this.components = components;
                            }
                        }
                        """,
                "net.minecraft.world.entity.LivingEntity", """
                        public class LivingEntity {
                            public final java.util.List<net.minecraft.world.item.ItemStack> slots =
                                new java.util.ArrayList<>(java.util.Collections.nCopies(4,
                                    net.minecraft.world.item.ItemStack.EMPTY));
                            public Iterable<net.minecraft.world.item.ItemStack> getArmorSlots() { return slots; }
                            public net.minecraft.world.item.ItemStack getItemBySlot(EquipmentSlot slot) {
                                return slots.get(slot.getIndex());
                            }
                            public void setItemSlot(EquipmentSlot slot, net.minecraft.world.item.ItemStack item) {
                                slots.set(slot.getIndex(), item);
                            }
                        }
                        """,
                "noppes.npcs.entity.EntityNPCInterface", """
                        public class EntityNPCInterface extends net.minecraft.world.entity.LivingEntity {
                            public int writes;
                            public Iterable<net.minecraft.world.item.ItemStack> getArmorSlots() {
                                return new java.util.ArrayList<>(slots);
                            }
                            public void setItemSlot(net.minecraft.world.entity.EquipmentSlot slot,
                                    net.minecraft.world.item.ItemStack item) {
                                writes++;
                                super.setItemSlot(slot, item);
                            }
                        }
                        """,
                "yesman.epicfight.client.renderer.patched.layer.WearableItemLayer", "public class WearableItemLayer {}",
                "fixture.Regression", REGRESSION);
        var arguments = new ArrayList<String>();
        Path classes = Files.createDirectory(temporary.resolve("classes"));
        Path annotations = Path.of(Mixin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        arguments.addAll(List.of("-proc:none", "-encoding", "UTF-8", "-classpath",
                annotations.toString(), "-d", classes.toString()));
        for (var fixture : fixtures.entrySet()) {
            Path file = temporary.resolve(fixture.getKey().replace('.', '/') + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, "package " + fixture.getKey().substring(0, fixture.getKey().lastIndexOf('.'))
                    + ";\n" + fixture.getValue());
            arguments.add(file.toString());
        }
        arguments.add(Path.of("src/main/java/top/bincnpcef/mixin/impl/MixinWearableItemLayer.java").toString());
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        assertEquals(0, compiler.run(null, null, null, arguments.toArray(String[]::new)));
        try (var loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            loader.loadClass("fixture.Regression").getMethod("run").invoke(null);
        }
    }

    @Test
    void redirectIsClientOnlyAndHasNoOptionalLibraryOrGlobalEntityHook() throws Exception {
        var config = JsonParser.parseString(Files.readString(Path.of("src/main/resources/cnpcef.mixins.json"))).getAsJsonObject();
        var name = new com.google.gson.JsonPrimitive("impl.MixinWearableItemLayer");
        assertTrue(config.getAsJsonArray("client").contains(name));
        assertFalse(config.getAsJsonArray("mixins").contains(name));
        assertFalse(config.getAsJsonArray("server").contains(name));
        String source = Files.readString(Path.of("src/main/java/top/bincnpcef/mixin/impl/MixinWearableItemLayer.java"));
        assertEquals(1, source.split("@Redirect", -1).length - 1);
        assertTrue(source.contains("WearableItemLayer.class, remap = false"));
        assertTrue(source.contains("method = \"getArmorModel\""));
        assertTrue(source.contains("Lnet/minecraft/world/entity/LivingEntity;getArmorSlots()Ljava/lang/Iterable;"));
        assertTrue(source.contains("require = 1"));
        for (String forbidden : new String[]{"@Inject", "software.bernie", "net.kenddie", "java.lang.reflect",
                "ARMOR_MODELS", "getMainHand", "getOffhand", "inventory.", "setReturnValue"}) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    private static final String REGRESSION = """
            import java.util.*;
            import java.lang.reflect.*;
            import net.minecraft.world.entity.*;
            import net.minecraft.world.item.ItemStack;
            import noppes.npcs.entity.EntityNPCInterface;
            import top.bincnpcef.mixin.impl.MixinWearableItemLayer;
            public class Regression {
                private static final EquipmentSlot[] ORDER = {
                    EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD };
                private static final EquipmentSlot[] RENDER_ORDER = {
                    EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HEAD };
                public static void run() throws Exception {
                    var method = MixinWearableItemLayer.class.getDeclaredMethod("cnpcef$armorProbeSlots", LivingEntity.class);
                    method.setAccessible(true);
                    var npc = new EntityNPCInterface();
                    for (int i = 0; i < 4; i++) npc.slots.set(i, new ItemStack("slot-" + i + ":dye,trim,enchants"));
                    var saved = List.copyOf(npc.slots);
                    var view = view(method.invoke(null, npc));
                    check(view.size() == 4 && view.equals(saved), "feet/legs/chest/head ordering");
                    // Unpatched CNPC snapshots fail the same EF probe, poisoning the item-level cache.
                    var cache = new HashMap<String, Boolean>();
                    cache.put(saved.get(2).item, probe(view(npc.getArmorSlots()), npc, EquipmentSlot.CHEST, saved.get(2)));
                    check(!cache.get(saved.get(2).item), "old NPC snapshot leaves outer model on HEAD");
                    var player = new LivingEntity();
                    var playerChest = new ItemStack("slot-2:different-components");
                    player.slots.set(2, playerChest);
                    check(!cache.get(player.slots.get(2).item), "player shares the broken NPC item cache");
                    for (EquipmentSlot slot : ORDER) {
                        boolean visible = probe(view, npc, slot, saved.get(slot.getIndex()));
                        check(visible, "correct single-slot visibility: " + slot);
                        check(npc.slots.equals(saved), "all original stack references/components restored: " + slot);
                        cache.put(saved.get(slot.getIndex()).item, visible);
                    }
                    check(cache.get(player.slots.get(2).item), "NPC-first cache is also valid for players");
                    check(method.invoke(null, player) == player.slots, "non-CNPC original iterable identity");
                    check(probe(view(method.invoke(null, player)), player, EquipmentSlot.CHEST, playerChest),
                        "player-first baking stays valid");
                    check(player.slots.get(2) == playerChest, "player stack identity restored");
                    var replacement = new ItemStack("changed");
                    npc.setItemSlot(EquipmentSlot.CHEST, replacement);
                    check(view.get(2) == replacement, "existing view tracks external equipment changes");
                    check(view.set(2, saved.get(2)) == replacement, "set returns previous value");
                    var secondNpc = new EntityNPCInterface();
                    var second = view(method.invoke(null, secondNpc));
                    check(second.stream().allMatch(s -> s == ItemStack.EMPTY), "empty second NPC independent");
                    check(view.set(0, null) == saved.get(0) && view.get(0) == ItemStack.EMPTY, "null normalized");
                    view.set(0, saved.get(0));
                    int writes = npc.writes;
                    for (int index : new int[]{-1, 4}) {
                        try { view.get(index); throw new AssertionError("get bounds"); }
                        catch (IndexOutOfBoundsException expected) {}
                        try { view.set(index, replacement); throw new AssertionError("set bounds"); }
                        catch (IndexOutOfBoundsException expected) {}
                    }
                    check(npc.writes == writes, "invalid index never mutates inventory");
                    try { view.add(replacement); throw new AssertionError("fixed size add"); }
                    catch (UnsupportedOperationException expected) {}
                    try { view.remove(0); throw new AssertionError("fixed size remove"); }
                    catch (UnsupportedOperationException expected) {}
                    check(npc.slots.equals(saved), "final NPC contents preserved");
                    check(secondNpc.writes == 0 && second.stream().allMatch(s -> s == ItemStack.EMPTY), "no cross-NPC state");
                    check(npc.getArmorSlots() != npc.slots, "ordinary CNPC snapshot contract unchanged");
                }
                @SuppressWarnings("unchecked")
                private static List<ItemStack> view(Object value) { return (List<ItemStack>) value; }
                // EF empties four slots, probes MC CHEST/LEGS/FEET/HEAD, restores, then converts Geo.
                // CHEST/FEET/HEAD reuse outer; LEGS uses inner. Geo honors the final visible parts.
                private static boolean probe(List<ItemStack> view, LivingEntity entity, EquipmentSlot slot, ItemStack item) {
                    var saved = new ArrayList<ItemStack>();
                    for (EquipmentSlot s : ORDER) saved.add(entity.getItemBySlot(s));
                    for (int i = 0; i < 4; i++) view.set(i, ItemStack.EMPTY);
                    view.set(slot.getIndex(), item);
                    EquipmentSlot inner = null, outer = null;
                    for (EquipmentSlot s : RENDER_ORDER) {
                        if (entity.getItemBySlot(s) == ItemStack.EMPTY) continue;
                        if (s == EquipmentSlot.LEGS) inner = s; else outer = s;
                    }
                    for (int i = 0; i < 4; i++) view.set(i, saved.get(i));
                    return slot == EquipmentSlot.LEGS ? inner == slot : outer == slot;
                }
                private static void check(boolean condition, String message) {
                    if (!condition) throw new AssertionError(message);
                }
            }
            """;
}
