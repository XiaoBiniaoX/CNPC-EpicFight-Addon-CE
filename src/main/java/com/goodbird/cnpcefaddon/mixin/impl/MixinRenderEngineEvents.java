package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.client.GuiRenderContext;
import com.goodbird.cnpcefaddon.client.NpcVisibility;
import com.goodbird.cnpcefaddon.mixin.IDataDisplay;
import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.RenderLivingEvent;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.client.ClientEngine;
import yesman.epicfight.client.events.engine.RenderEngine;
import yesman.epicfight.config.ClientConfig;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * Renders EF-patched NPCs inside GUI entity displays.
 * <p>
 * Epic Fight's own {@code renderLivingEvent} has a GUI branch -- disable the compute shader,
 * render the armature model directly, cancel the event -- but it is gated on
 * {@code entitypatch instanceof LocalPlayerPatch}. An NPC patch fails that check and falls
 * through to the world path, which renders through the compute shader (no output in a GUI
 * framebuffer) and then cancels the event anyway because
 * {@code shouldRenderVanillaModel()} is false. The result is an NPC that renders nowhere in
 * the inventory / creation previews: neither the EF mesh nor the vanilla model.
 * <p>
 * This applies the player treatment to NPC patches. If for any reason the EF mesh cannot be
 * drawn -- no client-side renderer registered for the model, a throwing renderer -- Epic
 * Fight's handler is abandoned <em>without</em> cancelling the event, so the vanilla Custom
 * NPCs model is drawn instead. A GUI preview therefore always shows something.
 */
@Mixin(value = RenderEngine.Events.class, remap = false)
public class MixinRenderEngineEvents {

    @Inject(method = "renderLivingEvent", at = @At("HEAD"), cancellable = true)
    private static void cnpcef$skipInvisibleNpc(
            RenderLivingEvent.Pre<? extends LivingEntity, ? extends EntityModel<? extends LivingEntity>> event,
            CallbackInfo ci) {
        LivingEntity entity = event.getEntity();

        if (!(entity instanceof EntityNPCInterface npc)) {
            return;
        }

        if (NpcVisibility.shouldHideFromClient(npc)) {
            event.setCanceled(true);
            ci.cancel();
            return;
        }

        if (!cnpcef$isGuiRender(event)) {
            return;
        }

        if (npc.display == null || !((IDataDisplay) npc.display).hasEFModel()) {
            return;
        }

        if (cnpcef$renderInGui(event, npc)) {
            // Mesh drawn here; suppress both Epic Fight's handler and the vanilla model.
            event.setCanceled(true);
            ci.cancel();
            return;
        }

        if (GuiRenderContext.isActive()) {
            // The mesh could not be drawn. Skip Epic Fight's world path -- it would cancel
            // the event and render nothing -- so the vanilla Custom NPCs model shows up.
            ci.cancel();
        }
    }

    /**
     * A GUI preview is in progress, or the partial tick is an exact 0 / 1 -- the value
     * off-world callers pass, and one the world never produces.
     */
    private static boolean cnpcef$isGuiRender(
            RenderLivingEvent.Pre<? extends LivingEntity, ? extends EntityModel<? extends LivingEntity>> event) {
        if (GuiRenderContext.isActive()) {
            return true;
        }

        float partialTick = event.getPartialTick();

        return partialTick == 0.0F || partialTick == 1.0F;
    }

    /**
     * @return true when the EF mesh was drawn and the vanilla model should be suppressed
     */
    private static boolean cnpcef$renderInGui(
            RenderLivingEvent.Pre<? extends LivingEntity, ? extends EntityModel<? extends LivingEntity>> event,
            EntityNPCInterface npc) {
        if (npc.level() == null) {
            return false;
        }

        LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(npc, LivingEntityPatch.class);

        if (patch == null || !patch.overrideRender()) {
            return false;
        }

        ClientEngine clientEngine = ClientEngine.getInstance();

        if (clientEngine == null) {
            return false;
        }

        RenderEngine renderEngine = clientEngine.renderEngine;

        if (renderEngine == null || !renderEngine.hasRendererFor(npc)) {
            return false;
        }

        boolean computeShader = ClientConfig.activateComputeShader;

        try {
            // The compute shader path has no output in a GUI framebuffer.
            ClientConfig.activateComputeShader = false;
            renderEngine.renderEntityArmatureModel(npc, patch, event.getRenderer(),
                    event.getMultiBufferSource(), event.getPoseStack(),
                    event.getPackedLight(), event.getPartialTick());
        } catch (Throwable ignored) {
            // Fall back to the vanilla model rather than losing the entity entirely.
            return false;
        } finally {
            ClientConfig.activateComputeShader = computeShader;
        }

        return true;
    }
}
