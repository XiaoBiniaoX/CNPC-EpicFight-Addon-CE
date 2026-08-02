package top.bincnpcef.mixin.impl;

import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.ai.EntityAIRangedAttack;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.bincnpcef.common.NpcBowDrawFlow;

/**
 * Makes Custom NPCs' ranged goal run a real item-use cycle when the NPC holds a bow or
 * crossbow, so Epic Fight's draw and reload poses actually appear.
 *
 * <p>Vanilla Custom NPCs behaviour is preserved for everything else: the projectile is still
 * launched by {@code performRangedAttack}, honouring the NPC's projectile slot, damage,
 * accuracy, burst and sound settings. Only the timing gains a draw / charge phase, and only
 * while a bow or crossbow is in the main hand.
 */
@Mixin(EntityAIRangedAttack.class)
public abstract class MixinEntityAIRangedAttack {

    @Shadow(remap = false)
    private EntityNPCInterface npc;

    @Inject(method = "tick", at = @At("HEAD"))
    private void cnpcef$driveDraw(CallbackInfo ci) {
        NpcBowDrawFlow.tickDraw(this.npc);
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void cnpcef$releaseDraw(CallbackInfo ci) {
        NpcBowDrawFlow.reset(this.npc);
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnoppes/npcs/entity/EntityNPCInterface;performRangedAttack(Lnet/minecraft/world/entity/LivingEntity;F)V"
            )
    )
    private void cnpcef$fireWhenDrawn(EntityNPCInterface npc, LivingEntity target, float distanceFactor) {
        if (!NpcBowDrawFlow.readyToFire(npc)) {
            // Still drawing: skip this shot, the next goal tick will retry.
            return;
        }

        npc.performRangedAttack(target, distanceFactor);
        NpcBowDrawFlow.onFired(npc);
    }
}