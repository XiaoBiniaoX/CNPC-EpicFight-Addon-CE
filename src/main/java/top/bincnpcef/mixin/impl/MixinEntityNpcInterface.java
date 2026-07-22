package top.bincnpcef.mixin.impl;

import net.minecraft.world.item.ProjectileWeaponItem;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * CNPC 实体 Mixin：注册 EF 战斗 AI + 触发武器站姿。
 *
 * <p>在 {@code addRegularEntries} 末尾注入：
 * <ol>
 *   <li>调用 {@link HumanoidMobPatch#setAIAsInfantry}，注册 EF 的 AnimatedAttackGoal。</li>
 *   <li>调用 {@link HumanoidMobPatch#modifyLivingMotionByCurrentItem}，根据当前主手武器
 *       注册对应的 idle/walk/chase 动画。</li>
 * </ol>
 *
 * <p>伤害源转换由 {@link MixinLivingEntityHurt} 统一处理。
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
            humanoidMobPatch.setAIAsInfantry(holdingRangedWeapon);
            try {
                humanoidMobPatch.modifyLivingMotionByCurrentItem(true);
            } catch (Exception e) {
                LOGGER.error("addRegularEntries: modifyLivingMotionByCurrentItem failed for NPC {}", self.getName().getString(), e);
            }
        }
    }
}
