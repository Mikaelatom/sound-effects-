package com.mikaelatom.tensuragacha;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class GachaConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue JACKPOT_CHANCE = BUILDER
            .comment("Chance (0-1) that a Soul Gacha Ticket rolls the jackpot Unique Skill: Gambler.")
            .defineInRange("jackpotChance", 0.02, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue ASTRAL_CORE_CHANCE = BUILDER
            .comment("Chance (0-1) that a roll gives an Astral Core (turns you into an Astral Slime) instead of a skill.")
            .defineInRange("astralCoreChance", 0.05, 0.0, 1.0);

    public static final ModConfigSpec.IntValue PITY_THRESHOLD = BUILDER
            .comment("Rolls without a jackpot before Gambler is guaranteed. 0 disables pity.")
            .defineInRange("pityThreshold", 50, 0, 10000);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST = BUILDER
            .comment("Unique Skill ids that the gacha will never roll, e.g. \"tensura:great_sage\".")
            .defineListAllowEmpty("blacklist", List.of(), () -> "", o -> o instanceof String);

    static {
        BUILDER.comment("Race Gacha Ticket: rolls a random race.",
                "Rarity comes from how far up its evolution line a race is:",
                "base races (Human, Slime, Goblin...) are Common, first evolutions are Rare,",
                "second evolutions are Epic, and third evolutions or higher (Divine races, God Slime, Devil Lord...) are Legendary.",
                "A tier is picked by weight first, then a race from that tier.").push("raceGacha");
    }

    public static final ModConfigSpec.IntValue RACE_WEIGHT_COMMON = BUILDER
            .comment("Chance weight of rolling a Common race.")
            .defineInRange("weightCommon", 60, 0, 10000);

    public static final ModConfigSpec.IntValue RACE_WEIGHT_RARE = BUILDER
            .comment("Chance weight of rolling a Rare race.")
            .defineInRange("weightRare", 28, 0, 10000);

    public static final ModConfigSpec.IntValue RACE_WEIGHT_EPIC = BUILDER
            .comment("Chance weight of rolling an Epic race.")
            .defineInRange("weightEpic", 10, 0, 10000);

    public static final ModConfigSpec.IntValue RACE_WEIGHT_LEGENDARY = BUILDER
            .comment("Chance weight of rolling a Legendary race.")
            .defineInRange("weightLegendary", 2, 0, 10000);

    public static final ModConfigSpec.IntValue RACE_PITY_THRESHOLD = BUILDER
            .comment("Race rolls without an Epic or Legendary race before one is guaranteed. 0 disables pity.")
            .defineInRange("pityThreshold", 20, 0, 10000);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> RACE_COMMON_OVERRIDES = BUILDER
            .comment("Race ids forced into the Common tier, whatever their evolution stage.")
            .defineListAllowEmpty("commonRaces", List.of(), () -> "", o -> o instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> RACE_RARE_OVERRIDES = BUILDER
            .comment("Race ids forced into the Rare tier.")
            .defineListAllowEmpty("rareRaces", List.of(), () -> "", o -> o instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> RACE_EPIC_OVERRIDES = BUILDER
            .comment("Race ids forced into the Epic tier.")
            .defineListAllowEmpty("epicRaces", List.of(), () -> "", o -> o instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> RACE_LEGENDARY_OVERRIDES = BUILDER
            .comment("Race ids forced into the Legendary tier.")
            .defineListAllowEmpty("legendaryRaces", List.of(), () -> "", o -> o instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> RACE_BLACKLIST = BUILDER
            .comment("Race ids the race gacha will never roll, e.g. \"tensura:human\".")
            .defineListAllowEmpty("blacklist", List.of(), () -> "", o -> o instanceof String);

    static {
        BUILDER.pop();
        BUILDER.comment("Soul Market shop. Every item gets a price in Soul Coins based on how rare it is:",
                "raw materials have base values, and crafted/smelted items are priced from their recipes.").push("shop");
    }

    public static final ModConfigSpec.BooleanValue SHOP_COMMAND = BUILDER
            .comment("Allow players to open the shop anywhere with /shop. If false, they need a Soul Market block.")
            .define("allowShopCommand", true);

    public static final ModConfigSpec.DoubleValue SHOP_SELL_MULTIPLIER = BUILDER
            .comment("Selling pays this fraction of an item's buy price. Keep it below 1 so buying and selling back can't make coins.")
            .defineInRange("sellMultiplier", 0.5, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue SHOP_PRICE_MULTIPLIER = BUILDER
            .comment("Multiplies every buy price, for making the whole shop cheaper or more expensive.")
            .defineInRange("priceMultiplier", 1.0, 0.01, 1000.0);

    public static final ModConfigSpec.BooleanValue SHOP_SPAWN_EGGS = BUILDER
            .comment("Allow spawn eggs to be bought and sold.")
            .define("allowSpawnEggs", false);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> SHOP_BLACKLIST = BUILDER
            .comment("Item ids that can't be bought or sold, on top of the built-in list of creative-only items.")
            .defineListAllowEmpty("blacklist", List.of(), () -> "", o -> o instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> SHOP_VALUE_OVERRIDES = BUILDER
            .comment("Fixed prices in Soul Coins, as \"item_id=price\" or \"#tag_id=price\", e.g. \"minecraft:diamond=2000\".",
                    "Items crafted from these are re-priced from their recipes automatically.")
            .defineListAllowEmpty("valueOverrides", List.of(), () -> "", o -> o instanceof String s && s.contains("="));

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
