package com.goodbird.cnpcefaddon.client;

import com.goodbird.cnpcefaddon.common.patch.INpcPatch;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yesman.epicfight.api.animation.AnimationPlayer;
import yesman.epicfight.api.animation.property.AnimationProperty.ActionAnimationProperty;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * Restores the death pose of Custom NPCs corpses that were re-sent to the client.
 * <p>
 * Epic Fight starts a death animation exactly once, from {@code LivingEntityPatch.onDeath}, which
 * runs on {@code LivingDeathEvent}. Leaving and re-entering the tracking range does not replay
 * that event: the client discards the entity and builds a fresh patch with a fresh animator when
 * it comes back, so no death animation is playing at all. {@code MobPatch.commonMobUpdateMotion}
 * still sets {@code currentLivingMotion = DEATH} because health is 0, but that only names the
 * motion - with an empty animation player the armature stays in its default pose, which is why a
 * corpse came back standing in the weapon idle stance instead of lying down.
 * <p>
 * The same emptiness also disables Epic Fight's own hold-at-the-end mechanism:
 * {@code LivingEntityPatch.tick} keeps {@code deathTime} pinned at 19 only while
 * {@code !animPlayer.isEmpty() && !animPlayer.isEnd()}.
 * <p>
 * Replaying the animation through {@code Animator.playDeathAnimation()} is the smallest fix that
 * reuses Epic Fight's own entry point, so the corpse uses whatever death animation its datapack
 * declares for {@code LivingMotions.DEATH}. {@code ClientAnimator.playDeathAnimation} is itself
 * guarded by {@code IS_DEATH_ANIMATION}, so it will not restart an animation that is already
 * playing - no twitching, no repeated collapsing.
 * <p>
 * Only corpses are touched: an NPC is required to be dead ({@code health <= 0}) and to own one of
 * this addon's patches. Custom NPCs' respawn path ({@code killedtime} -> {@code reset()}) is not
 * involved at all, so revival keeps working exactly as before; once the NPC revives its health is
 * positive again and it drops out of this check.
 * <p>
 * Client only: the pose is a purely visual concern and the animator here is the client animator.
 */
@Mod.EventBusSubscriber(modid = "cnpcefaddon", value = Dist.CLIENT)
public final class NpcCorpsePose {

    private static final Logger LOGGER = LoggerFactory.getLogger(NpcCorpsePose.class);

    private NpcCorpsePose() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {
            return;
        }

        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof EntityNPCInterface npc)) {
                continue;
            }

            if (npc.getHealth() > 0.0F) {
                continue;
            }

            LivingEntityPatch<?> patch =
                    EpicFightCapabilities.getEntityPatch(npc, LivingEntityPatch.class);

            if (!(patch instanceof INpcPatch)) {
                // No Epic Fight model: Custom NPCs draws its own corpse, nothing to correct.
                continue;
            }

            try {
                AnimationPlayer player = patch.getAnimator().getPlayerFor(null);
                boolean playingDeathAnimation = player.getAnimation().get()
                        .getProperty(ActionAnimationProperty.IS_DEATH_ANIMATION)
                        .orElse(false);

                if (!playingDeathAnimation) {
                    patch.getAnimator().playDeathAnimation();
                }
            } catch (Throwable t) {
                LOGGER.error("[cnpcef-fix] corpse death pose restore failed", t);
            }
        }
    }
}
