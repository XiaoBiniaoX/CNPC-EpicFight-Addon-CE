package com.goodbird.cnpcefaddon.common;

import net.minecraftforge.common.ForgeConfigSpec;

public class AddonConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue NPC_FACTION_FRIENDLY_FIRE;
    public static final ForgeConfigSpec.BooleanValue NPC_PLAYER_FACTION_PROTECTION;

    public static final ForgeConfigSpec.DoubleValue PLAYER_STUN_IMMUNE_ARMOR;
    public static final ForgeConfigSpec.BooleanValue ARMOR_STUN_BENEFIT_POSITIVE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("faction_protection");

        builder.comment(
                "NPC阵营友伤保护：开启后，NPC的史诗战斗攻击不会伤害与其阵营不敌对的NPC。",
                "仅对NPC攻击者生效，普通生物（如僵尸）不受影响。",
                "Faction friendly fire protection: When enabled, NPC epic fight attacks",
                "will not damage NPCs whose faction is not hostile to the attacker."
        );
        NPC_FACTION_FRIENDLY_FIRE = builder.define("npcFactionFriendlyFire", true);

        builder.comment(
                "NPC对玩家的阵营保护：开启后，友好阵营的NPC攻击不会伤害玩家，",
                "中立阵营的NPC除非以玩家为攻击目标，否则也不会伤害玩家。",
                "阵营声望未设置时按默认点数判断（通常为中立）。",
                "NPC-to-player faction protection: When enabled, NPCs whose faction is friendly",
                "to the player will not damage them. Neutral faction NPCs will only damage",
                "the player if the player is their current attack target."
        );
        NPC_PLAYER_FACTION_PROTECTION = builder.define("npcPlayerFactionProtection", true);

        builder.pop();

        builder.push("player_stun");

        builder.comment(
                "玩家免僵直的防御阈值（原版护甲点）。玩家防御值 >= 此值时，",
                "NPC的攻击不再对该玩家造成任何僵直（短僵直、长僵直、击倒、破防）。",
                "设为 0 或负数可关闭此免疫。",
                "Armor threshold above which a player becomes immune to NPC-inflicted stun.",
                "Set to 0 or below to disable the immunity entirely."
        );
        PLAYER_STUN_IMMUNE_ARMOR = builder.defineInRange("playerStunImmuneArmor", 30.0D, -1.0D, 1024.0D);

        builder.comment(
                "防御对僵直的收益方向。",
                "true（正收益）：防御越高，僵直造成的晕眩时长越短。",
                "false（负收益）：防御越高，僵直造成的晕眩时长越长。",
                "缩放按史诗战斗内部的 stunTime = base * (1 - stunReduction) 公式实现，",
                "以玩家防御 / 上面的阈值 作为插值比例。",
                "Direction of the armor-to-stun relationship. true: more armor shortens stun.",
                "false: more armor lengthens stun. Applied through Epic Fight's own",
                "stunTime = base * (1 - stunReduction) formula."
        );
        ARMOR_STUN_BENEFIT_POSITIVE = builder.define("armorStunBenefitPositive", true);

        builder.pop();

        SPEC = builder.build();
    }
}
