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
        addLabel(new GuiLabel(312, "EpicFight Config:", this.guiLeft + 124, this.guiTop + 24, 0xffffff));
        this.addButton(new GuiButtonNop(this, 302, this.guiLeft + 230, this.guiTop + 22, 150, 20, curName, (b) -> {
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
