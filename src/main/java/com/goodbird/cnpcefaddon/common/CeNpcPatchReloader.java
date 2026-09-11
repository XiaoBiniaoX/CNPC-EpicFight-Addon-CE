package com.goodbird.cnpcefaddon.common;

import com.goodbird.cnpcefaddon.common.provider.CeNpcPatchProvider;
import com.goodbird.cnpcefaddon.common.provider.NpcBranchPatchProvider;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.server.ServerLifecycleHooks;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.shelmarow.combat_evolution.ai.CEPatchReloadListener;
import yesman.epicfight.api.data.reloader.MobPatchReloadListener;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.main.EpicFightSharedConstants;
import yesman.epicfight.model.armature.HumanoidArmature;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 读取按 CNPC efModel 分派的 CombatEvolution 数据包。
 *
 * <p>CE 原生 listener 以 EntityType 为键，不能用于 CNPC（全部 NPC 共用一个 EntityType）。
 * 此 listener 仅负责把 CE 原生格式转换为 CE provider，再交给我方分支 provider；复杂字段
 * 全部调用 CE 自己的 public parser，确保行为树及以后 CE 的格式变动保持同一语义。
 */
public final class CeNpcPatchReloader extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "ce_npc_epicfight_mobpatch";
    public static final String PATCH_TYPE = "COMBAT_EVOLUTION";
    /** 本 listener 在共享注册表里的归属标记，与 {@link CeNpcPatchOptional#PATCH_TYPE} 一致。 */
    public static final String OWNER = PATCH_TYPE;

    private static final Logger LOGGER = LoggerFactory.getLogger(CeNpcPatchReloader.class);
    /** 单轮重绑定最多打这么多条失败详情，其余汇总一条，避免大量 NPC 同时失败时刷屏。 */
    private static final int REBIND_ERROR_LOG_LIMIT = 5;
    private static final Gson GSON = new GsonBuilder().create();
    private static final Set<ResourceLocation> OWNED_KEYS = new HashSet<>();

    public CeNpcPatchReloader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager, ProfilerFiller profiler) {
        // 两阶段提交：先把本轮全部条目解析进临时表，只有解析成功的键才在第二阶段换入共享注册表。
        //
        // 旧实现是「先按 OWNED_KEYS 删掉上一轮的 CE 条目，再逐条解析并逐条写回」，于是任何一条
        // 解析失败（JSON/NBT、armature 非法、CE 字段解析器抛错、渲染器注册抛错）都会让那个键
        // 在本轮彻底消失——旧配置已删、新配置没进来，且没有回滚。改为暂存后整体换入，
        // 失败键保留上一轮的有效条目，只记错误。
        Map<ResourceLocation, CeNpcPatchProvider> parsedProviders = new HashMap<>();
        Map<ResourceLocation, CompoundTag> parsedTags = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            try {
                CompoundTag tag = TagParser.parseTag(entry.getValue().toString());
                CeNpcPatchProvider provider = deserialize(tag);
                // 数据包重载阶段用裸 armature 路径（与 CE 原生 :117 及我方 :195 一致）；
                // 只有服务端→客户端同步那条路径才需要 animmodels/*.json 前缀。
                ResourceLocation armatureLocation = ResourceLocation.parse(tag.getString("armature"));
                provider.armature = Armatures.getOrCreate(armatureLocation,
                        tag.getBoolean("humanoid") ? HumanoidArmature::new : Armature::new);
                CompoundTag syncTag = tag.copy();
                // CE names this field humanoid; RenderStorage follows EF's isHumanoid spelling.
                // Preserve CE's original key and add the renderer-facing alias only for this bridge.
                syncTag.putBoolean("isHumanoid", tag.getBoolean("humanoid"));
                syncTag.putString("id", entry.getKey().toString());
                syncTag.putString("cnpcefPatchType", PATCH_TYPE);

                // 单人游戏 / 物理客户端也必须在这里注册 EF 渲染器：processServerPacket 只在
                // 多人联机的同步路径上跑，单人时 CE 数据包全程只经过本 listener。缺这一步
                // 客户端拿不到 patched renderer，NPC 会以原版 CNPC 模型渲染、没有任何 EF 动画。
                // 与 AdvNpcPatchReloader :133 同一范式；CE 用 renderer 字段，且不支持 preset。
                if (EpicFightSharedConstants.isPhysicalClient()) {
                    com.goodbird.cnpcefaddon.client.render.RenderStorage.registerRenderer(
                            entry.getKey(), tag.getString("renderer"), syncTag);
                }

                parsedProviders.put(entry.getKey(), provider);
                parsedTags.put(entry.getKey(), syncTag);
            } catch (Exception e) {
                LOGGER.error("Failed to load CE NPC EpicFight mobpatch for {}: {}", entry.getKey(), describeError(e));
                NpcPatchReloadListener.loadErrors.put(entry.getKey(), describeError(e));
            }
        }

        // 第二阶段：撤回上一轮仍归本 listener 的键（定向撤回，不碰别的 reloader 后写的同名条目），
        // 再换入本轮解析成功的条目。解析失败的键不在 parsedProviders 里，其上一轮条目保持有效。
        for (ResourceLocation key : OWNED_KEYS) {
            if (parsedProviders.containsKey(key)) {
                continue;
            }
            if (NpcPatchReloadListener.branchPatchProvider.removeProviderIfOwnedBy(key, OWNER)) {
                NpcPatchReloadListener.AVAILABLE_MODELS.remove(key);
                NpcPatchReloadListener.TAGMAP.remove(key);
            }
        }
        OWNED_KEYS.clear();

        for (Map.Entry<ResourceLocation, CeNpcPatchProvider> parsed : parsedProviders.entrySet()) {
            ResourceLocation key = parsed.getKey();
            NpcPatchReloadListener.branchPatchProvider.addProvider(key, parsed.getValue(), OWNER);
            NpcPatchReloadListener.AVAILABLE_MODELS.add(key);
            NpcPatchReloadListener.TAGMAP.put(key, parsedTags.get(key));
            OWNED_KEYS.add(key);
        }

        // Existing CNPCs keep the old patch instance across a resource reload. Rebind them on
        // the server thread after the provider table is complete, otherwise one world contains
        // both old and new CE behavior trees until the NPC is recreated.
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> {
                int[] failed = new int[1];
                server.getAllLevels().forEach(level -> level.getAllEntities().forEach(entity -> {
                    if (entity instanceof EntityNPCInterface npc && npc.display instanceof com.goodbird.cnpcefaddon.mixin.IDataDisplay display
                            && display.hasEFModel()) {
                        try {
                            display.refreshEFModel();
                        } catch (Throwable t) {
                            // 旧实现整条吞掉：重绑定会改属性、AI、BossBar/BGM 与 capability，
                            // 失败后实体可能半初始化，而日志里没有任何线索。至少留一条可定位的记录。
                            // 仍不 rethrow：一个坏 NPC 不能中断整轮刷新。
                            failed[0]++;
                            if (failed[0] <= REBIND_ERROR_LOG_LIMIT) {
                                LOGGER.error("Failed to rebind CE patch for NPC {} (efModel {})",
                                        npc.getUUID(), display.getEFModel(), t);
                            }
                        }
                    }
                }));
                if (failed[0] > REBIND_ERROR_LOG_LIMIT) {
                    LOGGER.error("CE patch rebind failed on {} more NPCs (log limited to {} entries)",
                            failed[0] - REBIND_ERROR_LOG_LIMIT, REBIND_ERROR_LOG_LIMIT);
                }
            });
        }
    }

    /** Builds a CE provider using CE's own public field parsers; only its private two-line wiring is local. */
    public static CeNpcPatchProvider deserialize(CompoundTag tag) {
        CeNpcPatchProvider provider = new CeNpcPatchProvider();
        provider.weaponLivingMotions = CEPatchReloadListener.getWeaponLivingMotions(tag);
        provider.weaponAttackMotions = CEPatchReloadListener.getWeaponAttackMotions(tag);
        provider.guardHitMotions = CEPatchReloadListener.getGuardHitMotions(tag);
        provider.stunAnimations = CEPatchReloadListener.getStunAnimations(tag);
        provider.attributeMap = CEPatchReloadListener.getAttributeMap(tag);
        provider.faction = CEPatchReloadListener.getFaction(tag);
        provider.chasingSpeed = tag.contains("chasingSpeed") ? tag.getFloat("chasingSpeed") : 1.25F;
        provider.scale = CEPatchReloadListener.getScale(tag);
        provider.breakTime = CEPatchReloadListener.getBreakTime(tag);
        provider.recoverTime = CEPatchReloadListener.getRecoverTime(tag);
        provider.staminaRegenDelay = CEPatchReloadListener.getStaminaRegenDelay(tag);
        provider.hurtImpact = CEPatchReloadListener.getHurtImpact(tag);
        provider.guardHitImpact = CEPatchReloadListener.getGuardHitImpact(tag);
        provider.beParriedDamage = CEPatchReloadListener.getBeParriedDamage(tag);
        CEPatchReloadListener.initBossBarSetting(provider, tag);
        CEPatchReloadListener.initBossBGM(provider, tag);
        return provider;
    }

    public static boolean isCeTag(CompoundTag tag) {
        return tag != null && PATCH_TYPE.equals(tag.getString("cnpcefPatchType"));
    }

    @OnlyIn(Dist.CLIENT)
    public static MobPatchReloadListener.AbstractMobPatchProvider deserializeClient(CompoundTag tag) {
        return deserialize(tag);
    }

    private static String describeError(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isEmpty() ? error.getClass().getSimpleName() : message;
    }
}
