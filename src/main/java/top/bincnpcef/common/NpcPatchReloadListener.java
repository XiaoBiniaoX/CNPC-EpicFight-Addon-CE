package top.bincnpcef.common;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import noppes.npcs.CustomEntities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.bincnpcef.client.render.RenderStorage;
import yesman.epicfight.api.data.reloader.MobPatchReloadListener;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NpcPatchReloadListener extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("CNPC-EF-Addon");
    public static final String DIRECTORY = "npc_epicfight_mobpatch";
    private static final ResourceLocation FORCED_ARMATURE = ResourceLocation.parse("epicfight:entity/biped");

    public static Set<ResourceLocation> AVAILABLE_MODELS = new java.util.HashSet<>();
    public static Map<ResourceLocation, CompoundTag> TAGMAP = new HashMap<>();
    public static CnpcBranchPatchProvider branchPatchProvider = new CnpcBranchPatchProvider();
    public static Map<ResourceLocation, String> loadErrors = new HashMap<>();

    public NpcPatchReloadListener() {
        super(new GsonBuilder().create(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objectIn, ResourceManager resourceManager, ProfilerFiller profiler) {
        Set<ResourceLocation> tempModels = new java.util.HashSet<>();
        Map<ResourceLocation, CompoundTag> tempTags = new HashMap<>();
        CnpcBranchPatchProvider tempProvider = new CnpcBranchPatchProvider();
        Map<ResourceLocation, String> tempErrors = new HashMap<>();

        LOGGER.info("Loading NPC EF mobpatches, found {} entries", objectIn.size());

        for (Map.Entry<ResourceLocation, JsonElement> entry : objectIn.entrySet()) {
            ResourceLocation rl = entry.getKey();
            try {
                CompoundTag tag = TagParser.parseTag(entry.getValue().toString());
                tag.putString("armature", FORCED_ARMATURE.toString());
                normalizeAttributeTypes(tag);

                boolean clientSide = false;
                MobPatchReloadListener.AbstractMobPatchProvider provider =
                    MobPatchReloadListener.deserializeMobPatchProvider(
                        CustomEntities.entityCustomNpc, tag, clientSide, resourceManager);

                tempProvider.addProvider(rl, provider);
                tempModels.add(rl);
                tempTags.put(rl, MobPatchReloadListener.filterClientData(tag));
            } catch (Exception e) {
                tempErrors.put(rl, e.getMessage());
                LOGGER.error("Failed to load mobpatch {}", rl, e);
            }
        }

        AVAILABLE_MODELS = tempModels;
        TAGMAP = tempTags;
        branchPatchProvider = tempProvider;
        loadErrors = tempErrors;

        EpicFightCapabilities.ENTITY_PATCH_PROVIDER.putCustomEntityPatch(
            CustomEntities.entityCustomNpc, entity -> branchPatchProvider.get(entity));

        RenderStorage.registerRenderers(tempProvider, tempTags);
        LOGGER.info("NPC EF mobpatch reload complete: {} models loaded, {} errors", tempModels.size(), tempErrors.size());
    }

    public static void processServerPacket(List<ResourceLocation> modelIds, List<CompoundTag> tags) {
        CnpcBranchPatchProvider tempProvider = new CnpcBranchPatchProvider();
        Set<ResourceLocation> tempModels = new java.util.HashSet<>();
        Map<ResourceLocation, CompoundTag> tempTags = new HashMap<>();

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

                tempProvider.addProvider(rl, provider);
                tempModels.add(rl);
                tempTags.put(rl, tag);
            } catch (Exception e) {
                LOGGER.error("Failed to process server packet for mobpatch {}", rl, e);
            }
        }

        AVAILABLE_MODELS = tempModels;
        TAGMAP = tempTags;
        branchPatchProvider = tempProvider;

        EpicFightCapabilities.ENTITY_PATCH_PROVIDER.putCustomEntityPatch(
            CustomEntities.entityCustomNpc, entity -> branchPatchProvider.get(entity));

        RenderStorage.registerRenderers(tempProvider, tempTags);
    }

    public static List<ResourceLocation> getModelIdList() {
        return new ArrayList<>(AVAILABLE_MODELS);
    }

    public static List<CompoundTag> getTagList() {
        List<CompoundTag> list = new ArrayList<>();
        for (ResourceLocation rl : AVAILABLE_MODELS) {
            list.add(TAGMAP.get(rl));
        }
        return list;
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
