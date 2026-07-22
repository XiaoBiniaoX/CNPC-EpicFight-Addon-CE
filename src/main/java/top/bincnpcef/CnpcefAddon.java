package top.bincnpcef;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import noppes.npcs.CustomEntities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.bincnpcef.common.NpcPatchReloadListener;
import top.bincnpcef.common.network.NetworkHandler;
import top.bincnpcef.common.network.SPDatapackSync;
import yesman.epicfight.registry.entries.EpicFightAttributes;

@Mod(CnpcefAddon.MODID)
public class CnpcefAddon {
    public static final String MODID = "cnpcef";
    private static final Logger LOGGER = LoggerFactory.getLogger(CnpcefAddon.class);

    public CnpcefAddon(IEventBus modEventBus, ModContainer modContainer) {
        // MOD 事件总线：注册网络 payload
        modEventBus.addListener(this::registerPayloadHandlers);
        // MOD 事件总线：为 CNPC 注册 EF 属性（修复攻击 EF 能力 NPC 时 "Can't find attribute epicfight:weight" 崩溃）
        modEventBus.addListener(CnpcefAddon::onEntityAttributeModification);
        // 游戏事件总线：注册数据包重载监听器和数据包同步事件
        NeoForge.EVENT_BUS.addListener(this::addReloadListener);
        NeoForge.EVENT_BUS.addListener(this::onDatapackSync);
    }

    private void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        NetworkHandler.register(event);
    }

    /**
     * 为 CNPC EntityType 注册全部 EpicFight 属性。
     * EF 的 MixinLivingEntity 安全网在构造函数 TAIL 检查 patch，但 CNPC 的 patch 是动态附加的，
     * 构造时不存在，所以安全网失效。必须在此显式注册。
     * 注册 humanoid 模式的全部 EF 属性（common 5 个 + offhand 4 个）。
     *
     * 注：CustomEntities.entityCustomNpc 类型为 EntityType<? extends EntityNPCInterface>，
     * 而 event.add() 需要 EntityType<? extends LivingEntity>。虽然 EntityNPCInterface 继承 LivingEntity，
     * 但 Java 泛型不支持通配符协变转换，故使用未检查强制转换。
     */
    @SuppressWarnings("unchecked")
    private static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        // EntityNPCInterface 继承 LivingEntity，此转换在运行时安全
        EntityType<? extends LivingEntity> npcType =
            (EntityType<? extends LivingEntity>) (EntityType<?>) CustomEntities.entityCustomNpc;
        // common 属性
        event.add(npcType, EpicFightAttributes.WEIGHT);
        event.add(npcType, EpicFightAttributes.ARMOR_NEGATION);
        event.add(npcType, EpicFightAttributes.IMPACT);
        event.add(npcType, EpicFightAttributes.MAX_STRIKES);
        event.add(npcType, EpicFightAttributes.STUN_ARMOR);
        // humanoid 属性
        event.add(npcType, EpicFightAttributes.OFFHAND_ATTACK_SPEED);
        event.add(npcType, EpicFightAttributes.OFFHAND_MAX_STRIKES);
        event.add(npcType, EpicFightAttributes.OFFHAND_ARMOR_NEGATION);
        event.add(npcType, EpicFightAttributes.OFFHAND_IMPACT);
    }

    private void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new NpcPatchReloadListener());
    }

    private void onDatapackSync(OnDatapackSyncEvent event) {
        SPDatapackSync packet = new SPDatapackSync(
            NpcPatchReloadListener.getModelIdList(),
            NpcPatchReloadListener.getTagList()
        );

        ServerPlayer player = event.getPlayer();
        if (player != null) {
            // 单机模式下不向本机玩家发送同步包（其已通过本地重载获得数据）
            if (!player.getServer().isSingleplayerOwner(player.getGameProfile())) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        } else {
            event.getPlayerList().getPlayers().forEach(p -> PacketDistributor.sendToPlayer(p, packet));
        }
    }
}
