package com.goodbird.cnpcefaddon.client;

import com.goodbird.cnpcefaddon.client.render.RenderStorage;
import top.theillusivec4.curios.client.render.CuriosLayer;
import yesman.epicfight.client.renderer.patched.entity.PatchedEntityRenderer;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;
import yesman.epicfight.compat.CuriosCompat;

/**
 * Makes Curios accessories show up on NPCs that render through an Epic Fight model.
 * <p>
 * Epic Fight already ships the adapter that draws a {@code CuriosLayer} onto a patched armature
 * ({@code CuriosCompat.PatchedCuriosLayerRenderer}), but it only installs that adapter on
 * {@code EntityType.PLAYER}:
 * <pre>
 * if (event.get(EntityType.PLAYER) instanceof PatchedLivingEntityRenderer r) {
 *     r.addPatchedLayerAlways(CuriosLayer.class, new PatchedCuriosLayerRenderer());
 * }
 * </pre>
 * NPCs handled by this addon render through their own patched renderers built in
 * {@link RenderStorage}, which are never touched by that listener - so accessories that a player
 * would wear correctly were invisible on an NPC.
 * <p>
 * The same official adapter instance is reused here rather than reimplementing the armature-to-
 * {@code HumanoidModel} transform, which keeps NPC accessory rendering identical to player
 * accessory rendering, including future Epic Fight fixes.
 * <p>
 * Only patched renderers that actually walk the vanilla layer list can host the adapter, i.e.
 * {@link PatchedLivingEntityRenderer}. {@code PCustomEntityRenderer} (a non-humanoid custom mesh)
 * has no vanilla layer pass, so it is skipped: nothing to attach to, and skipping keeps its
 * behaviour unchanged.
 * <p>
 * This class references Curios and is therefore only ever loaded from
 * {@link CuriosIntegration}, behind a mod-presence check.
 */
final class CuriosNpcLayerInstaller {

    private CuriosNpcLayerInstaller() {
    }

    /**
     * Installs Epic Fight's Curios adapter on every NPC renderer registered so far.
     *
     * @return how many renderers received the layer, for logging
     */
    static int install() {
        int installed = 0;

        for (var entry : RenderStorage.renderersMap.entrySet()) {
            PatchedEntityRenderer renderer = entry.getValue();

            if (!(renderer instanceof PatchedLivingEntityRenderer)) {
                continue;
            }

            // Raw type mirrors how Epic Fight registers this very adapter: the layer is typed for
            // LivingEntity / LivingEntityPatch, which every NPC renderer here satisfies, but the
            // renderer's own wildcards cannot express that through the generic signature.
            @SuppressWarnings({"rawtypes", "unchecked"})
            PatchedLivingEntityRenderer livingRenderer = (PatchedLivingEntityRenderer) renderer;

            // addPatchedLayerAlways overwrites an existing entry, so a repeated reload cannot
            // stack duplicate accessory draws.
            livingRenderer.addPatchedLayerAlways(CuriosLayer.class, new CuriosCompat.PatchedCuriosLayerRenderer());
            installed++;
        }

        return installed;
    }
}
