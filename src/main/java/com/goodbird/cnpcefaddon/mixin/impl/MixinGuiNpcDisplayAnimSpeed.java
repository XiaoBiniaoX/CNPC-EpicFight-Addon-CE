package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.common.AnimSpeedFactor;
import com.goodbird.cnpcefaddon.mixin.IDataDisplay;
import net.minecraft.client.gui.screens.Screen;
import noppes.npcs.client.gui.mainmenu.GuiNpcDisplay;
import noppes.npcs.entity.data.DataDisplay;
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
 * 在 NPC 的「显示」界面加一行动画攻击速度倍率输入框。
 *
 * <p>放在这个界面而不是属性界面，是因为值存在 {@code DataDisplay} 上
 * （原因见 {@link AnimSpeedFactor} 的类注释：只有 display 会随
 * {@code writeSpawnData} 同步到客户端，而动画速度必须双端一致）。
 * 本界面的 {@code save()} 走 {@code SPacketMenuSave(EnumMenuType.DISPLAY, display.save(...))}，
 * 正是 {@code MixinDataDisplay} 写读 NBT 的那条路径，因此无需新增网络包。
 *
 * <p>控件 id 取 20：CNPC 原生在本界面已用掉 textField 0/2/3/6/8/9/11 与 label 0~15
 * （已逐个核对反编译源码），20 是空位。
 *
 * <p><b>remap 陷阱（踩坑第 15/36 条同族）</b>：{@code init} 是 vanilla {@code Screen.init}
 * 的覆写，jar 内为 SRG 名 {@code m_7856_}，这里必须写<b>映射前的名字</b> {@code "init"} 并保留
 * 默认 remap，让注解处理器生成 refmap 条目；直接写 {@code "m_7856_"} 会导致 refmap 里没有该
 * 映射，注入静默不命中（首轮就踩了这个，靠对照既有 {@code MixinGuiNpcStats} 的 refmap 条目
 * {@code "init": "...GuiNpcStats;m_7856_()V"} 才发现）。
 * {@code unFocused} 是 CNPC 自有方法，必须 {@code remap = false}。
 * 两者签名已用 {@code javap -p} 核对：
 * {@code public void m_7856_()} 与 {@code public void unFocused(GuiTextFieldNop)}。
 */
@Mixin(GuiNpcDisplay.class)
public abstract class MixinGuiNpcDisplayAnimSpeed {

    private static final Logger LOGGER = LogManager.getLogger("cnpcefaddon");

    /** 本行控件 id；CNPC 原生未占用。 */
    private static final int ROW_ID = 20;

    @Shadow(remap = false)
    private DataDisplay display;

    @Inject(method = "init", at = @At("RETURN"))
    private void cnpcef$addAnimSpeedRow(CallbackInfo ci) {
        try {
            GuiBasic gui = (GuiBasic) (Object) this;
            if (this.display == null) {
                return;
            }
            float factor = ((IDataDisplay) this.display).getAnimSpeedFactor();
            // 原生最后一行（bossbar）在 guiTop+142，这里再下一格，沿用界面 23px 行距。
            int y = gui.guiTop + 165;
            gui.addLabel(new GuiLabel(ROW_ID, "cnpcefaddon.gui.animSpeed", gui.guiLeft + 5, y + 5));
            gui.addTextField(new GuiTextFieldNop(ROW_ID, (Screen) (Object) this,
                    gui.guiLeft + 120, y, 50, 20, "" + factor));
            gui.getTextField(ROW_ID).floatsOnly = true;
            gui.getTextField(ROW_ID).setMinMaxDefault(
                    AnimSpeedFactor.MIN, AnimSpeedFactor.MAX, AnimSpeedFactor.DEFAULT);
            gui.addLabel(new GuiLabel(ROW_ID + 1, "(0.01-10.0)", gui.guiLeft + 175, y + 5));
        } catch (Throwable t) {
            LOGGER.error("cnpcef-anim: anim speed row failed", t);
        }
    }

    @Inject(method = "unFocused", at = @At("TAIL"), remap = false)
    private void cnpcef$applyAnimSpeed(GuiTextFieldNop textfield, CallbackInfo ci) {
        if (textfield.id == ROW_ID && this.display != null) {
            ((IDataDisplay) this.display).setAnimSpeedFactor(textfield.getFloat());
        }
    }
}
