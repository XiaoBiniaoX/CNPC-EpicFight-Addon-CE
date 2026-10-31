package com.goodbird.cnpcefaddon.mixin.impl;

import com.goodbird.cnpcefaddon.client.NpcVisibility;
import com.goodbird.cnpcefaddon.client.NpcCorpsePose;
import com.goodbird.cnpcefaddon.mixin.IDataDisplay;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drive vanilla invisibility flags so EF renderers pick translucent / hidden paths.
 * <p>
 * PatchedLivingEntityRenderer uses:
 * {@code isVisible = !isInvisible(); isVisibleToPlayer = !isVisible && !isInvisibleTo(player)}
 * then alpha = isVisibleToPlayer ? 0.15 : 1.0 and translucent RenderType.
 */
@Mixin(EntityNPCInterface.class)
public abstract class MixinEntityNPCInvisible extends PathfinderMob {
    @Unique
    private NpcCorpsePose cnpcef$corpsePose;

    protected MixinEntityNPCInvisible(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void cnpcef$beforeNpcTick(CallbackInfo ci) {
        if (!level().isClientSide) return;
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        if (self.display == null || !((IDataDisplay) self.display).hasEFModel()) {
            cnpcef$corpsePose = null;
            return;
        }
        if (cnpcef$corpsePose == null) cnpcef$corpsePose = new NpcCorpsePose();
        cnpcef$corpsePose.beforeTick(self);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void cnpcef$afterNpcTick(CallbackInfo ci) {
        if (level().isClientSide && cnpcef$corpsePose != null) {
            cnpcef$corpsePose.afterTick((EntityNPCInterface) (Object) this);
        }
    }

    @Inject(method = "isInvisible", at = @At("HEAD"), cancellable = true)
    private void cnpcef$efInvisible(CallbackInfoReturnable<Boolean> cir) {
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        if (self.display == null || !((IDataDisplay) self.display).hasEFModel()) {
            return;
        }
        if (NpcVisibility.shouldHideFromClient(self) || NpcVisibility.shouldRenderTranslucent(self)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
    private void cnpcef$efInvisibleTo(Player player, CallbackInfoReturnable<Boolean> cir) {
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        if (self.display == null || !((IDataDisplay) self.display).hasEFModel()) {
            return;
        }
        if (NpcVisibility.shouldHideFromClient(self)) {
            // Fully hidden from this viewer
            cir.setReturnValue(true);
            return;
        }
        if (NpcVisibility.shouldRenderTranslucent(self)) {
            // Invisible flag set, but still visible to player → translucent path
            cir.setReturnValue(false);
        }
    }
}
