package com.tensurafragments;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("Removing the original Tensura skills").push("originals");
    }

    public static final ModConfigSpec.BooleanValue STRIP_ORIGINAL_SKILLS = BUILDER
            .comment("Remove Tensura's own top-tier skills from players (the types below): this addon makes the new ones.",
                    "Everything else (magic, resistances, common, extra and intrinsic skills) is learned the normal Tensura way.")
            .define("stripOriginalSkills", true);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STRIPPED_NAMESPACES = BUILDER
            .comment("Mods whose skills of the types below get removed.")
            .defineListAllowEmpty("strippedNamespaces", List.of("tensura"), () -> "tensura", o -> o instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STRIPPED_SKILL_TYPES = BUILDER
            .comment("Tensura skill types that get removed (RESISTANCE, INTRINSIC, COMMON, EXTRA, UNIQUE, ULTIMATE).",
                    "Ultimates are removed because this addon's skills will evolve into its own.")
            .defineListAllowEmpty("strippedSkillTypes", List.of("UNIQUE", "ULTIMATE"), () -> "UNIQUE", o -> o instanceof String);

    static {
        BUILDER.pop().comment("Learning Tensura's magic").push("magic");
    }

    public static final ModConfigSpec.IntValue SURVIVALS_TO_LEARN_SPELL = BUILDER
            .comment("Survive being hit by a Tensura spell you don't know this many times and you learn it (0 turns this off).")
            .defineInRange("survivalsToLearnSpell", 5, 0, 1000);

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
            .comment("Blast radius of a card. (Replaces the old blastRadius.)")
            .defineInRange("cardBlastRadius", 5.0, 0.5, 32.0);
    public static final ModConfigSpec.DoubleValue BLAST_DAMAGE = BUILDER
            .comment("Damage at the centre of the blast at 100% charge. Falls off to 0 at the edge. (Replaces the old blastDamage.)")
            .defineInRange("cardBlastDamage", 20.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue SELF_DAMAGE_MULTIPLIER = BUILDER
            .comment("How much of the blast hits the card's owner. 0 disables self damage. (Replaces the old selfDamageMultiplier.)")
            .defineInRange("cardSelfDamage", 0.35, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue BLAST_KNOCKBACK = BUILDER
            .defineInRange("blastKnockback", 1.2, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue SPELL_CARD_POWER = BUILDER
            .comment("Multiplier on all Spell Card damage (and, a little, their size).")
            .defineInRange("spellCardPower", 1.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue SPELL_CARD_COST_MULTIPLIER = BUILDER
            .comment("Multiplier on the magicules it takes to inscribe a Spell Card (Flame 120 up to Meteor 600).")
            .defineInRange("spellCardCostMultiplier", 1.0, 0.0, 1000.0);
    public static final ModConfigSpec.IntValue MAX_SPELL_CARDS = BUILDER
            .comment("Spell Cards you can have out at once (separate from the plain cards). Throwing another makes the oldest fizzle.")
            .defineInRange("maxSpellCards", 8, 1, 64);
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
            .comment("How far away a block can be turned into a shikigami (barrier talismans reach twice as far).")
            .defineInRange("shikigamiReachBlocks", 32.0, 1.0, 128.0);
    public static final ModConfigSpec.DoubleValue SHIKIGAMI_BASE_MAGICULE_COST = BUILDER
            .defineInRange("shikigamiBaseMagiculeCost", 50.0, 0.0, 1.0E9);
    public static final ModConfigSpec.DoubleValue SHIKIGAMI_HARDNESS_MAGICULE_COST = BUILDER
            .comment("Extra magicules per point of block hardness (stone 1.5, iron block 5, obsidian 50 -> capped at 10).")
            .defineInRange("shikigamiHardnessMagiculeCost", 20.0, 0.0, 1.0E9);
    public static final ModConfigSpec.DoubleValue SHIKIGAMI_STRENGTH = BUILDER
            .comment("Multiplier on shikigami health and damage.")
            .defineInRange("shikigamiStrength", 1.0, 0.1, 100.0);
    public static final ModConfigSpec.DoubleValue TALISMAN_SPEED = BUILDER
            .comment("How fast talismans are thrown (blocks per tick). They fly nearly straight: about 100 blocks at 3.0.")
            .defineInRange("talismanThrowSpeed", 3.0, 0.1, 10.0);
    public static final ModConfigSpec.DoubleValue SPELL_COST_MULTIPLIER = BUILDER
            .comment("Multiplier on every spell talisman's magicule cost (Explosive 20, Fire 25, Water 20, Wood 25,",
                    "Lightning 35, Earth 30, Ice 25, Wind 20, Teleport 30).")
            .defineInRange("spellCostMultiplier", 1.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue TELEPORT_RANGE = BUILDER
            .comment("Farthest a Teleport talisman can take you (halved for leaves). It flies dead straight until it hits something.")
            .defineInRange("teleportTalismanMaxRange", 256.0, 1.0, 1000.0);
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
    public static final ModConfigSpec.DoubleValue PAPER_BEAST_MAGICULE_COST = BUILDER
            .comment("Paper beasts: fold paper into an owl, hound, winged cat or horned rabbit. Magicules to fold one (the",
                    "paper each takes is set per beast: owl 2, hound 3, cat 2, rabbit 1).")
            .defineInRange("paperBeastMagiculeCost", 40.0, 0.0, 1.0E9);
    public static final ModConfigSpec.DoubleValue PAPER_BEAST_STRENGTH = BUILDER
            .comment("Multiplier on paper beasts' health and damage.")
            .defineInRange("paperBeastStrength", 1.0, 0.05, 100.0);
    public static final ModConfigSpec.DoubleValue POSSESSION_MAGICULES_PER_SECOND = BUILDER
            .comment("Magicules per second while you see through and control a paper beast.")
            .defineInRange("possessionMagiculesPerSecond", 2.0, 0.0, 1.0E6);

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
        BUILDER.pop().comment("Spirit Control: every attack calls a different spirit that strikes once and vanishes").push("spirits");
    }

    public static final ModConfigSpec.BooleanValue GRANT_SPIRIT_CONTROL = BUILDER
            .comment("Give every player the Spirit Control skill.")
            .define("grantSpiritControl", true);
    public static final ModConfigSpec.DoubleValue SPIRIT_MAGICULE_COST = BUILDER
            .comment("Magicules per spirit.")
            .defineInRange("spiritMagiculeCost", 30.0, 0.0, 1.0E9);
    public static final ModConfigSpec.IntValue SPIRIT_INTERVAL_TICKS = BUILDER
            .comment("Minimum ticks between two spirits, so each attack gets one and spam-clicking doesn't flood.")
            .defineInRange("spiritIntervalTicks", 10, 0, 200);
    public static final ModConfigSpec.DoubleValue SPIRIT_DAMAGE = BUILDER
            .comment("Base damage of a spirit's attack (Ifrit, Sylphide and Undine x1, War Gnome x1.2, Blade Tiger x1.4).")
            .defineInRange("spiritDamage", 12.0, 0.0, 100000.0);
    public static final ModConfigSpec.DoubleValue SPIRIT_RANGE = BUILDER
            .comment("How far away you can aim a spirit with the skill key.")
            .defineInRange("spiritRange", 32.0, 4.0, 128.0);

    static {
        BUILDER.pop().comment("Energy Magic: Tensura's spells, glowing green and twice as strong, paid for with experience levels").push("energy");
    }

    public static final ModConfigSpec.BooleanValue GRANT_ENERGY_MAGIC = BUILDER
            .comment("Give every player the Energy Magic skill.")
            .define("grantEnergyMagic", true);
    public static final ModConfigSpec.DoubleValue ENERGY_POWER = BUILDER
            .comment("How many times stronger an energy spell is than the normal spell.")
            .defineInRange("energyPower", 2.0, 0.1, 100.0);
    public static final ModConfigSpec.DoubleValue ENERGY_LEVEL_COST_MULTIPLIER = BUILDER
            .comment("Multiplier on every energy spell's cost in experience levels (Fire Bolt 1 up to Plasma Ball 5).")
            .defineInRange("energyLevelCostMultiplier", 1.0, 0.0, 100.0);

    static {
        BUILDER.pop().comment("Spirit Communion (Yifa): see elemental spirits, bind them, and cast through them").push("yifa");
    }

    public static final ModConfigSpec.BooleanValue GRANT_SPIRIT_COMMUNION = BUILDER
            .comment("Give every player the Spirit Communion skill.")
            .define("grantSpiritCommunion", true);
    public static final ModConfigSpec.DoubleValue SPIRIT_SIGHT_MAGICULES_PER_SECOND = BUILDER
            .comment("Magicules per second while Spirit Sight is on.")
            .defineInRange("spiritSightMagiculesPerSecond", 1.0, 0.0, 1.0E6);
    public static final ModConfigSpec.IntValue YIFA_MAX_BOUND = BUILDER
            .comment("How many spirits you can have bound (orbiting you) at once.")
            .defineInRange("maxBoundSpirits", 5, 1, 32);
    public static final ModConfigSpec.IntValue YIFA_BOUND_SECONDS = BUILDER
            .comment("How long a bound spirit stays with you before it drifts off.")
            .defineInRange("boundSpiritSeconds", 180, 5, 3600);
    public static final ModConfigSpec.IntValue YIFA_WILD_CAP = BUILDER
            .comment("Most wild spirits around you at once (twice that while you carry a Magisteel Spirit Lantern).")
            .defineInRange("wildSpiritCap", 6, 0, 64);
    public static final ModConfigSpec.DoubleValue YIFA_MAGIC_POWER = BUILDER
            .comment("Multiplier on all Spirit Magic damage, healing and size.")
            .defineInRange("spiritMagicPower", 1.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue YIFA_MAGICULES_PER_SPIRIT = BUILDER
            .comment("Magicules per spirit released in Spirit Magic (the spirits carry the real power).")
            .defineInRange("magiculesPerSpirit", 5.0, 0.0, 1.0E6);
    public static final ModConfigSpec.DoubleValue YIFA_JUTSU_COST = BUILDER
            .comment("Magicules for a Spirit Jutsu (the weak fallback with no spirits).")
            .defineInRange("jutsuMagiculeCost", 15.0, 0.0, 1.0E6);
    public static final ModConfigSpec.IntValue SPIRIT_BELL_COOLDOWN_TICKS = BUILDER
            .comment("Magisteel Spirit Bell: how long before it can be rung again.")
            .defineInRange("spiritBellCooldownTicks", 200, 0, 12000);
    public static final ModConfigSpec.DoubleValue SPIRIT_BELL_RANGE = BUILDER
            .comment("Magisteel Spirit Bell: how far away wild spirits hear it.")
            .defineInRange("spiritBellRange", 32.0, 1.0, 128.0);

    static {
        BUILDER.pop().comment("Soul Reaper: every kill gives a soul; each soul can be used once to summon, absorb or possess").push("souls");
    }

    public static final ModConfigSpec.BooleanValue GRANT_SOUL_REAPER = BUILDER
            .comment("Give every player the Soul Reaper skill.")
            .define("grantSoulReaper", true);
    public static final ModConfigSpec.IntValue SOUL_POINTS_PER_KILL = BUILDER
            .comment("Tensura soul points every kill gives (1000 points show as 1 soul in Tensura's menu). Using the soul takes them back.")
            .defineInRange("soulPointsPerKill", 1000, 0, 10_000_000);
    public static final ModConfigSpec.DoubleValue SOUL_POINTS_PER_EP = BUILDER
            .comment("Extra soul points per EP of what you killed, so stronger kills give more.")
            .defineInRange("soulPointsPerEp", 0.5, 0.0, 1000.0);
    public static final ModConfigSpec.IntValue MAX_CAPTURED_SOULS = BUILDER
            .comment("How many captured souls you can keep; past that the weakest is let go.")
            .defineInRange("maxCapturedSouls", 27, 1, 256);
    public static final ModConfigSpec.IntValue SOUL_SUMMON_SECONDS = BUILDER
            .comment("How long a summoned soul fights for you.")
            .defineInRange("summonSeconds", 60, 5, 3600);
    public static final ModConfigSpec.IntValue MAX_SOUL_SUMMONS = BUILDER
            .comment("Most summoned souls you can have out at once.")
            .defineInRange("maxSummons", 3, 1, 32);
    public static final ModConfigSpec.DoubleValue SOUL_ABSORB_RATE = BUILDER
            .comment("Share of a soul's EP you gain by absorbing it (1.0 = all of it). (Replaces the old absorbRate.)")
            .defineInRange("absorbEpShare", 0.5, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue SOUL_ABSORB_CAP = BUILDER
            .comment("Most EP one absorbed soul can give, as a share of your own max EP (0.25 = a quarter of it).")
            .defineInRange("absorbCap", 0.25, 0.0, 100.0);
    public static final ModConfigSpec.IntValue SOUL_ABSORB_COOLDOWN_SECONDS = BUILDER
            .comment("Cooldown between absorbing souls.")
            .defineInRange("absorbCooldownSeconds", 30, 0, 86400);
    public static final ModConfigSpec.DoubleValue SOUL_POSSESS_RANGE = BUILDER
            .comment("How far away a creature can be to possess it.")
            .defineInRange("possessRange", 24.0, 2.0, 128.0);
    public static final ModConfigSpec.IntValue SOUL_POSSESS_MAX_STACKS = BUILDER
            .comment("How many souls can possess one creature (each makes it stronger).")
            .defineInRange("maxPossessionStacks", 5, 1, 100);
    public static final ModConfigSpec.DoubleValue SOUL_POSSESS_HEALTH = BUILDER
            .comment("Extra max health per possessing soul, as a share of its normal health (0.5 = +50%).")
            .defineInRange("possessHealthBonus", 0.5, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue SOUL_POSSESS_DAMAGE = BUILDER
            .comment("Extra attack damage per possessing soul, as a share of its normal damage.")
            .defineInRange("possessDamageBonus", 0.5, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue SOUL_POSSESS_SPEED = BUILDER
            .comment("Extra movement speed per possessing soul, as a share of its normal speed.")
            .defineInRange("possessSpeedBonus", 0.1, 0.0, 10.0);

    static {
        BUILDER.pop().comment("Spirit race: born in the Spirit Realm, must take a body to stay in the material world").push("spiritRace");
    }

    public static final ModConfigSpec.BooleanValue SPIRIT_RACE_IN_MENU = BUILDER
            .comment("Add Lesser Spirit to Tensura's race selection menu.")
            .define("spiritRaceInMenu", true);
    public static final ModConfigSpec.IntValue SPIRIT_MATERIAL_SECONDS = BUILDER
            .comment("How long a spirit without a body can stay in the material world before it fades and dies.")
            .defineInRange("secondsWithoutBody", 300, 10, 86400);
    public static final ModConfigSpec.IntValue SPIRIT_RELEASE_SECONDS = BUILDER
            .comment("Spirit Release at 10% or 50%: how long it lasts (it costs half your health when it ends).")
            .defineInRange("releaseSeconds", 300, 5, 86400);
    public static final ModConfigSpec.IntValue SPIRIT_RELEASE_FULL_SECONDS = BUILDER
            .comment("Spirit Release at 100%: how long it lasts (then your spirit tears free of the body).")
            .defineInRange("fullReleaseSeconds", 120, 5, 86400);
    public static final ModConfigSpec.IntValue SPIRIT_RELEASE_COOLDOWN_SECONDS = BUILDER
            .comment("Spirit Release: cooldown after a release ends.")
            .defineInRange("releaseCooldownSeconds", 60, 0, 86400);

    static {
        BUILDER.pop().comment("Rune Magic: draw runes onto paper, then use them on a creature or inscribe them on a weapon").push("runes");
    }

    public static final ModConfigSpec.BooleanValue GRANT_RUNE_MAGIC = BUILDER
            .comment("Give every player Rune Magic (only used when [startingSkill] pickOneSkill is off).")
            .define("grantRuneMagic", true);
    public static final ModConfigSpec.DoubleValue RUNE_DRAW_MAGICULES = BUILDER
            .comment("Magicules to draw one rune (plus one paper).")
            .defineInRange("drawMagicules", 30.0, 0.0, 1.0E6);
    public static final ModConfigSpec.IntValue RUNE_WEAPON_CHARGES = BUILDER
            .comment("How many hits a rune inscribed on a weapon lasts.")
            .defineInRange("weaponCharges", 32, 1, 100000);
    public static final ModConfigSpec.IntValue RUNE_INSCRIBE_SECONDS = BUILDER
            .comment("Without Rune Magic: how long a drawn rune takes to finish inscribing before it can be used.")
            .defineInRange("inscribeSecondsWithoutSkill", 120, 0, 86400);
    public static final ModConfigSpec.DoubleValue RUNE_TOME_CHANCE = BUILDER
            .comment("Chance a loot chest holds a Rune Tome (reading it teaches its rune).")
            .defineInRange("runeTomeChestChance", 0.15, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue RUNE_PAPER_LOOT_CHANCE = BUILDER
            .comment("Chance a loot chest holds a ready-to-use rune paper.")
            .defineInRange("runePaperChestChance", 0.10, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue RUNE_POWER = BUILDER
            .comment("Multiplies every rune's damage, healing and durations.")
            .defineInRange("runePower", 1.0, 0.1, 100.0);

    static {
        BUILDER.pop().comment("Starting skill: after picking a race, new players pick one of this addon's skills").push("startingSkill");
    }

    public static final ModConfigSpec.BooleanValue STARTING_SKILL_PICK = BUILDER
            .comment("New players pick one of this addon's skills after choosing their race, instead of being given them all.",
                    "Players who already have this addon's skills keep them. Off: everyone gets every skill whose grant",
                    "option is on, as before.")
            .define("pickOneSkill", true);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
