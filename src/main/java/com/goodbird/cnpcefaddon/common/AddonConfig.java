package com.goodbird.cnpcefaddon.common;

import net.minecraftforge.common.ForgeConfigSpec;

public class AddonConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue NPC_FACTION_FRIENDLY_FIRE;
    public static final ForgeConfigSpec.BooleanValue NPC_PLAYER_FACTION_PROTECTION;

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

        SPEC = builder.build();
    }
}
