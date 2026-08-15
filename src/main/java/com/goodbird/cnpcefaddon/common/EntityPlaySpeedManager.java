package com.goodbird.cnpcefaddon.common;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.property.AnimationProperty.PlaybackSpeedModifier;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.main.EpicFightSharedConstants;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.util.Set;

import static yesman.epicfight.api.animation.property.AnimationProperty.StaticAnimationProperty.PLAY_SPEED_MODIFIER;

/**
 * NPC datapack play_speed support.
 * <p>
 * EF animations are shared globals. Installing a PLAY_SPEED_MODIFIER must
 * chain-preserve any existing modifier (attack-speed caps from EF / epicfight_extra).
 * Never replace the property outright — that was causing player weapons to ignore
 * attack-speed limits when this addon loaded.
 * <p>
 * Speeds are looked up per datapack patch (see {@link PatchKeyResolver}), because all
 * NPCs share one entity type while each mobpatch declares its own play_speed values.
 */
public class EntityPlaySpeedManager {

    private static final class ChainedModifier implements PlaybackSpeedModifier {
        private final PlaybackSpeedModifier previous;

        private ChainedModifier(PlaybackSpeedModifier previous) {
            this.previous = unwrap(previous);
        }

        private static PlaybackSpeedModifier unwrap(PlaybackSpeedModifier modifier) {
            PlaybackSpeedModifier current = modifier;
            while (current instanceof ChainedModifier chained) {
                current = chained.previous;
            }
            return current;
        }

        @Override
        public float modify(DynamicAnimation self, LivingEntityPatch<?> entitypatch,
                            float baseSpeed, float prevElapsedTime, float elapsedTime) {
            float speed = baseSpeed;
            if (previous != null) {
                try {
                    speed = previous.modify(self, entitypatch, speed, prevElapsedTime, elapsedTime);
                } catch (Throwable ignored) {
                }
            }
            return applyNpcSpeed(self, entitypatch, speed);
        }
    }

    public static final PlaybackSpeedModifier MODIFIER =
            (self, entitypatch, baseSpeed, prevElapsedTime, elapsedTime) ->
                    applyNpcSpeed(self, entitypatch, baseSpeed);

    private static float applyNpcSpeed(DynamicAnimation self, LivingEntityPatch<?> entitypatch, float baseSpeed) {
        float speed = baseSpeed;
        try {
            Entity original = entitypatch.getOriginal();
            if (original == null) {
                return speed;
            }
            // Keyed by the datapack patch, not the entity type: every NPC shares the
            // customnpcs:customnpc entity type but may run a different mobpatch.
            ResourceLocation patchKey = PatchKeyResolver.resolve(original);
            ResourceLocation animKey = resolveAnimKey(self);
            if (patchKey != null && animKey != null) {
                float cached = PlaySpeedCache.getSpeed(patchKey, animKey);
                if (cached != 1.0F) {
                    speed = cached * speed;
                }
            }
            return speed * rangedDrawFactor(self, entitypatch);
        } catch (Throwable t) {
            return baseSpeed;
        }
    }

    /**
     * Draw / charge animations an NPC actually plays while drawing a bow or crossbow.
     * <p>
     * The bow aim is the draw phase (slowed to match the fire interval); the crossbow reload
     * is its charge phase (same). The crossbow aim is a static ready pose after the charge
     * completes, not a timed phase, so it is deliberately excluded: slowing it down made the
     * NPC appear stuck in a slow-motion "firing" pose between shots.
     */
    private static final Set<String> RANGED_DRAW_ANIMS = Set.of(
            "biped/combat/bow_aim",
            "biped/combat/crossbow_reload"
    );

    /**
     * Slows the draw / reload phase of NPCs so one full draw lasts exactly as long as the
     * native fire interval ({@code delayMin/2}), instead of the animation being restarted
     * from frame zero on every shot (the NPC visibly keeps "re-pulling the string").
     * <p>
     * Only NPCs that are currently using the item are affected; players, idle NPCs and
     * non-ranged animations are untouched. The modifier never speeds anything up: NPCs
     * whose configured fire rate is faster than the animation simply play it at full speed.
     */
    private static float rangedDrawFactor(DynamicAnimation self, LivingEntityPatch<?> entitypatch) {
        try {
            Entity original = entitypatch.getOriginal();
            if (!(original instanceof EntityNPCInterface npc)) {
                return 1.0F;
            }
            if (!npc.isUsingItem()) {
                return 1.0F;
            }
            DynamicAnimation real = self.getRealAnimation().get();
            if (real == null) {
                return 1.0F;
            }
            ResourceLocation key = real.getRegistryName();
            if (key == null || !RANGED_DRAW_ANIMS.contains(key.getPath())) {
                return 1.0F;
            }
            int drawTicks = Math.max(npc.stats.ranged.getDelayMin() / 2, 1);
            float factor = real.getTotalTime() / (drawTicks * EpicFightSharedConstants.A_TICK);
            return Math.max(0.1F, Math.min(1.0F, factor));
        } catch (Throwable t) {
            return 1.0F;
        }
    }

    /**
     * Installs the draw slowdown modifier on the three ranged draw / reload animations.
     * Idempotent: re-invoking only re-wraps the existing chain without stacking factors.
     * Called once per datapack reload, when Epic Fight's animations are already registered.
     */
    public static void ensureRangedDrawModifiers() {
        for (String path : RANGED_DRAW_ANIMS) {
            ensureModifier(ResourceLocation.fromNamespaceAndPath("epicfight", path));
        }
    }

    private static ResourceLocation resolveAnimKey(DynamicAnimation self) {
        try {
            AssetAccessor<? extends StaticAnimation> real = self.getRealAnimation();
            if (real == null) {
                return null;
            }
            StaticAnimation anim = real.get();
            if (anim == null) {
                return null;
            }
            return anim.getRegistryName();
        } catch (Throwable t) {
            return null;
        }
    }

    public static void ensureModifier(ResourceLocation animKey) {
        if (animKey == null) {
            return;
        }

        // Remembered before the lookup: the modifier lives inside the StaticAnimation instance,
        // and a client resource reload replaces every instance with a fresh one. Recording the key
        // here - the single point every caller goes through - lets PlaySpeedCache re-install them
        // all once Epic Fight has rebuilt its animations.
        PlaySpeedCache.rememberInstalledKey(animKey);

        AnimationAccessor<? extends StaticAnimation> accessor = AnimationManager.byKey(animKey);
        if (accessor == null) {
            return;
        }
        StaticAnimation anim = accessor.get();
        if (anim == null) {
            return;
        }

        PlaybackSpeedModifier current = anim.getProperty(PLAY_SPEED_MODIFIER).orElse(null);
        // Idempotent: re-wrap only peels existing ChainedModifier and keeps foreign previous once.
        anim.addProperty(PLAY_SPEED_MODIFIER, new ChainedModifier(current));
    }

    public static void clearPatched() {
        // no-op retained for call sites; chaining is idempotent via unwrap
    }
}
