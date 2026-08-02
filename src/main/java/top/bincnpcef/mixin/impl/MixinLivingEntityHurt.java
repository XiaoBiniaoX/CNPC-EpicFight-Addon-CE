package top.bincnpcef.mixin.impl;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import top.bincnpcef.common.NpcDamageSourceConverter;

/**
 * 在 {@link LivingEntity#hurt(DamageSource, float)} 入口处转换 DamageSource 为
 * {@link yesman.epicfight.world.damagesource.EpicFightDamageSource}，使 EF 能识别
 * CNPC 的攻击并正确触发僵直、装甲穿透等。
 *
 * <p>注意：玩家受害者走 {@code Player.hurt} 覆写方法，此注入不生效，
 * 由 {@link MixinEntityNpcInterface#cnpcef$convertToEpicFightSource} 在上游调用点
 * （CNPC doHurtTarget）完成转换。本注入作为非玩家受害者的兜底。
 */
@Mixin(LivingEntity.class)
public class MixinLivingEntityHurt {
    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
    private DamageSource cnpcef$convertToEpicFightSource(DamageSource value) {
        if (value.getEntity() == null) {
            return value;
        }
        return NpcDamageSourceConverter.convertMobAttack(value.getEntity(), value);
    }
}
