package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.api.IDataMeleeAttackDesire;
import com.goodbird.cnpcefaddon.common.AnimSpeedFactor;
import com.goodbird.cnpcefaddon.mixin.IDataDisplay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.client.gui.mainmenu.GuiNpcStats;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.constants.EnumMenuType;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.packets.Packets;
import noppes.npcs.packets.server.SPacketMenuSave;
import noppes.npcs.entity.data.DataMelee;
import noppes.npcs.entity.data.DataStats;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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

    /** 战斗欲望行的控件 id。 */
    private static final int DESIRE_ID = 30;
    /** 动画攻速倍率行的控件 id（与欲望同行，放在其右侧）。 */
    private static final int ANIM_SPEED_ID = 31;

    @Shadow(remap = false)
    private DataStats stats;

    /**
     * 取本界面的 NPC。
     *
     * <p><b>不能用 {@code @Shadow} 声明 npc 字段</b>：它定义在父类
     * {@code GuiNPCInterface} 上，而 {@code @Shadow} 只在<b>目标类本身</b>的字节码里查找，
     * 找不到就抛 {@code InvalidMixinException: @Shadow field npc was not located in the
     * target class}，进而使 {@code CustomNpcs} 类初始化失败、启动崩溃（本轮实测）。
     *
     * <p>该字段是 public 的，直接转型访问即可，无需 shadow。
     */
    @Unique
    private EntityNPCInterface cnpcef$npc() {
        return ((GuiNPCInterface) (Object) this).npc;
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void cnpcef$addBattleDesireRow(CallbackInfo ci) {
        try {
            GuiBasic gui = (GuiBasic) (Object) this;
            DataMelee melee = this.stats.melee;
            float desire = melee == null ? 5.0F : ((IDataMeleeAttackDesire) (Object) melee).getAttackDesire();
            gui.addLabel(new GuiLabel(DESIRE_ID, "cnpcefaddon.gui.battleDesire", gui.guiLeft + 140, gui.guiTop + 37, "guihint.npcmeleeaggro"));
            gui.addTextField(new GuiTextFieldNop(DESIRE_ID, (Screen) (Object) this, gui.guiLeft + 220, gui.guiTop + 32, 50, 18,
                    "" + desire));
            gui.getTextField(DESIRE_ID).floatsOnly = true;
            gui.getTextField(DESIRE_ID).setMinMaxDefault(0.0F, 10.0F, 5.0F);

            // 动画攻速倍率与战斗欲望并排：两者都是战斗参数，且相乘构成最终动画速度
            // （play_speed × 欲望系数 × 本倍率），放在一起才便于对照调整。
            //
            // 值本身存在 DataDisplay 上（stats.melee 不进 writeSpawnData 网络包，
            // 客户端拿不到，动画速度必须双端一致），但「存哪」与「界面放哪」互不相干 ——
            // 上一版据此把输入框也放进外观页，那里本就拥挤且会与既有控件抢位。
            //
            // 位置：欲望同行 guiTop+32，x 从 +275 起。该行原生只用到 +220~+270（欲望本身）
            // 与 +275/+355（生命回复标签与输入框）在 guiTop+120 之后的行，此处 +275 为空位。
            EntityNPCInterface npc = cnpcef$npc();
            float animFactor = npc == null || npc.display == null
                    ? AnimSpeedFactor.DEFAULT
                    : ((IDataDisplay) npc.display).getAnimSpeedFactor();
            gui.addLabel(new GuiLabel(ANIM_SPEED_ID, "cnpcefaddon.gui.animSpeed", gui.guiLeft + 275, gui.guiTop + 37));
            gui.addTextField(new GuiTextFieldNop(ANIM_SPEED_ID, (Screen) (Object) this, gui.guiLeft + 355, gui.guiTop + 32, 50, 18,
                    "" + animFactor));
            gui.getTextField(ANIM_SPEED_ID).floatsOnly = true;
            gui.getTextField(ANIM_SPEED_ID).setMinMaxDefault(
                    AnimSpeedFactor.MIN, AnimSpeedFactor.MAX, AnimSpeedFactor.DEFAULT);
        } catch (Throwable t) {
            LOGGER.error("cnpcef-ai: battle desire row failed", t);
        }
    }

    @Inject(method = "unFocused", at = @At("TAIL"), remap = false)
    private void cnpcef$applyBattleDesire(GuiTextFieldNop textfield, CallbackInfo ci) {
        if (textfield.id == DESIRE_ID && this.stats.melee != null) {
            float desire = textfield.getFloat();
            ((IDataMeleeAttackDesire) (Object) this.stats.melee).setAttackDesire(desire);
        } else if (textfield.id == ANIM_SPEED_ID) {
            EntityNPCInterface npc = cnpcef$npc();
            if (npc != null && npc.display != null) {
                ((IDataDisplay) npc.display).setAnimSpeedFactor(textfield.getFloat());
            }
        }
    }

    /**
     * 补发一个 DISPLAY 存档包，否则动画攻速倍率永远存不下来。
     *
     * <p>本界面原生的 {@code save()} 只发
     * {@code SPacketMenuSave(EnumMenuType.STATS, stats.save(new CompoundTag()))}
     * （字节码已核：偏移 4 取 {@code EnumMenuType.STATS}，18 调 {@code DataStats.save}）。
     * 而倍率存在 {@code DataDisplay} 上（{@code stats.melee} 不进 {@code writeSpawnData}，
     * 客户端拿不到，动画速度必须双端一致），**完全不在这个包里** —— 于是关闭界面时
     * 服务端从未收到新值，重开界面读回的还是旧值，表现为「不论填什么都被重置为 1.0」。
     *
     * <p>这里在 {@code save()} 之后追加一个 DISPLAY 包。服务端对 DISPLAY 的处理与
     * 外观界面保存时完全相同（同一 {@code SPacketMenuSave} 分支，需 {@code NPC_DISPLAY} 权限），
     * 因此不需要新增网络包，也不改动任何 NBT 键。
     */
    @Inject(method = "save", at = @At("TAIL"), remap = false)
    private void cnpcef$saveDisplay(CallbackInfo ci) {
        try {
            EntityNPCInterface npc = cnpcef$npc();
            if (npc != null && npc.display != null) {
                Packets.sendServer(new SPacketMenuSave(EnumMenuType.DISPLAY,
                        npc.display.save(new CompoundTag())));
            }
        } catch (Throwable t) {
            LOGGER.error("cnpcef-ai: anim speed save failed", t);
        }
    }
}
