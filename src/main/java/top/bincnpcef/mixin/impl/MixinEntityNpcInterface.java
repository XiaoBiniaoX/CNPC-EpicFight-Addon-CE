package top.bincnpcef.mixin.impl;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ProjectileWeaponItem;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.bincnpcef.common.NpcDamageSourceConverter;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * CNPC 实体 Mixin：注册 EF 战斗 AI + 触发武器站姿 + 伤害源转换。
 *
 * <p>在 {@code addRegularEntries} 末尾注入：
 * <ol>
 *   <li>调用 {@link HumanoidMobPatch#setAIAsInfantry}，注册 EF 的 AnimatedAttackGoal。</li>
 *   <li>调用 {@link HumanoidMobPatch#modifyLivingMotionByCurrentItem}，根据当前主手武器
 *       注册对应的 idle/walk/chase 动画。</li>
 * </ol>
 *
 * <p>在 {@code doHurtTarget} 中把 {@code hurt()} 的第一个参数（DamageSource）从 CNPC
 * 的原始源转换为 {@link yesman.epicfight.world.damagesource.EpicFightDamageSource}。
 * 这是问题1/2 的根因修复：玩家受害者的 {@code Player.hurt} 是覆写方法，绕过
 * {@code MixinLivingEntityHurt} 对 {@code LivingEntity.hurt} 的注入，必须在上游调用点转换。
 */
@Mixin(value = EntityNPCInterface.class)
public abstract class MixinEntityNpcInterface {
    private static final Logger LOGGER = LoggerFactory.getLogger("CNPC-EF-Addon");

    @Inject(method = "addRegularEntries", at = @At("TAIL"), remap = false)
    private void cnpcef$setEFCombatAI(CallbackInfo ci) {
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        if (self.level().isClientSide()) {
            return;
        }
        LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(self, LivingEntityPatch.class);
        if (patch instanceof HumanoidMobPatch<?> humanoidMobPatch) {
            boolean holdingRangedWeapon = self.getMainHandItem().getItem() instanceof ProjectileWeaponItem;
            try {
                humanoidMobPatch.setAIAsInfantry(holdingRangedWeapon);
            } catch (Exception e) {
                LOGGER.error("addRegularEntries: setAIAsInfantry failed for NPC {} (patch={}, ranged={})",
                    self.getName().getString(), patch.getClass().getName(), holdingRangedWeapon, e);
            }
            try {
                humanoidMobPatch.modifyLivingMotionByCurrentItem(true);
            } catch (Exception e) {
                LOGGER.error("addRegularEntries: modifyLivingMotionByCurrentItem failed for NPC {}", self.getName().getString(), e);
            }
            LOGGER.info("addRegularEntries: EF AI registered for NPC {} (patch={}, ranged={})",
                self.getName().getString(), patch.getClass().getName(), holdingRangedWeapon);
        } else {
            LOGGER.warn("addRegularEntries: NPC {} has no HumanoidMobPatch (patch={})",
                self.getName().getString(), patch == null ? "null" : patch.getClass().getName());
        }
    }

    /**
     * 根因修复（问题1/2）：CNPC 的 {@code doHurtTarget} 内联构造
     * {@code new DamageSource(NpcDamageSource.NPC, this)} 作为 {@code hurt()} 第一参数，
     * 这里在调用点将其替换为 EpicFightDamageSource，使玩家受害者能正确进入
     * EF 的僵直处理链（impact、stun、armor penetration）。
     */
    @ModifyArg(method = "doHurtTarget",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"),
        index = 0)
    private DamageSource cnpcef$convertToEpicFightSource(DamageSource source) {
        Entity self = (Entity) (Object) this;
        return NpcDamageSourceConverter.convertMobAttack(self, source);
    }
}
