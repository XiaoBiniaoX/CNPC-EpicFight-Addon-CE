package com.goodbird.cnpcefaddon.common;

import net.minecraftforge.common.ForgeConfigSpec;

public class AddonConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue NPC_FACTION_FRIENDLY_FIRE;
    public static final ForgeConfigSpec.BooleanValue NPC_PLAYER_FACTION_PROTECTION;

    public static final ForgeConfigSpec.DoubleValue PLAYER_STUN_IMMUNE_ARMOR;
    public static final ForgeConfigSpec.BooleanValue ARMOR_STUN_BENEFIT_POSITIVE;

    public static final ForgeConfigSpec.ConfigValue<String> ONE_HAND_WEAPON_FACTOR;
    public static final ForgeConfigSpec.ConfigValue<String> TWO_HAND_WEAPON_FACTOR;

    public static final ForgeConfigSpec.BooleanValue SUPPRESS_EPICFIGHT_LOG;
    public static final ForgeConfigSpec.BooleanValue SUPPRESS_INDESTRUCTIBLE_LOG;

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

        builder.push("weapon_damage");

        builder.comment(
                "单手持械伤害系数：NPC仅单手持武器时，武器伤害部分乘以该系数后再加上NPC配置的近战力量伤害。",
                "填 1.0 表示武器伤害全额（默认行为）。",
                "留空则该NPC不套用武器伤害计算，伤害直接走NPC配置的近战力量并经过动画倍率。",
                "One-hand weapon damage factor: when the NPC wields a weapon in one hand only,",
                "the weapon damage is multiplied by this factor, then added to the configured melee strength.",
                "1.0 keeps the full weapon damage (default). Leave empty to skip weapon damage",
                "calculation and use the configured melee strength through animation scaling."
        );
        ONE_HAND_WEAPON_FACTOR = builder.define("oneHandWeaponDamageFactor", "1.0");

        builder.comment(
                "双手持械伤害系数：NPC双手都持有武器时，(主手武器+副手武器) 乘以该系数后再加上NPC配置的近战力量伤害。",
                "填 0.9 表示双持合计后打九折（默认行为）。",
                "留空则该NPC不套用武器伤害计算，伤害直接走NPC配置的近战力量并经过动画倍率。",
                "Two-hand weapon damage factor: when the NPC wields weapons in both hands,",
                "(mainhand + offhand) is multiplied by this factor, then added to the configured melee strength.",
                "0.9 applies a 10% reduction to the combined weapons (default). Leave empty to skip",
                "weapon damage calculation and use the configured melee strength through animation scaling."
        );
        TWO_HAND_WEAPON_FACTOR = builder.define("twoHandWeaponDamageFactor", "0.9");

        builder.pop();

        builder.push("log_suppression");

        builder.comment(
                "抑制史诗战斗的技能数据键日志（\"Data keys [...] for ...\"）。",
                "该日志在每次数据包/资源重载时把所有技能的数据键完整打印一遍，",
                "条数随已装技能模组数量增长，纯属注册表内部信息，无诊断价值。",
                "仅过滤这一类 INFO，史诗战斗的其他日志与全部 WARN/ERROR 一律保留。",
                "Suppress Epic Fight's skill data key dump (\"Data keys [...] for ...\"),",
                "reprinted in full on every datapack/resource reload. Only this INFO line is",
                "filtered; all other Epic Fight logs and every WARN/ERROR are kept."
        );
        SUPPRESS_EPICFIGHT_LOG = builder.define("suppressEpicFightDataKeyLog", true);

        builder.comment(
                "抑制坚不可摧的\"xxx can't be recognized\"警告。",
                "该警告在解析数据包时对每个未识别的可选字段各打一条，",
                "一个较大的数据包可产生数百条，会把日志刷满，但不影响功能。",
                "仅过滤这一类 WARN，坚不可摧的其他日志与全部 ERROR 一律保留。",
                "Suppress Indestructible's \"xxx can't be recognized\" warnings, emitted once per",
                "unrecognised optional field while parsing datapacks (hundreds for a large pack).",
                "Only this WARN pattern is filtered; other logs and every ERROR are kept."
        );
        SUPPRESS_INDESTRUCTIBLE_LOG = builder.define("suppressIndestructibleUnrecognizedWarn", true);

        builder.pop();

        SPEC = builder.build();
    }
}
