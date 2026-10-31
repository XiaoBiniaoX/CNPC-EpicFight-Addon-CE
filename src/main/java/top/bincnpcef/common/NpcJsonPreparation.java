package top.bincnpcef.common;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** 只有资源扫描与 JSON→NBT 进入框架准备线程，不访问实体或注册表。 */
public final class NpcJsonPreparation {
    public record Entry(ResourceLocation id, CompoundTag tag, Exception error) {}

    private NpcJsonPreparation() {}

    public static CompletableFuture<List<Entry>> prepare(ResourceManager resources, String directory,
                                                         Gson gson, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            var json = new HashMap<ResourceLocation, JsonElement>();
            SimpleJsonResourceReloadListener.scanDirectory(resources, directory, gson, json);
            return new ArrayList<>(json.entrySet());
        }, executor).thenCompose(entries -> {
            Entry[] parsed = new Entry[entries.size()];
            var tasks = new ArrayList<CompletableFuture<Void>>();
            int workers = Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
            int batch = Math.max(1, (entries.size() + workers - 1) / workers);
            for (int start = 0; start < entries.size(); start += batch) {
                int from = start, to = Math.min(entries.size(), start + batch);
                tasks.add(CompletableFuture.runAsync(() -> {
                    for (int i = from; i < to; i++) {
                        var entry = entries.get(i);
                        try {
                            parsed[i] = new Entry(entry.getKey(), TagParser.parseTag(entry.getValue().toString()), null);
                        } catch (Exception e) {
                            parsed[i] = new Entry(entry.getKey(), null, e);
                        }
                    }
                }, executor));
            }
            // allOf 保留原扫描遍历顺序；单准备线程也不会在自己的任务上 join 死锁。
            return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new))
                    .thenApply(unused -> List.of(parsed));
        });
    }
}
