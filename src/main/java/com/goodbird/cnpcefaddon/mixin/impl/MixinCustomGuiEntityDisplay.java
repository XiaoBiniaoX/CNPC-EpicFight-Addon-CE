package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.client.GuiRenderContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import noppes.npcs.client.gui.custom.components.CustomGuiEntityDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Flags the GUI entity preview so the visibility gates can stand down inside it.
 * <p>
 * Every NPC preview in Custom NPCs -- the NPC inventory screen, the model creation tabs,
 * dialogs, quests, companion screens, scripted GUIs -- reaches the entity render dispatcher
 * through this one method. See {@link GuiRenderContext}.
 */
@Mixin(value = CustomGuiEntityDisplay.class, remap = false)
public class MixinCustomGuiEntityDisplay {

    @Inject(
            method = "drawEntity(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/Entity;IIFIIIFFZZ)V",
            at = @At("HEAD")
    )
    private static void cnpcef$enterGui(GuiGraphics graphics, Entity entity, int x, int y, float zoomed,
                                        int rotation, int xMouse, int yMouse, float guiLeft, float guiTop,
                                        boolean followCursor, boolean showRiders, CallbackInfo ci) {
        GuiRenderContext.push();
    }

    @Inject(
            method = "drawEntity(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/Entity;IIFIIIFFZZ)V",
            at = @At("RETURN")
    )
    private static void cnpcef$exitGui(GuiGraphics graphics, Entity entity, int x, int y, float zoomed,
                                       int rotation, int xMouse, int yMouse, float guiLeft, float guiTop,
                                       boolean followCursor, boolean showRiders, CallbackInfo ci) {
        GuiRenderContext.pop();
    }
}
