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
 * 红死尸（死亡 NPC 呈红色直立姿势）修复。
 * <p>
 * <b>2026-09-05 实测确认解决</b>：使用第三方 EF 附属死亡动画的自写数据包
 * （{@code "death": "epicfightx:biped/living/death"}，配合 cdmoveset 战斗动画）
 * 在服务端与单人游戏均不再复现红死尸直立。
 * <p>
 * <b>历次修复的三个阶段</b>（每一阶段都是独立成因，前一阶段的修复不覆盖后一阶段）：
 * <ol>
 *   <li><b>区块重新加载后红死尸直立</b>：EF 的死亡动画只在 {@code LivingDeathEvent} 播一次，
 *       区块重载不重放该事件，新建的 Animator 没有基础动画，尸体回到默认站姿。
 *       修复：本类在客户端 tick 里对「新建 Animator」补播一次。</li>
 *   <li><b>开启隐藏尸体后红死尸直立</b>：隐藏尸体是 CNPC 的独立语义，不应建立任何尸体姿势。
 *       修复：{@code hideKilledBody} 为真时完全跳过补播。</li>
 *   <li><b>使用其他 mod 的 EF 死亡动画时红死尸直立</b>（本次）：见 {@link #playDeathPose} 的说明
 *       —— EF 客户端版 {@code playDeathAnimation()} 的判重依赖动画的可选属性
 *       {@code IS_DEATH_ANIMATION}，第三方死亡动画普遍不声明它，导致每 tick 从零重播、
 *       动画永远走不到末帧，视觉上就是持续的红色直立尸体。</li>
 * </ol>
 *
 * <p>在死亡 CNPC 重新进入客户端追踪范围时，仅对新建的 Epic Fight Animator 补播一次死亡动画。
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

                // EF 客户端的 playDeathAnimation() 判重依赖动画的 IS_DEATH_ANIMATION 可选属性，
                // 第三方死亡动画（如 epicfightx:biped/living/death）不声明它，会被每 tick 重播；
                // 且它在缺 DEATH 映射时回退 EMPTY_ANIMATION 而非 BIPED_DEATH。
                // 因此这里自己按数据包的 DEATH 映射直接播一次，不走那条判重。
                if (!playDeathPose(patch)) {
                    continue;
                }
                RESTORED_ANIMATORS.put(uuid, animatorIdentity);
            } catch (Throwable t) {
                LOGGER.error("[cnpcef-fix] 死亡 NPC 姿势补播失败：entityId={} uuid={} hideKilledBody={} deathTime={}",
                        npc.getId(), uuid, npc.stats.hideKilledBody, npc.deathTime, t);
            }
        }
    }

    /**
     * 按数据包的 {@code LivingMotions.DEATH} 映射播一次死亡姿势。
     *
     * <p>不调用 EF 的 {@code playDeathAnimation()}：它以「当前动画是否带
     * {@code IS_DEATH_ANIMATION}」判重，而该属性是动画作者可选的，第三方死亡动画普遍不带，
     * 于是判重恒失败、每 tick 从零重播，动画永远走不到末帧，表现为红色直立尸体。
     * 它在缺映射时还会回退 {@code EMPTY_ANIMATION}，让尸体回到站姿。
     *
     * @return 是否确实播上了一条死亡动画；未配置 DEATH 映射时返回 false，交由 EF 原生行为处理
     */
    private static boolean playDeathPose(LivingEntityPatch<?> patch) {
        var animator = patch.getAnimator();
        var death = animator.getLivingAnimation(yesman.epicfight.api.animation.LivingMotions.DEATH, null);

        if (death == null || yesman.epicfight.api.animation.AnimationManager.checkNull(death)) {
            return false;
        }

        animator.playAnimation(death, 0.0F);
        return true;
    }
}
