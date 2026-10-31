package top.bincnpcef.common;

import com.google.gson.GsonBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import noppes.npcs.CustomEntities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.bincnpcef.common.network.SPDatapackSync;
import yesman.epicfight.api.data.reloader.MobPatchReloadListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.world.entity.Entity;

public class NpcPatchReloadListener implements PreparableReloadListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("CNPC-EF-Addon");
    public static final String DIRECTORY = "npc_epicfight_mobpatch";
    private static final ResourceLocation FORCED_ARMATURE = ResourceLocation.parse("epicfight:entity/biped");

    // 单机逻辑服务端与客户端不共享 provider/数据，代际一次发布。
    private record Snapshot(CnpcBranchPatchProvider provider, Map<ResourceLocation, CompoundTag> tags,
                            Map<ResourceLocation, String> errors) {}
    private static volatile Snapshot server = new Snapshot(new CnpcBranchPatchProvider(), Map.of(), Map.of());
    private static volatile Snapshot client = new Snapshot(new CnpcBranchPatchProvider(), Map.of(), Map.of());

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resources,
                                          ProfilerFiller preparationProfiler, ProfilerFiller applyProfiler,
                                          Executor preparationExecutor, Executor applyExecutor) {
        return NpcJsonPreparation.prepare(resources, DIRECTORY, new GsonBuilder().create(), preparationExecutor)
                .thenCompose(barrier::wait).thenAcceptAsync(entries -> apply(entries, resources), applyExecutor);
    }

    private void apply(List<NpcJsonPreparation.Entry> entries, ResourceManager resourceManager) {
        Set<ResourceLocation> tempModels = new java.util.HashSet<>();
        Map<ResourceLocation, CompoundTag> tempTags = new LinkedHashMap<>();
        CnpcBranchPatchProvider tempProvider = new CnpcBranchPatchProvider();
        Map<ResourceLocation, String> tempErrors = new HashMap<>();

        LOGGER.info("Loading NPC EF mobpatches, found {} entries", entries.size());

        for (NpcJsonPreparation.Entry entry : entries) {
            ResourceLocation rl = entry.id();
            try {
                if (entry.error() != null) throw entry.error();
                CompoundTag tag = entry.tag();
                tag.putString("armature", FORCED_ARMATURE.toString());
                normalizeAttributeTypes(tag);

                boolean clientSide = false;
                MobPatchReloadListener.AbstractMobPatchProvider provider =
                    MobPatchReloadListener.deserializeMobPatchProvider(
                        CustomEntities.entityCustomNpc, tag, clientSide, resourceManager);

                tempProvider.addProvider(rl, provider, tag);
                tempModels.add(rl);
                tempTags.put(rl, MobPatchReloadListener.filterClientData(tag));
            } catch (Exception e) {
                tempErrors.put(rl, e.toString());
                LOGGER.error("Failed to load mobpatch {}", rl, e);
            }
        }

        server = new Snapshot(tempProvider, java.util.Collections.unmodifiableMap(tempTags), Map.copyOf(tempErrors));

        LOGGER.info("NPC EF mobpatch reload complete: {} models loaded, {} errors", tempModels.size(), tempErrors.size());
    }

    public static void processServerPacket(List<ResourceLocation> modelIds, List<CompoundTag> tags) {
        CnpcBranchPatchProvider tempProvider = new CnpcBranchPatchProvider();
        Set<ResourceLocation> tempModels = new java.util.HashSet<>();
        Map<ResourceLocation, CompoundTag> tempTags = new LinkedHashMap<>();
        Map<ResourceLocation, String> errors = new HashMap<>();
        if (modelIds.size() != tags.size()) throw new IllegalArgumentException("Mismatched NPC model/tag lists");

        for (int i = 0; i < modelIds.size(); i++) {
            ResourceLocation rl = modelIds.get(i);
            CompoundTag tag = tags.get(i);
            try {
                CompoundTag fullTag = tag.copy();
                fullTag.putString("armature", FORCED_ARMATURE.toString());
                normalizeAttributeTypes(fullTag);

                MobPatchReloadListener.AbstractMobPatchProvider provider =
                    MobPatchReloadListener.deserializeMobPatchProvider(
                        CustomEntities.entityCustomNpc, fullTag, true, null);

                tempProvider.addProvider(rl, provider, fullTag);
                tempModels.add(rl);
                tempTags.put(rl, tag.copy());
            } catch (Exception e) {
                errors.put(rl, e.toString());
                LOGGER.error("Failed to process server packet for mobpatch {}", rl, e);
            }
        }

        client = new Snapshot(tempProvider, java.util.Collections.unmodifiableMap(tempTags), Map.copyOf(errors));

    }

    public static CnpcBranchPatchProvider providerFor(Entity entity) {
        return (entity.level().isClientSide() ? client : server).provider();
    }

    public static Set<ResourceLocation> clientModels() {
        return client.tags().keySet();
    }

    public static Map<ResourceLocation, CompoundTag> clientTags() {
        var copy = new LinkedHashMap<ResourceLocation, CompoundTag>();
        client.tags().forEach((id, tag) -> copy.put(id, tag.copy()));
        return copy;
    }

    public static SPDatapackSync syncPacket() {
        Snapshot snapshot = server;
        var ids = new ArrayList<ResourceLocation>();
        var tags = new ArrayList<CompoundTag>();
        snapshot.tags().forEach((id, tag) -> {
            ids.add(id);
            tags.add(tag.copy());
        });
        return new SPDatapackSync(ids, tags);
    }

    /**
     * EF 的 deserializeAttributes 对 impact/armor_negation/stun_armor 只检查 TAG_DOUBLE，
     * 对 max_strikes 只检查 TAG_INT。
     * 但 JSON 整数（如 "impact": 2）被 TagParser.parseTag 解析为 IntTag，
     * 导致 tag.contains("impact", TAG_DOUBLE) 返回 false，使用默认值 0.5。
     * 此方法把数值类型转换为 EF 期望的类型。
     */
    private static void normalizeAttributeTypes(CompoundTag tag) {
        if (!tag.contains("attributes", Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag attrs = tag.getCompound("attributes");
        normalizeToDouble(attrs, "impact");
        normalizeToDouble(attrs, "armor_negation");
        normalizeToDouble(attrs, "stun_armor");
        normalizeToInt(attrs, "max_strikes");
        tag.put("attributes", attrs);
    }

    private static void normalizeToDouble(CompoundTag attrs, String key) {
        if (attrs.contains(key, Tag.TAG_INT)) {
            attrs.putDouble(key, attrs.getInt(key));
        } else if (attrs.contains(key, Tag.TAG_LONG)) {
            attrs.putDouble(key, attrs.getLong(key));
        } else if (attrs.contains(key, Tag.TAG_FLOAT)) {
            attrs.putDouble(key, attrs.getFloat(key));
        }
    }

    private static void normalizeToInt(CompoundTag attrs, String key) {
        if (attrs.contains(key, Tag.TAG_DOUBLE)) {
            attrs.putInt(key, (int) attrs.getDouble(key));
        } else if (attrs.contains(key, Tag.TAG_FLOAT)) {
            attrs.putInt(key, (int) attrs.getFloat(key));
        } else if (attrs.contains(key, Tag.TAG_LONG)) {
            attrs.putInt(key, (int) attrs.getLong(key));
        }
    }
}
