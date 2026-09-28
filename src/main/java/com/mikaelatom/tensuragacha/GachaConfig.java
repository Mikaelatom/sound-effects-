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
        BUILDER.comment("Race Gacha Ticket: rolls a random race. Rarity comes from the race's difficulty.").push("raceGacha");
    }

    public static final ModConfigSpec.IntValue RACE_WEIGHT_EASY = BUILDER
            .comment("Relative weight of Easy (common) races.")
            .defineInRange("weightEasy", 60, 0, 10000);

    public static final ModConfigSpec.IntValue RACE_WEIGHT_INTERMEDIATE = BUILDER
            .comment("Relative weight of Intermediate (rare) races.")
            .defineInRange("weightIntermediate", 28, 0, 10000);

    public static final ModConfigSpec.IntValue RACE_WEIGHT_HARD = BUILDER
            .comment("Relative weight of Hard (epic) races.")
            .defineInRange("weightHard", 10, 0, 10000);

    public static final ModConfigSpec.IntValue RACE_WEIGHT_EXTREME = BUILDER
            .comment("Relative weight of Extreme (legendary) races.")
            .defineInRange("weightExtreme", 2, 0, 10000);

    public static final ModConfigSpec.BooleanValue RACE_STARTING_ONLY = BUILDER
            .comment("Only roll base races (ones that aren't an evolution of another race).")
            .define("startingRacesOnly", true);

    public static final ModConfigSpec.IntValue RACE_PITY_THRESHOLD = BUILDER
            .comment("Race rolls without a Hard or Extreme race before one is guaranteed. 0 disables pity.")
            .defineInRange("pityThreshold", 20, 0, 10000);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> RACE_BLACKLIST = BUILDER
            .comment("Race ids the race gacha will never roll, e.g. \"tensura:human\".")
            .defineListAllowEmpty("blacklist", List.of(), () -> "", o -> o instanceof String);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
