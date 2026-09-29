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

    public static final ModConfigSpec.BooleanValue GRANT_GAMBIT_CARDS = BUILDER
            .comment("Give every player the Gambit Cards skill.")
            .define("grantGambitCards", true);
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
        BUILDER.pop().comment("Shikigami Control: paper talismans, block shikigami, barriers and substitution").push("shikigami");
    }

    public static final ModConfigSpec.BooleanValue GRANT_SHIKIGAMI_CONTROL = BUILDER
            .comment("Give every player the Shikigami Control skill.")
            .define("grantShikigamiControl", true);
    public static final ModConfigSpec.DoubleValue LEAF_POTENCY = BUILDER
            .comment("Leaves can stand in for paper when you have none. How strong a leaf talisman is compared to paper:",
                    "shikigami health, damage and lifetime, talisman blast, barrier duration, and how much of a hit a",
                    "leaf doll blocks.")
            .defineInRange("leafPotency", 0.5, 0.0, 1.0);
    public static final ModConfigSpec.IntValue MAX_SHIKIGAMI = BUILDER
            .comment("Most shikigami you can have out at once (a new one replaces the oldest). 0 means no limit.")
            .defineInRange("shikigamiLimit", 0, 0, 100000);
    public static final ModConfigSpec.IntValue SHIKIGAMI_LIFETIME_TICKS = BUILDER
            .comment("How long a shikigami lasts before it turns back into its block.")
            .defineInRange("shikigamiLifetimeTicks", 2400, 20, 720000);
    public static final ModConfigSpec.DoubleValue SHIKIGAMI_REACH = BUILDER
            .comment("How far away a block can be turned into a shikigami.")
            .defineInRange("shikigamiReach", 8.0, 1.0, 64.0);
    public static final ModConfigSpec.DoubleValue SHIKIGAMI_BASE_MAGICULE_COST = BUILDER
            .defineInRange("shikigamiBaseMagiculeCost", 50.0, 0.0, 1.0E9);
    public static final ModConfigSpec.DoubleValue SHIKIGAMI_HARDNESS_MAGICULE_COST = BUILDER
            .comment("Extra magicules per point of block hardness (stone 1.5, iron block 5, obsidian 50 -> capped at 10).")
            .defineInRange("shikigamiHardnessMagiculeCost", 20.0, 0.0, 1.0E9);
    public static final ModConfigSpec.DoubleValue SHIKIGAMI_STRENGTH = BUILDER
            .comment("Multiplier on shikigami health and damage.")
            .defineInRange("shikigamiStrength", 1.0, 0.1, 100.0);
    public static final ModConfigSpec.DoubleValue TALISMAN_SPEED = BUILDER
            .defineInRange("talismanSpeed", 1.5, 0.1, 10.0);
    public static final ModConfigSpec.DoubleValue SPELL_COST_MULTIPLIER = BUILDER
            .comment("Multiplier on every spell talisman's magicule cost (Explosive 20, Fire 25, Water 20, Wood 25,",
                    "Lightning 35, Earth 30, Ice 25, Wind 20, Teleport 30).")
            .defineInRange("spellCostMultiplier", 1.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue TELEPORT_RANGE = BUILDER
            .comment("Farthest a Teleport talisman can take you (halved for leaves).")
            .defineInRange("teleportTalismanRange", 48.0, 1.0, 1000.0);
    public static final ModConfigSpec.BooleanValue GRANT_RAINBOW_MAGIC = BUILDER
            .comment("Give every player the Rainbow Magic skill (Tensura's spells in rainbow, striking with every element).")
            .define("grantRainbowMagic", true);
    public static final ModConfigSpec.DoubleValue RAINBOW_COST_MULTIPLIER = BUILDER
            .comment("Rainbow Magic spells cost this many times their base magicules (Fire Ball 30, Water Blade 30,",
                    "Wind Blade 25, Lightning Lance 45, Stone Shot 30, Ice Lance 35).")
            .defineInRange("rainbowCostMultiplier", 2.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue RAINBOW_DAMAGE_MULTIPLIER = BUILDER
            .comment("A Rainbow Magic hit adds every element's damage at once (Fire 4 + Water 2 + Lightning 8 + Earth 5 +",
                    "Ice 3 + Wind 2 = 24) on top of the spell itself, multiplied by this.")
            .defineInRange("rainbowDamageMultiplier", 0.5, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue TALISMAN_BLAST_RADIUS = BUILDER
            .defineInRange("talismanBlastRadius", 3.0, 0.5, 32.0);
    public static final ModConfigSpec.DoubleValue TALISMAN_BLAST_DAMAGE = BUILDER
            .defineInRange("talismanBlastDamage", 7.0, 0.0, 1000.0);
    public static final ModConfigSpec.IntValue MAX_BARRIER_ANCHORS = BUILDER
            .comment("Talisman anchors a barrier can have. Placing the last one raises the barrier automatically.")
            .defineInRange("maxBarrierAnchors", 6, 3, 16);
    public static final ModConfigSpec.DoubleValue BARRIER_ANCHOR_MAGICULE_COST = BUILDER
            .defineInRange("barrierAnchorMagiculeCost", 15.0, 0.0, 1.0E9);
    public static final ModConfigSpec.DoubleValue BARRIER_UPKEEP_PER_SECOND = BUILDER
            .comment("Magicules drained every second while a barrier is up. It collapses when you run out.")
            .defineInRange("barrierUpkeepPerSecond", 8.0, 0.0, 1.0E9);
    public static final ModConfigSpec.IntValue BARRIER_DURATION_TICKS = BUILDER
            .defineInRange("barrierDurationTicks", 600, 20, 720000);
    public static final ModConfigSpec.IntValue BARRIER_HEIGHT = BUILDER
            .defineInRange("barrierHeight", 5, 1, 64);
    public static final ModConfigSpec.BooleanValue BARRIER_BLOCKS_ALL_MOBS = BUILDER
            .comment("If true the barrier keeps out every mob except your own shikigami and pets. If false, only hostile",
                    "mobs and mobs targeting you.")
            .define("barrierBlocksAllMobs", true);
    public static final ModConfigSpec.BooleanValue BARRIER_BLOCKS_PLAYERS = BUILDER
            .comment("Whether other players are kept out too.")
            .define("barrierBlocksPlayers", false);
    public static final ModConfigSpec.DoubleValue BARRIER_DAMAGE_PER_SECOND = BUILDER
            .comment("Damage per second to hostile mobs caught inside the barrier while being pushed out.")
            .defineInRange("barrierDamagePerSecond", 2.0, 0.0, 1000.0);
    public static final ModConfigSpec.IntValue SUBSTITUTION_COOLDOWN_TICKS = BUILDER
            .comment("Substitution is automatic: while it's on and you have paper, every attack is taken by a paper doll.",
                    "Minimum ticks between two dolls (you're already invulnerable for a second after each one).")
            .defineInRange("substitutionCooldownTicks", 0, 0, 1200);
    public static final ModConfigSpec.DoubleValue SUBSTITUTION_BLINK_DISTANCE = BUILDER
            .comment("How far you blink away from the attacker when the paper doll takes the hit.")
            .defineInRange("substitutionBlinkDistance", 3.0, 0.0, 16.0);

    static {
        BUILDER.pop().comment("Sealing Grimoire: seal creatures and magic in a book with paper, then release them").push("grimoire");
    }

    public static final ModConfigSpec.BooleanValue GRANT_SEALING_GRIMOIRE = BUILDER
            .comment("Give every player the Sealing Grimoire skill. The first use turns one ordinary book into the grimoire.")
            .define("grantSealingGrimoire", true);
    public static final ModConfigSpec.IntValue GRIMOIRE_PAGES = BUILDER
            .comment("How many things one grimoire can hold.")
            .defineInRange("grimoirePages", 9, 1, 64);
    public static final ModConfigSpec.DoubleValue SEAL_REACH = BUILDER
            .defineInRange("sealReach", 8.0, 1.0, 64.0);
    public static final ModConfigSpec.DoubleValue SEAL_HEALTH_THRESHOLD = BUILDER
            .comment("A creature can only be sealed at or below this fraction of its health (paper; leaves multiply it by",
                    "leafPotency). A failed attempt still burns the talisman.")
            .defineInRange("sealHealthThreshold", 0.35, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue SEAL_MAX_HEALTH = BUILDER
            .comment("Creatures with more max health than this can't be sealed at all. Bosses never can",
                    "(entity tag tensurafragments:unsealable).")
            .defineInRange("sealMaxHealth", 300.0, 1.0, 1.0E9);
    public static final ModConfigSpec.DoubleValue SEAL_BASE_MAGICULE_COST = BUILDER
            .defineInRange("sealBaseMagiculeCost", 30.0, 0.0, 1.0E9);
    public static final ModConfigSpec.DoubleValue SEAL_MAGICULE_COST_PER_HEALTH = BUILDER
            .comment("Extra magicules per point of the creature's max health.")
            .defineInRange("sealMagiculeCostPerHealth", 2.0, 0.0, 1.0E9);
    public static final ModConfigSpec.IntValue CATCH_WINDOW_TICKS = BUILDER
            .comment("How long the book stays open to catch magic after using Seal Magic.")
            .defineInRange("catchWindowTicks", 10, 1, 200);
    public static final ModConfigSpec.DoubleValue CATCH_RADIUS = BUILDER
            .comment("How close a spell or projectile has to come to be caught.")
            .defineInRange("catchRadius", 3.0, 0.5, 16.0);
    public static final ModConfigSpec.IntValue CATCH_WHIFF_COOLDOWN_TICKS = BUILDER
            .comment("Cooldown if nothing is caught in the window.")
            .defineInRange("catchWhiffCooldownTicks", 40, 0, 1200);
    public static final ModConfigSpec.DoubleValue RELEASE_CREATURE_MAGICULE_COST = BUILDER
            .defineInRange("releaseCreatureMagiculeCost", 40.0, 0.0, 1.0E9);
    public static final ModConfigSpec.DoubleValue RELEASE_MAGIC_MAGICULE_COST = BUILDER
            .defineInRange("releaseMagicMagiculeCost", 20.0, 0.0, 1.0E9);
    public static final ModConfigSpec.IntValue BOUND_DURATION_TICKS = BUILDER
            .comment("How long a released creature fights for you before returning to the book.")
            .defineInRange("boundDurationTicks", 1200, 20, 720000);

    static {
        BUILDER.pop().comment("Flame Emperor: Tensura's fire magic, and Draconic Hell Storm").push("flame");
    }

    public static final ModConfigSpec.BooleanValue GRANT_FLAME_EMPEROR = BUILDER
            .comment("Give every player the Flame Emperor skill.")
            .define("grantFlameEmperor", true);
    public static final ModConfigSpec.DoubleValue FIRE_MAGIC_COST_MULTIPLIER = BUILDER
            .comment("Multiplier on every Flame Emperor fire spell's magicules (Fire Bolt 20 up to Hell Flare 250).")
            .defineInRange("fireMagicCostMultiplier", 1.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue HELL_STORM_MAGICULE_COST = BUILDER
            .defineInRange("hellStormMagiculeCost", 800.0, 0.0, 1.0E9);
    public static final ModConfigSpec.IntValue HELL_STORM_COOLDOWN_SECONDS = BUILDER
            .defineInRange("hellStormCooldownSeconds", 30, 0, 3600);
    public static final ModConfigSpec.IntValue HELL_STORM_CHARGE_TICKS = BUILDER
            .comment("How long the magic circle on your hand charges before the storm is released.")
            .defineInRange("hellStormChargeTicks", 30, 0, 200);
    public static final ModConfigSpec.IntValue HELL_STORM_DURATION_TICKS = BUILDER
            .comment("How long the storm pours out. It follows where you look, so you can sweep it.")
            .defineInRange("hellStormDurationTicks", 60, 1, 1200);
    public static final ModConfigSpec.DoubleValue HELL_STORM_RANGE = BUILDER
            .defineInRange("hellStormRange", 18.0, 1.0, 64.0);
    public static final ModConfigSpec.DoubleValue HELL_STORM_END_RADIUS = BUILDER
            .comment("The storm is Gluttony's mist: a cone, thin at your hand and this wide (radius) at its far end.",
                    "The mist is drawn to match, so what you see is what burns.")
            .defineInRange("hellStormEndRadius", 5.0, 0.5, 16.0);
    public static final ModConfigSpec.DoubleValue HELL_STORM_DAMAGE = BUILDER
            .comment("Hellfire damage per hit. Anything in the storm is hit 4 times a second. It ignores armour, shields,",
                    "Resistance, Fire Resistance, fire immunity and Tensura's dodges and barriers.")
            .defineInRange("hellStormDamagePerHit", 50.0, 0.0, 100000.0);
    public static final ModConfigSpec.DoubleValue HELLFIRE_DAMAGE_PER_SECOND = BUILDER
            .comment("Draconic Hellfire: the burn Hell Storm leaves never goes out, dealing this much every second until",
                    "the target dies.")
            .defineInRange("hellfireBurnPerSecond", 8.0, 0.0, 100000.0);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
