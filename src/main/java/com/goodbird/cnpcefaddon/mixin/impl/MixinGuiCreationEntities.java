package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.client.gui.GuiStringSelection;
import com.goodbird.cnpcefaddon.client.ysm.YsmefOptional;
import com.goodbird.cnpcefaddon.common.NpcPatchReloadListener;
import com.goodbird.cnpcefaddon.mixin.IDataDisplay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.client.gui.model.GuiCreationEntities;
import noppes.npcs.client.gui.model.GuiCreationScreenInterface;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Vector;

@Mixin(value = GuiCreationEntities.class, priority = 1001)
public class MixinGuiCreationEntities extends GuiCreationScreenInterface {
    /**
     * Vertical offset of the EF config row.
     * <p>
     * {@code GuiCreationScreenInterface} anchors the NPC preview at {@code y = 200} (the
     * model's feet, growing upwards) and puts the rotation slider at {@code guiTop + 210}.
     * A 20px row at 190 therefore sits at the very bottom of the preview and directly on
     * top of the slider -- as low as the screen allows without covering either the slider
     * or the message label at {@code imageHeight - 10}. Being at the bottom also keeps it
     * out of the top row, where other addons add their own widgets.
     */
    private static final int EF_ROW_Y = 190;

    /**
     * YSM model row sits one slot above the EF config row, still inside the free bottom
     * band below the preview and above any other addon rows: {@code guiTop + 165}.
     */
    private static final int YSM_ROW_Y = 165;

    public MixinGuiCreationEntities() {
        super(null);
    }

    @Inject(method = "init",at = @At("TAIL"))
    public void init(CallbackInfo ci){
        Vector<String> list = new Vector<>();
        for(ResourceLocation resLoc : NpcPatchReloadListener.AVAILABLE_MODELS){
            list.add(resLoc.toString());
        }
        String curName = "cnpcefaddon.gui.selectConfig";
        if(((IDataDisplay)npc.display).hasEFModel()){
            curName = ((IDataDisplay)npc.display).getEFModel().toString();
        }

        int buttonX = this.guiLeft + this.xOffset + 142;
        int labelX = this.guiLeft + 130;

        addLabel(new GuiLabel(312, "cnpcefaddon.gui.efConfig", labelX, this.guiTop + EF_ROW_Y + 6, 0xffffff));
        this.addButton(new GuiButtonNop(this, 302, buttonX, this.guiTop + EF_ROW_Y, 120, 20, curName, (b) -> {
            setSubGui(new GuiStringSelection(this, "cnpcefaddon.gui.selectingConfig", list, name -> {
                ((IDataDisplay)npc.display).setEFModel(ResourceLocation.parse(name), false);
                getButton(302).setDisplayText(name);
            }));
        }));

        // YSM model selection: only meaningful when the EF config row already picked an Epic
        // Fight model and ysm_epicfight_compat (plus the YSM base mod) is installed. When the
        // mod is absent the list is empty and the button still opens but offers no choices.
        if (YsmefOptional.isLoaded()) {
            List<String> ysmModels = YsmefOptional.tryAvailableModelIds();
            String curYsm = "cnpcefaddon.gui.selectYsm";
            if (((IDataDisplay)npc.display).hasYsmModel()) {
                curYsm = ((IDataDisplay)npc.display).getYsmModel();
            }
            addLabel(new GuiLabel(313, "cnpcefaddon.gui.ysmConfig", labelX, this.guiTop + YSM_ROW_Y + 6, 0xffffff));
            this.addButton(new GuiButtonNop(this, 303, buttonX, this.guiTop + YSM_ROW_Y, 120, 20, curYsm, (b) -> {
                setSubGui(new GuiStringSelection(this, "cnpcefaddon.gui.selectingYsm", ysmModels, name -> {
                    ((IDataDisplay)npc.display).setYsmModel(name, false);
                    getButton(303).setDisplayText(name);
                }));
            }));
        }
    }

    @Override
    public void drawNpc(GuiGraphics graphics, LivingEntity entity, int x, int y, float zoomed, int rotation) {
        if(wrapper.subgui==null) {
            super.drawNpc(graphics, entity, x, y, zoomed, rotation);
        }
    }
}
