package top.bincnpcef.mixin.impl;

import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.gui.mainmenu.GuiNpcStats;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.constants.EnumMenuType;
import noppes.npcs.entity.data.DataStats;
import noppes.npcs.packets.Packets;
import noppes.npcs.packets.server.SPacketMenuSave;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.bincnpcef.api.IDataDisplay;
import top.bincnpcef.api.IDataMeleeAttackDesire;
import top.bincnpcef.common.NpcCombatParameters;

@Mixin(GuiNpcStats.class)
public class MixinGuiNpcStats {
    @Unique private static final int DESIRE_ID = 30;
    @Unique private static final int SPEED_ID = 31;
    @Shadow(remap = false) private DataStats stats;
    @Unique private boolean cnpcef$speedChanged;

    @Inject(method = "save", at = @At("HEAD"), remap = false)
    private void cnpcef$commitInputs(CallbackInfo ci) {
        GuiBasic gui = (GuiBasic) (Object) this;
        // Esc/快捷保存不一定先触发失焦；保存前显式提交两框。
        if (gui.getTextField(DESIRE_ID) != null) cnpcef$apply(gui.getTextField(DESIRE_ID), ci);
        if (gui.getTextField(SPEED_ID) != null) cnpcef$apply(gui.getTextField(SPEED_ID), ci);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void cnpcef$inputs(CallbackInfo ci) {
        GuiBasic gui = (GuiBasic) (Object) this;
        var npc = ((GuiNPCInterface) (Object) this).npc;
        gui.addLabel(new GuiLabel(DESIRE_ID, "cnpcef.gui.battle_desire", gui.guiLeft + 140,
                gui.guiTop + 37, "cnpcef.gui.battle_desire.tooltip"));
        gui.addTextField(new GuiTextFieldNop(DESIRE_ID, (Screen) (Object) this,
                gui.guiLeft + 220, gui.guiTop + 32, 50, 18,
                Float.toString(((IDataMeleeAttackDesire) stats.melee).cnpcef$getAttackDesire())));
        var desire = gui.getTextField(DESIRE_ID);
        desire.floatsOnly = true;
        desire.setMinMaxDefault(0.0F, 10.0F, 5.0F);
        desire.setTooltip(Tooltip.create(Component.translatable("cnpcef.gui.battle_desire.tooltip")));
        gui.addLabel(new GuiLabel(SPEED_ID, "cnpcef.gui.anim_speed", gui.guiLeft + 275,
                gui.guiTop + 37, "cnpcef.gui.anim_speed.tooltip"));
        gui.addTextField(new GuiTextFieldNop(SPEED_ID, (Screen) (Object) this,
                gui.guiLeft + 355, gui.guiTop + 32, 50, 18,
                Float.toString(((IDataDisplay) npc.display).cnpcef$getAnimSpeed())));
        var speed = gui.getTextField(SPEED_ID);
        speed.floatsOnly = true;
        speed.setMinMaxDefault(NpcCombatParameters.MIN_SPEED, NpcCombatParameters.MAX_SPEED, 1.0F);
        speed.setTooltip(Tooltip.create(Component.translatable("cnpcef.gui.anim_speed.tooltip")));
    }

    @Inject(method = "unFocused", at = @At("TAIL"), remap = false)
    private void cnpcef$apply(GuiTextFieldNop field, CallbackInfo ci) {
        if (field.id == DESIRE_ID) {
            ((IDataMeleeAttackDesire) stats.melee).cnpcef$setAttackDesire(field.getFloat());
        } else if (field.id == SPEED_ID) {
            var display = (IDataDisplay) ((GuiNPCInterface) (Object) this).npc.display;
            float old = display.cnpcef$getAnimSpeed();
            display.cnpcef$setAnimSpeed(field.getFloat());
            cnpcef$speedChanged |= old != display.cnpcef$getAnimSpeed();
        }
    }

    @Inject(method = "save", at = @At("TAIL"), remap = false)
    private void cnpcef$saveSpeed(CallbackInfo ci) {
        // STATS 不带 DataDisplay，必须走 CNPC 已有的 DISPLAY 权限/保存链。
        if (!cnpcef$speedChanged) return;
        var npc = ((GuiNPCInterface) (Object) this).npc;
        Packets.sendServer(new SPacketMenuSave(EnumMenuType.DISPLAY, npc.display.save(new CompoundTag())));
        cnpcef$speedChanged = false;
    }
}
