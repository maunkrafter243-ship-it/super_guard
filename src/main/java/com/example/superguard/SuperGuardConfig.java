package com.example.superguard;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;

public class SuperGuardConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_MOD;
    public static final ModConfigSpec.BooleanValue ENABLE_AUTH;
    public static final ModConfigSpec.BooleanValue ENABLE_SECURE_ADMIN;
    public static final ModConfigSpec.BooleanValue ENABLE_CHAT_GUARD;
    public static final ModConfigSpec.BooleanValue BLOCK_CHEAT_COMMANDS;
    public static final ModConfigSpec.BooleanValue ENABLE_TOTAL_ANTI_DUPE;
    public static final ModConfigSpec.BooleanValue BLOCK_DRAIN_AND_CONTRAPTION_EXPLOITS;
    public static final ModConfigSpec.BooleanValue ENABLE_ANTI_XRAY_HEURISTICS;
    public static final ModConfigSpec.BooleanValue ENABLE_ANTI_CHEAT;
    public static final ModConfigSpec.BooleanValue ENABLE_LAG_SHIELD;
    public static final ModConfigSpec.BooleanValue ENABLE_SCHEDULED_ITEM_CLEAR;
    public static final ModConfigSpec.IntValue MAX_CONTRAPTIONS_PER_CHUNK;
    public static final ModConfigSpec.IntValue MAX_DROPPED_ITEMS_PER_CHUNK;
    public static final ModConfigSpec.IntValue CLEAR_INTERVAL_MINUTES;
    public static final ModConfigSpec.IntValue MAX_ORES_PER_5_MINUTES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLOCKED_COMMANDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DUPE_PRONE_CREATE_BLOCKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> JUNK_ITEMS_TO_CLEAR;

    static {
        BUILDER.push("general");
        ENABLE_MOD = BUILDER.define("enableMod", true);
        ENABLE_AUTH = BUILDER.define("enableAuth", true);
        ENABLE_SECURE_ADMIN = BUILDER.define("enableSecureAdmin", true);
        ENABLE_CHAT_GUARD = BUILDER.define("enableChatGuard", true);
        BLOCK_CHEAT_COMMANDS = BUILDER.define("blockCheatCommands", true);
        ENABLE_TOTAL_ANTI_DUPE = BUILDER.define("enableTotalAntiDupe", true);
        BLOCK_DRAIN_AND_CONTRAPTION_EXPLOITS = BUILDER.define("blockDrainAndContraptionExploits", true);
        ENABLE_ANTI_XRAY_HEURISTICS = BUILDER.define("enableAntiXrayHeuristics", true);
        ENABLE_ANTI_CHEAT = BUILDER.define("enableAntiCheat", true);
        ENABLE_LAG_SHIELD = BUILDER.define("enableLagShield", true);
        ENABLE_SCHEDULED_ITEM_CLEAR = BUILDER.define("enableScheduledItemClear", true);
        BUILDER.pop();

        BUILDER.push("limits");
        MAX_CONTRAPTIONS_PER_CHUNK = BUILDER.defineInRange("maxContraptionsPerChunk", 15, 1, 64);
        MAX_DROPPED_ITEMS_PER_CHUNK = BUILDER.defineInRange("maxDroppedItemsPerChunk", 200, 20, 2000);
        CLEAR_INTERVAL_MINUTES = BUILDER.defineInRange("clearIntervalMinutes", 30, 1, 240);
        MAX_ORES_PER_5_MINUTES = BUILDER.defineInRange("maxValuableOresPer5Minutes", 28, 8, 200);
        BUILDER.pop();

        BUILDER.push("blacklists");
        BLOCKED_COMMANDS = BUILDER.defineListAllowEmpty("blockedCommands", List.of(
                "gamemode", "give", "op", "deop", "fill", "clone", "setblock",
                "summon", "tp", "teleport", "effect", "enchant", "xp", "experience",
                "seed", "execute", "data", "item", "forceload", "tick"
        ), obj -> obj instanceof String);

        DUPE_PRONE_CREATE_BLOCKS = BUILDER.defineListAllowEmpty("dupeProneCreateBlocks", List.of(
                "create:item_drain",
                "create:spout",
                "create:belt",
                "create:depot",
                "create:weighted_ejector",
                "create:chute",
                "create:smart_chute",
                "create:funnel",
                "create:andesite_funnel",
                "create:brass_funnel",
                "create:item_vault",
                "create:basin",
                "create:mechanical_press",
                "create:mechanical_mixer",
                "create:deployer",
                "create:schematicannon",
                "create:package_frogport",
                "create:packagers",
                "create:stock_ticker"
        ), obj -> obj instanceof String);

        JUNK_ITEMS_TO_CLEAR = BUILDER.defineListAllowEmpty("junkItemsToClear", List.of(
                "minecraft:dirt",
                "minecraft:cobblestone",
                "minecraft:stone",
                "minecraft:gravel",
                "minecraft:sand",
                "minecraft:netherrack",
                "minecraft:diorite",
                "minecraft:granite",
                "minecraft:andesite",
                "minecraft:cobbled_deepslate",
                "minecraft:deepslate",
                "minecraft:tuff",
                "minecraft:basalt",
                "minecraft:soul_sand",
                "minecraft:soul_soil",
                "minecraft:ice",
                "minecraft:snowball"
        ), obj -> obj instanceof String);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
