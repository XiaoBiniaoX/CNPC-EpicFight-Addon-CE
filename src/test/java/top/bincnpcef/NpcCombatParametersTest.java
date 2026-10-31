package top.bincnpcef;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import top.bincnpcef.common.NpcCombatParameters;

import static org.junit.jupiter.api.Assertions.*;

class NpcCombatParametersTest {
    @Test
    void parametersKeepDefaultsAndRejectNonFiniteValues() {
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertEquals(5.0F, NpcCombatParameters.sanitizeDesire(invalid));
            assertEquals(1.0F, NpcCombatParameters.sanitizeSpeed(invalid));
        }
        assertEquals(0.0F, NpcCombatParameters.sanitizeDesire(-1));
        assertEquals(10.0F, NpcCombatParameters.sanitizeDesire(11));
        assertEquals(1.0F, NpcCombatParameters.sanitizeSpeed(0));
        assertEquals(1.0F, NpcCombatParameters.sanitizeSpeed(-1));
        assertEquals(0.01F, NpcCombatParameters.sanitizeSpeed(0.001F));
        assertEquals(10.0F, NpcCombatParameters.sanitizeSpeed(11));
        assertEquals(0.5F, NpcCombatParameters.animationFactor(0));
        assertEquals(1.0F, NpcCombatParameters.animationFactor(5));
        assertEquals(1.5F, NpcCombatParameters.animationFactor(10));
        assertEquals(20, NpcCombatParameters.adjustedDelay(20, 5));
        assertEquals(160, NpcCombatParameters.adjustedDelay(20, 2.5F));
        assertEquals(2, NpcCombatParameters.adjustedDelay(20, 10));
        assertEquals(Integer.MAX_VALUE, NpcCombatParameters.adjustedDelay(20, 0));
        assertEquals(Integer.MAX_VALUE, NpcCombatParameters.adjustedDelay(20, Float.MIN_VALUE));
    }

    @Test
    void nbtAcceptsNumericAndLegacyStringButMissingOrInvalidUsesDefault() {
        CompoundTag tag = new CompoundTag();
        String key = NpcCombatParameters.DESIRE_KEY;
        assertEquals(5.0F, NpcCombatParameters.read(tag, key, 5));
        tag.putInt(key, 3);
        assertEquals(3.0F, NpcCombatParameters.read(tag, key, 5));
        tag.putString(key, " 7.5 ");
        assertEquals(7.5F, NpcCombatParameters.read(tag, key, 5));
        tag.putString(key, "not-a-number");
        assertEquals(5.0F, NpcCombatParameters.read(tag, key, 5));
        tag.putFloat(key, Float.NaN);
        assertEquals(5.0F, NpcCombatParameters.sanitizeDesire(NpcCombatParameters.read(tag, key, 5)));
        tag.put(key, new CompoundTag());
        assertEquals(5.0F, NpcCombatParameters.read(tag, key, 5));
    }
}
