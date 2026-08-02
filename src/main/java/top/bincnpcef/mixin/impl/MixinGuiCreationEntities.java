package top.bincnpcef.mixin.impl;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.client.gui.model.GuiCreationEntities;
import noppes.npcs.client.gui.model.GuiCreationScreenInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.bincnpcef.client.gui.EfModelSelectionScreen;
import top.bincnpcef.common.NpcPatchReloadListener;
import top.bincnpcef.api.IDataDisplay;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = GuiCreationEntities.class, priority = 1001)
public class MixinGuiCreationEntities extends GuiCreationScreenInterface {
    public MixinGuiCreationEntities() {
        super(null);
    }

    /**
     * EF 配置行的垂直偏移：旋转滑块在 guiTop+210（GuiCreationScreenInterface L106，x=282），
     * 按钮放在滑块正上方 guiTop+190，即 NPC 展示底部、滑条之上（1.20.1 同款布局，不与
     * "Reset To NPC"(guiTop+46) 及右上角其它附属 mod 冲突）。
     */
    private static final int EF_ROW_Y = 190;

    @Inject(method = "init", at = @At("TAIL"))
    private void cnpcef$init(CallbackInfo ci) {
        List<String> list = new ArrayList<>();
        for (ResourceLocation resLoc : NpcPatchReloadListener.AVAILABLE_MODELS) {
            list.add(resLoc.toString());
        }
        String curName = "Select Config";
        if (((IDataDisplay) npc.display).cnpcef$hasEFModel()) {
            curName = ((IDataDisplay) npc.display).cnpcef$getEFModel().toString();
        }
        // 与下方旋转滑块同列（x=284）；label 放在滚动列表(x<=120)与滑块之间的空隙。
        int buttonX = this.guiLeft + this.xOffset + 142;
        int labelX = this.guiLeft + 130;
        addLabel(new GuiLabel(312, "EF Config:", labelX, this.guiTop + EF_ROW_Y + 6, 0xffffff));
        this.addButton(new GuiButtonNop(this, 302, buttonX, this.guiTop + EF_ROW_Y, 120, 20, curName, (b) -> {
            setSubGui(new EfModelSelectionScreen(this, "Selecting epicfight config:", list, name -> {
                ((IDataDisplay) npc.display).cnpcef$setEFModel(ResourceLocation.parse(name));
                getButton(302).setDisplayText(name);
            }));
        }));
    }

    @Override
    public void drawNpc(GuiGraphics graphics, LivingEntity entity, int x, int y, float zoomed, int rotation) {
        if (wrapper.subgui == null) {
            super.drawNpc(graphics, entity, x, y, zoomed, rotation);
        }
    }
}
