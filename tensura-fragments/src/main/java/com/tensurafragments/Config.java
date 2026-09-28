package com.tensurafragments;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("Removing the original Tensura skills").push("originals");
    }

    public static final ModConfigSpec.BooleanValue STRIP_ORIGINAL_SKILLS = BUILDER
            .comment("Remove every skill from the namespaces below from players. Tensura is only used for its assets.")
            .define("stripOriginalSkills", true);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STRIPPED_NAMESPACES = BUILDER
            .comment("Skill namespaces that get removed.")
            .defineListAllowEmpty("strippedNamespaces", List.of("tensura"), () -> "tensura", o -> o instanceof String);

    static {
        BUILDER.pop().comment("Gambit Cards: place cards, teleport to them, blow them up").push("cards");
    }

    public static final ModConfigSpec.IntValue DECK_SIZE = BUILDER
            .comment("Cards the deck can hold.")
            .defineInRange("deckSize", 5, 1, 64);
    public static final ModConfigSpec.IntValue DECK_REGEN_TICKS = BUILDER
            .comment("Ticks to draw one card back into the deck.")
            .defineInRange("deckRegenTicks", 60, 1, 72000);
    public static final ModConfigSpec.IntValue MAX_ACTIVE_CARDS = BUILDER
            .comment("Cards that can be out at once. Throwing another makes the oldest one fizzle.")
            .defineInRange("maxActiveCards", 3, 1, 32);
    public static final ModConfigSpec.IntValue CARD_LIFETIME_TICKS = BUILDER
            .comment("How long a card lasts before it fizzles out.")
            .defineInRange("cardLifetimeTicks", 400, 20, 72000);
    public static final ModConfigSpec.IntValue FULL_CHARGE_TICKS = BUILDER
            .comment("Ticks a card needs to reach full charge. Charge scales blast damage from 50% to 150%.")
            .defineInRange("fullChargeTicks", 100, 1, 72000);
    public static final ModConfigSpec.DoubleValue THROW_SPEED = BUILDER
            .defineInRange("throwSpeed", 1.8, 0.1, 10.0);
    public static final ModConfigSpec.DoubleValue THROW_MAGICULE_COST = BUILDER
            .defineInRange("throwMagiculeCost", 40.0, 0.0, 1.0E9);
    public static final ModConfigSpec.DoubleValue TELEPORT_MAGICULE_COST = BUILDER
            .defineInRange("teleportMagiculeCost", 60.0, 0.0, 1.0E9);
    public static final ModConfigSpec.DoubleValue BLAST_RADIUS = BUILDER
            .defineInRange("blastRadius", 4.0, 0.5, 32.0);
    public static final ModConfigSpec.DoubleValue BLAST_DAMAGE = BUILDER
            .comment("Damage at the centre of the blast at 100% charge. Falls off to 0 at the edge.")
            .defineInRange("blastDamage", 8.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue SELF_DAMAGE_MULTIPLIER = BUILDER
            .comment("How much of the blast hits the card's owner. 0 disables self damage.")
            .defineInRange("selfDamageMultiplier", 1.0, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue BLAST_KNOCKBACK = BUILDER
            .defineInRange("blastKnockback", 1.2, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue MOMENTUM_CARRY = BUILDER
            .comment("Fraction of your speed kept (redirected where you look) when teleporting to a card.")
            .defineInRange("momentumCarry", 1.0, 0.0, 2.0);
    public static final ModConfigSpec.DoubleValue MAX_CARRIED_SPEED = BUILDER
            .defineInRange("maxCarriedSpeed", 3.0, 0.0, 10.0);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
