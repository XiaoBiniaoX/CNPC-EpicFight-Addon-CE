package com.goodbird.cnpcefaddon.client;

import com.goodbird.cnpcefaddon.common.patch.INpcPatch;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 在死亡 CNPC 重新进入客户端追踪范围时，仅对新建的 Epic Fight Animator 补播一次死亡动画。
 * <p>
 * Epic Fight 的死亡动画由死亡事件触发；区块重新加载或重新追踪不会再次派发该事件，新的
 * Animator 因而没有基础动画，尸体会回到默认站姿。这里通过 Animator 实例身份识别“新建”，
 * 让 {@code playDeathAnimation()} 使用数据包的 {@code LivingMotions.DEATH} 映射，故不依赖
 * 动画作者是否设置 {@code IS_DEATH_ANIMATION} 属性，第三方死亡动画也能正常推进到末帧。
 * <p>
 * 绝不每 tick 重播：第三方动画未声明该可选属性时，旧实现会反复从零开始，正是红色直立尸体
 * 的根因。隐藏尸体是 CNPC 的独立语义，必须完全跳过补播，避免隐藏状态与死亡 Animator 交叉。
 */
@Mod.EventBusSubscriber(modid = "cnpcefaddon", value = Dist.CLIENT)
public final class NpcCorpsePose {
    private static final Logger LOGGER = LoggerFactory.getLogger(NpcCorpsePose.class);

    /** UUID -> 当前已完成补播的 Animator 实例身份；Animator 重建后身份不同，才允许再播一次。 */
    private static final Map<UUID, Integer> RESTORED_ANIMATORS = new HashMap<>();

    private NpcCorpsePose() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            RESTORED_ANIMATORS.clear();
            return;
        }

        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof EntityNPCInterface npc)) {
                continue;
            }

            UUID uuid = npc.getUUID();
            if (npc.getHealth() > 0.0F) {
                // 同一 UUID 的 NPC 复活后，下一次真实死亡需要重新获得一次补播资格。
                RESTORED_ANIMATORS.remove(uuid);
                continue;
            }

            // CNPC 的隐藏尸体只保留复活计时，不应建立或驱动任何尸体视觉姿势。
            if (npc.stats.hideKilledBody) {
                RESTORED_ANIMATORS.remove(uuid);
                continue;
            }

            LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(npc, LivingEntityPatch.class);
            if (!(patch instanceof INpcPatch)) {
                continue;
            }

            try {
                int animatorIdentity = System.identityHashCode(patch.getAnimator());
                Integer restoredIdentity = RESTORED_ANIMATORS.get(uuid);
                if (restoredIdentity != null && restoredIdentity == animatorIdentity) {
                    continue;
                }

                patch.getAnimator().playDeathAnimation();
                RESTORED_ANIMATORS.put(uuid, animatorIdentity);
            } catch (Throwable t) {
                LOGGER.error("[cnpcef-fix] 死亡 NPC 姿势补播失败：entityId={} uuid={} hideKilledBody={} deathTime={}",
                        npc.getId(), uuid, npc.stats.hideKilledBody, npc.deathTime, t);
            }
        }
    }
}
