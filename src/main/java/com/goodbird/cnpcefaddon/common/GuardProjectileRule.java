package com.goodbird.cnpcefaddon.common;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.EntityProjectile;
import yesman.epicfight.world.damagesource.EpicFightDamageTypeTags;

/**
 * Decides when Epic Fight's guard skills should treat a projectile as blockable.
 * <p>
 * Epic Fight hardcodes {@code !damageSource.is(DamageTypeTags.IS_PROJECTILE)} in
 * {@code GuardSkill.isBlockableSource} (and inherits the same rejection in
 * {@code ImpactGuardSkill} and {@code ParryingSkill}), so no projectile can ever be guarded.
 * This class narrows that rule to a single case: a projectile whose owner is a Custom NPC.
 * <p>
 * Two shapes are covered, because Custom NPCs reaches a target in two ways:
 * <ul>
 *   <li>{@code EntityProjectile} -- the normal CNPC ranged attack (the projectile slot item is
 *       only a texture, {@code isArrow()} does not change the class);</li>
 *   <li>{@code AbstractArrow} -- a real vanilla arrow, used when an NPC fires an actual bow.</li>
 * </ul>
 * In both cases the damage source carries the projectile as the direct entity and the NPC as
 * the causing entity, which is what this check keys on.
 * <p>
 * Everything epic fight already refuses to block is still refused: only the projectile term is
 * relaxed, so armour-piercing, invulnerability-bypassing, fire, magic and explosion damages keep
 * their original behaviour. Projectiles from other mods and from vanilla mobs are untouched.
 */
public final class GuardProjectileRule {

    private GuardProjectileRule() {
    }

    /**
     * @return whether the guard skills should block this damage source although epic fight's own
     *         check rejected it as a projectile
     */
    public static boolean isNpcProjectile(DamageSource damageSource) {
        if (!AddonConfig.GUARD_NPC_PROJECTILE.get()) {
            return false;
        }

        Entity direct = damageSource.getDirectEntity();

        if (!(direct instanceof EntityProjectile) && !(direct instanceof AbstractArrow)) {
            return false;
        }

        if (!(damageSource.getEntity() instanceof EntityNPCInterface)) {
            return false;
        }

        // Mirror epic fight's remaining rejections instead of blanket-allowing the source: a CNPC
        // projectile that is also armour-piercing or explicitly unblockable must stay unblockable.
        return damageSource.is(DamageTypeTags.IS_PROJECTILE)
                && !damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && !damageSource.is(EpicFightDamageTypeTags.UNBLOCKALBE)
                && !damageSource.is(DamageTypeTags.BYPASSES_ARMOR)
                && !damageSource.is(DamageTypeTags.IS_EXPLOSION)
                && !damageSource.is(DamageTypes.MAGIC)
                && !damageSource.is(DamageTypeTags.IS_FIRE);
    }
}
