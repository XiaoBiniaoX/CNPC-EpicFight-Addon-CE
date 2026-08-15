package com.goodbird.cnpcefaddon.common;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;

import java.util.List;

/**
 * Damage model for NPCs running an Epic Fight patch.
 * <p>
 * {@code EntityNPCInterface.doHurtTarget} deals exactly {@code stats.melee.getStrength()} and
 * never consults the held item, so an EF-patched NPC swinging a diamond sword hit for the
 * same amount as an empty-handed one. For NPCs that carry a patch the two are combined:
 * <pre>
 *   one weapon : weapon damage * oneHandFactor + configured strength
 *   both hands : (mainhand + offhand) * twoHandFactor + configured strength
 * </pre>
 * The two factors come from {@link AddonConfig} and are kept at their default values
 * (1.0 / 0.9) unless the player edits them. Leaving a factor empty skips the weapon
 * damage calculation entirely for that grip, so the NPC deals its configured melee
 * strength through Epic Fight's own animation scaling, exactly like an unarmed NPC.
 * Plain NPCs without a patch are untouched, and datapacks written against the old
 * behaviour keep working -- they only ever configured strength, which is still added
 * in full.
 */
public final class NpcDamageModel {

    /** Fallback when a configured factor cannot be parsed. */
    private static final float DEFAULT_ONE_HAND_FACTOR = 1.0F;
    private static final float DEFAULT_TWO_HAND_FACTOR = 0.9F;

    private NpcDamageModel() {
    }

    /**
     * @param patch          the attacking NPC's patch
     * @param incomingDamage the amount handed to {@code getModifiedBaseDamage}
     * @param modifiedDamage what the patch itself returned for that amount
     * @return the combined damage, or {@code modifiedDamage} when the rule does not apply
     */
    public static float resolve(LivingEntityPatch<?> patch, float incomingDamage, float modifiedDamage) {
        if (patch == null) {
            return modifiedDamage;
        }

        LivingEntity original = patch.getOriginal();

        if (!(original instanceof EntityNPCInterface npc)) {
            return modifiedDamage;
        }

        float mainHand = weaponDamage(patch, InteractionHand.MAIN_HAND);
        float offHand = weaponDamage(patch, InteractionHand.OFF_HAND);

        boolean dualWield = offHand > 0.0F && mainHand > 0.0F;
        String factorConfig = dualWield
                ? AddonConfig.TWO_HAND_WEAPON_FACTOR.get()
                : AddonConfig.ONE_HAND_WEAPON_FACTOR.get();

        // Empty factor: skip the weapon damage calculation, the NPC deals its
        // configured melee strength through the normal (unarmed) path.
        if (factorConfig == null || factorConfig.isBlank()) {
            return modifiedDamage;
        }

        float factor = dualWield ? DEFAULT_TWO_HAND_FACTOR : DEFAULT_ONE_HAND_FACTOR;
        try {
            factor = Float.parseFloat(factorConfig.trim());
        } catch (NumberFormatException ignored) {
        }

        float configured = npc.stats == null || npc.stats.melee == null
                ? 0.0F
                : npc.stats.melee.getStrength();

        float weapons = (mainHand + offHand) * factor;

        if (weapons <= 0.0F) {
            // Unarmed: nothing to combine, leave the patch's own result alone.
            return modifiedDamage;
        }

        float combined = weapons + configured;

        // Preserve whatever scaling the patch applied (Indestructible's per-animation
        // damage modifier arrives as a plain multiplier on the incoming amount).
        if (incomingDamage > 0.0F && modifiedDamage != incomingDamage) {
            combined *= modifiedDamage / incomingDamage;
        }

        return combined;
    }

    /**
     * Sums the ATTACK_DAMAGE modifiers a held stack contributes, including the ones the
     * Epic Fight weapon capability adds on top of the vanilla item attributes.
     * <p>
     * {@code EquipmentSlot.MAINHAND} is used for both hands on purpose: that is the slot
     * Epic Fight itself queries when it swaps in the offhand weapon's damage.
     */
    private static float weaponDamage(LivingEntityPatch<?> patch, InteractionHand hand) {
        ItemStack stack = patch.getOriginal().getItemInHand(hand);

        if (stack.isEmpty()) {
            return 0.0F;
        }

        List<AttributeModifier> modifiers = CapabilityItem.getAttributeModifiers(
                Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND, stack, patch);

        float flat = 0.0F;
        float multiply = 1.0F;

        for (AttributeModifier modifier : modifiers) {
            switch (modifier.getOperation()) {
                case ADDITION -> flat += (float) modifier.getAmount();
                case MULTIPLY_BASE, MULTIPLY_TOTAL -> multiply += (float) modifier.getAmount();
            }
        }

        return Math.max(0.0F, flat * multiply);
    }

    /** Exposed for tooltips or debug output. */
    public static float baseAttributeDamage(LivingEntity entity, Attribute attribute) {
        return (float) entity.getAttributeValue(attribute);
    }
}
