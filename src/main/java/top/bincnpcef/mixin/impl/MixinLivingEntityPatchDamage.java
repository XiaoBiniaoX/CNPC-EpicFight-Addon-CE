package top.bincnpcef.mixin.impl;

import net.minecraft.world.InteractionHand;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bincnpcef.common.AddonConfig;
import top.bincnpcef.common.NpcDamageModel;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * Combines held-weapon damage with the NPC's configured melee strength for EF-patched NPCs.
 *
 * <p>Epic Fight funnels the final amount through {@code getModifiedBaseDamage} in
 * {@code VanillaEntityEventHooks.onCalculateDamagePre}, which makes it the single place the
 * vanilla attack path and skill-driven hits pass through. See {@link NpcDamageModel}.
 *
 * <p>Also supports the optional NPC attack-speed override: when
 * {@link AddonConfig#NPC_ATTACK_SPEED_USE_MELEE_DELAY} is enabled, an EF-patched NPC's
 * attack animation speed follows its own melee delay ({@code 20 / meleeDelay}), instead of
 * the default weapon attack speed used by Epic Fight.
 */
@Mixin(value = LivingEntityPatch.class, remap = false)
public abstract class MixinLivingEntityPatchDamage {

    @Inject(method = "getModifiedBaseDamage", at = @At("RETURN"), cancellable = true)
    private void cnpcef$combineNpcDamage(float baseDamage, CallbackInfoReturnable<Float> cir) {
        float resolved = NpcDamageModel.resolve(
                (LivingEntityPatch<?>) (Object) this, baseDamage, cir.getReturnValueF());

        if (resolved != cir.getReturnValueF()) {
            cir.setReturnValue(resolved);
        }
    }

    @Inject(method = "getAttackSpeed", at = @At("RETURN"), cancellable = true)
    private void cnpcef$overrideNpcAttackSpeed(InteractionHand hand, CallbackInfoReturnable<Float> cir) {
        if (!AddonConfig.NPC_ATTACK_SPEED_USE_MELEE_DELAY.get()) {
            return;
        }

        LivingEntityPatch<?> self = (LivingEntityPatch<?>) (Object) this;
        if (!(self.getOriginal() instanceof EntityNPCInterface npc) || npc.stats == null || npc.stats.melee == null) {
            return;
        }

        int delay = npc.stats.melee.getDelay();
        if (delay <= 0) {
            return;
        }

        // CNPC melee delay is in ticks; convert to the 1-tick-normalised speed EF scales by.
        cir.setReturnValue(20.0F / delay);
    }
}