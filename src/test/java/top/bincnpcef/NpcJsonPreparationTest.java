package top.bincnpcef;

import com.google.gson.Gson;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;
import top.bincnpcef.common.NpcJsonPreparation;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NpcJsonPreparationTest {
    @Test
    void singlePreparationThreadCompletesAllBatchesWithoutJoiningItself() throws Exception {
        var resources = new LinkedHashMap<ResourceLocation, Resource>();
        for (int i = 0; i < 12; i++) {
            byte[] json = ("{\"index\":" + i + "}").getBytes(StandardCharsets.UTF_8);
            resources.put(ResourceLocation.parse("cnpcef:npc_epicfight_mobpatch/model_" + i + ".json"),
                    new Resource(null, () -> new ByteArrayInputStream(json)));
        }
        // Valid JSON but not an SNBT compound: one bad entry must not discard its neighbors.
        resources.put(ResourceLocation.parse("cnpcef:npc_epicfight_mobpatch/bad.json"),
                new Resource(null, () -> new ByteArrayInputStream("[]".getBytes(StandardCharsets.UTF_8))));
        ResourceManager manager = (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> {
                    assertEquals("listResources", method.getName());
                    assertEquals("npc_epicfight_mobpatch", args[0]);
                    return resources;
                });
        var submitted = new AtomicInteger();
        var executor = Executors.newSingleThreadExecutor();
        try {
            var entries = NpcJsonPreparation.prepare(manager, "npc_epicfight_mobpatch", new Gson(), task -> {
                submitted.incrementAndGet();
                executor.execute(task);
            }).get(10, TimeUnit.SECONDS);
            assertTrue(submitted.get() >= 2 && submitted.get() <= 5, "scan plus at most four batches");
            assertEquals(13, entries.size());
            assertEquals(1, entries.stream().filter(entry -> entry.error() != null).count());
            for (var entry : entries) {
                if (entry.error() == null) {
                    int index = Integer.parseInt(entry.id().getPath().substring("model_".length()));
                    assertEquals(index, entry.tag().getInt("index"));
                } else {
                    assertEquals("bad", entry.id().getPath());
                    assertNull(entry.tag());
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
