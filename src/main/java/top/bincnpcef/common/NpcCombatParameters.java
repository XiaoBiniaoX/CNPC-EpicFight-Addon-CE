package top.bincnpcef.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import noppes.npcs.entity.EntityNPCInterface;
import top.bincnpcef.api.IDataMeleeAttackDesire;

/** 两个参数默认均为中性值；不改技能槽、伤害与远程流程。 */
public final class NpcCombatParameters {
    public static final String DESIRE_KEY = "MeleeAttackDesire";
    public static final String SPEED_KEY = "cnpcefAnimSpeed";
    public static final float DEFAULT_DESIRE = 5.0F;
    public static final float DEFAULT_SPEED = 1.0F;
    public static final float MIN_SPEED = 0.01F;
    public static final float MAX_SPEED = 10.0F;

    private NpcCombatParameters() {}

    public static float sanitizeDesire(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(10.0F, value)) : DEFAULT_DESIRE;
    }

    public static float sanitizeSpeed(float value) {
        return Float.isFinite(value) && value > 0.0F
                ? Math.max(MIN_SPEED, Math.min(MAX_SPEED, value)) : DEFAULT_SPEED;
    }

    public static float read(CompoundTag tag, String key, float fallback) {
        if (tag.get(key) instanceof NumericTag number) return number.getAsFloat();
        if (tag.get(key) instanceof StringTag text) {
            try {
                return Float.parseFloat(text.getAsString().trim());
            } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    public static float desire(EntityNPCInterface npc) {
        return npc.stats == null || npc.stats.melee == null ? DEFAULT_DESIRE
                : ((IDataMeleeAttackDesire) npc.stats.melee).cnpcef$getAttackDesire();
    }

    public static float animationFactor(float desire) {
        return 0.5F + sanitizeDesire(desire) * 0.1F;
    }

    public static int adjustedDelay(int delay, float desire) {
        float value = sanitizeDesire(desire);
        if (value == DEFAULT_DESIRE) return delay;
        if (value <= 0.0F) return Integer.MAX_VALUE;
        double factor = Math.pow(DEFAULT_DESIRE / value, 3.0D);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1.0D, delay * factor));
    }
}
