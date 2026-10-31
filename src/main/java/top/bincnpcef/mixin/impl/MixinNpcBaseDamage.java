package top.bincnpcef.mixin.impl;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.bincnpcef.common.NpcDamageModel;
import yesman.epicfight.api.event.impl.VanillaEntityEventHooks;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.util.function.Consumer;

@Mixin(value = VanillaEntityEventHooks.class, remap = false)
public class MixinNpcBaseDamage {
    @Redirect(method = "onCalculateDamagePre", at = @At(value = "INVOKE",
            target = "Lyesman/epicfight/world/capabilities/entitypatch/LivingEntityPatch;getModifiedBaseDamage(F)F"))
    private static float cnpcef$baseDamage(LivingEntityPatch<?> patch, float damage,
                                          LivingEntity target, DamageSource source, float amount,
                                          Consumer<Float> applier) {
        // CNPC 的 hurt 输入已经过脚本/格挡/护甲；只跳过 NPC 近战的 EF 伤害倍率层。
        // 必须有 source 上下文，否则会误改同一攻击者的箭矢和处决。
        return NpcDamageModel.useMeleeStrength(source) ? damage : patch.getModifiedBaseDamage(damage);
    }
}
