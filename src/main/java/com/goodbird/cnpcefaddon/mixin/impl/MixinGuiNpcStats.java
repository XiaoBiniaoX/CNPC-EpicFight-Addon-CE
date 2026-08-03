package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.api.IDataMeleeAttackDesire;
import net.minecraft.client.gui.screens.Screen;
import noppes.npcs.client.gui.mainmenu.GuiNpcStats;
import noppes.npcs.entity.data.DataMelee;
import noppes.npcs.entity.data.DataStats;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a "battle desire" input row to the NPC stats screen, directly below the aggro
 * range field (which occupies guiTop+10; this row sits at guiTop+32 in the same
 * x+220 column, which is otherwise empty on that line).
 * <p>
 * The value is persisted through {@link com.goodbird.cnpcefaddon.mixin.impl.MixinDataMelee}
 * (NBT key "MeleeAttackDesire") and consumed by
 * {@link com.goodbird.cnpcefaddon.mixin.impl.MixinEntityAIAttackTarget} to scale the
 * melee attack interval.
 * <p>
 * NOTE: {@code init} is a vanilla override in the jar's bytecode (SRG name
 * {@code m_7856_}), so this injection must keep default remapping -- no {@code remap=false}.
 * {@code unFocused} is a Custom NPCs method with its original name, so it does need
 * {@code remap=false}.
 */
@Mixin(GuiNpcStats.class)
public abstract class MixinGuiNpcStats {

    private static final Logger LOGGER = LogManager.getLogger("cnpcefaddon");

    @Shadow(remap = false)
    private DataStats stats;

    @Inject(method = "init", at = @At("RETURN"))
    private void cnpcef$addBattleDesireRow(CallbackInfo ci) {
        try {
            GuiBasic gui = (GuiBasic) (Object) this;
            DataMelee melee = this.stats.melee;
            float desire = melee == null ? 5.0F : ((IDataMeleeAttackDesire) (Object) melee).getAttackDesire();
            gui.addLabel(new GuiLabel(30, "战斗欲望", gui.guiLeft + 140, gui.guiTop + 37, "guihint.npcmeleeaggro"));
            gui.addTextField(new GuiTextFieldNop(30, (Screen) (Object) this, gui.guiLeft + 220, gui.guiTop + 32, 50, 18,
                    "" + desire));
            gui.getTextField(30).floatsOnly = true;
            gui.getTextField(30).setMinMaxDefault(0.0F, 10.0F, 5.0F);
        } catch (Throwable t) {
            LOGGER.error("cnpcef-ai: battle desire row failed", t);
        }
    }

    @Inject(method = "unFocused", at = @At("TAIL"), remap = false)
    private void cnpcef$applyBattleDesire(GuiTextFieldNop textfield, CallbackInfo ci) {
        if (textfield.id == 30 && this.stats.melee != null) {
            float desire = textfield.getFloat();
            ((IDataMeleeAttackDesire) (Object) this.stats.melee).setAttackDesire(desire);
            LOGGER.info("cnpcef-ai: gui set desire={}", desire);
        }
    }
}
