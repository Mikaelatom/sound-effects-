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

    public static final ModConfigSpec SPEC = BUILDER.build();
}
