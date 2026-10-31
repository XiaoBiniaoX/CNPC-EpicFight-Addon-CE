package com.goodbird.cnpcefaddon.common;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Only file reading and JSON -> NBT preparation run in parallel; no game registries here. */
public abstract class NpcJsonReloadListener implements PreparableReloadListener {
    private final Gson gson;
    private final String directory;
    private final List<ResourceLocation> fallbackKeys;

    protected NpcJsonReloadListener(Gson gson, String directory, ResourceLocation... fallbackKeys) {
        this.gson = gson;
        this.directory = directory;
        this.fallbackKeys = List.of(fallbackKeys);
    }

    protected record ParsedTag(CompoundTag tag, Exception error) {
        CompoundTag getOrThrow() throws Exception {
            if (error != null) throw error;
            return tag;
        }
    }

    protected record Prepared(Map<ResourceLocation, ParsedTag> entries,
                              Map<ResourceLocation, List<ParsedTag>> fallbacks) {
    }

    @Override
    public final CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resources,
                                               ProfilerFiller preparationProfiler, ProfilerFiller applyProfiler,
                                               Executor preparationExecutor, Executor applyExecutor) {
        return CompletableFuture.supplyAsync(() -> {
            // Match vanilla's HashMap traversal order; parallel completion must not reorder apply.
            Map<ResourceLocation, JsonElement> json = new HashMap<>();
            SimpleJsonResourceReloadListener.scanDirectory(resources, directory, gson, json);
            return json;
        }, preparationExecutor).thenCompose(json -> {
            var entries = new ArrayList<>(json.entrySet());
            ParsedTag[] parsed = new ParsedTag[entries.size()];
            List<CompletableFuture<Void>> tasks = new ArrayList<>();
            // Bounded batches, not one future per NPC/behavior. Never join on this executor:
            // reload must also work when Minecraft supplies a single preparation worker.
            int workers = Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
            int batchSize = Math.max(1, (entries.size() + workers - 1) / workers);
            for (int start = 0; start < entries.size(); start += batchSize) {
                int from = start;
                int to = Math.min(entries.size(), start + batchSize);
                tasks.add(CompletableFuture.runAsync(() -> {
                    for (int i = from; i < to; i++) {
                        try {
                            parsed[i] = new ParsedTag(TagParser.parseTag(entries.get(i).getValue().toString()), null);
                        } catch (Exception e) {
                            parsed[i] = new ParsedTag(null, e);
                        }
                    }
                }, preparationExecutor));
            }
            Map<ResourceLocation, List<ParsedTag>> fallbacks = new LinkedHashMap<>();
            tasks.add(CompletableFuture.runAsync(() -> {
                for (ResourceLocation key : fallbackKeys) {
                    List<ParsedTag> stack = new ArrayList<>();
                    try {
                        ResourceLocation path = ResourceLocation.fromNamespaceAndPath(key.getNamespace(),
                                directory + "/" + key.getPath() + ".json");
                        List<Resource> resourcesStack = resources.getResourceStack(path);
                        for (int i = resourcesStack.size() - 1; i >= 0; i--) {
                            try (Reader reader = resourcesStack.get(i).openAsReader()) {
                                JsonElement element = GsonHelper.fromJson(gson, reader, JsonElement.class);
                                stack.add(new ParsedTag(TagParser.parseTag(element.toString()), null));
                            } catch (Exception e) {
                                stack.add(new ParsedTag(null, e));
                            }
                        }
                    } catch (Exception e) {
                        stack.add(new ParsedTag(null, e));
                    }
                    fallbacks.put(key, List.copyOf(stack));
                }
            }, preparationExecutor));
            return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).thenApply(unused -> {
                Map<ResourceLocation, ParsedTag> ordered = new LinkedHashMap<>();
                for (int i = 0; i < entries.size(); i++) ordered.put(entries.get(i).getKey(), parsed[i]);
                return new Prepared(ordered, fallbacks);
            });
        }).thenCompose(barrier::wait).thenAcceptAsync(prepared -> apply(prepared, resources, applyProfiler), applyExecutor);
    }

    protected abstract void apply(Prepared prepared, ResourceManager resources, ProfilerFiller profiler);
}
