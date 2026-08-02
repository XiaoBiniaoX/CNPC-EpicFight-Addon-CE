package top.bincnpcef.common;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;

import java.util.List;

/**
 * Damage model for NPCs running an Epic Fight patch.
 *
 * <p>{@code EntityNPCInterface.doHurtTarget} deals exactly {@code stats.melee.getStrength()} and
 * never consults the held item, so an EF-patched NPC swinging a diamond sword hit for the
 * same amount as an empty-handed one. For NPCs that carry a patch the two are combined:
 * <pre>
 *   one weapon : weapon damage + configured strength
 *   both hands : (mainhand + offhand) * 0.9 + configured strength
 * </pre>
 * Plain NPCs without a patch are untouched, and datapacks written against the old behaviour
 * keep working -- they only ever configured strength, which is still added in full.
 */
public final class NpcDamageModel {

    /** Applied when the NPC wields a weapon in each hand. */
    private static final float DUAL_WIELD_FACTOR = 0.9F;

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

        float configured = npc.stats == null || npc.stats.melee == null
                ? 0.0F
                : npc.stats.melee.getStrength();

        float mainHand = weaponDamage(patch, InteractionHand.MAIN_HAND);
        float offHand = weaponDamage(patch, InteractionHand.OFF_HAND);

        float weapons = offHand > 0.0F && mainHand > 0.0F
                ? (mainHand + offHand) * DUAL_WIELD_FACTOR
                : mainHand + offHand;

        if (weapons <= 0.0F) {
            // Unarmed: nothing to combine, leave the patch's own result alone.
            return modifiedDamage;
        }

        float combined = weapons + configured;

        // Preserve whatever scaling the patch applied (per-animation damage modifier
        // arrives as a plain multiplier on the incoming amount).
        if (incomingDamage > 0.0F && modifiedDamage != incomingDamage) {
            combined *= modifiedDamage / incomingDamage;
        }

        return combined;
    }

    /**
     * Sums the ATTACK_DAMAGE modifiers a held stack contributes, including the ones the
     * Epic Fight weapon capability adds on top of the vanilla item attributes.
     *
     * <p>{@code EquipmentSlot.MAINHAND} is used for both hands on purpose: that is the slot
     * Epic Fight itself queries when it swaps in the offhand weapon's damage.
     */
    private static float weaponDamage(LivingEntityPatch<?> patch, InteractionHand hand) {
        ItemStack stack = patch.getOriginal().getItemInHand(hand);

        if (stack.isEmpty()) {
            return 0.0F;
        }

        List<AttributeModifier> modifiers = CapabilityItem.getAttributeModifiersAsWeapon(
                Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND, stack, patch);

        float flat = 0.0F;
        float multiply = 1.0F;

        for (AttributeModifier modifier : modifiers) {
            switch (modifier.operation()) {
                case ADD_VALUE -> flat += (float) modifier.amount();
                case ADD_MULTIPLIED_BASE, ADD_MULTIPLIED_TOTAL -> multiply += (float) modifier.amount();
            }
        }

        return Math.max(0.0F, flat * multiply);
    }
}