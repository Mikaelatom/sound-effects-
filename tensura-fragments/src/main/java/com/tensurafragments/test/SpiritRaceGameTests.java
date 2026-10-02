package com.tensurafragments.test;

import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.spiritrace.SpiritPassage;
import com.tensurafragments.spiritrace.SpiritPassageTestAccess;
import com.tensurafragments.spiritrace.SpiritRace;
import com.tensurafragments.spiritrace.SpiritRaces;
import com.tensurafragments.spiritrace.SpiritRealm;
import io.github.manasmods.manascore.race.api.ManasRace;
import io.github.manasmods.manascore.race.api.ManasRaceInstance;
import io.github.manasmods.manascore.race.api.RaceAPI;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.registry.skill.IntrinsicSkills;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Spirit race: its line of evolutions, the Spirit Realm, and the clock on a spirit without a body. */
@GameTestHolder(TensuraFragments.MODID)
@PrefixGameTestTemplate(false)
public final class SpiritRaceGameTests {
    private SpiritRaceGameTests() {
    }

    private static ServerPlayer spirit(GameTestHelper helper, ManasRace race) {
        ServerPlayer player = TestPlayers.spawn(helper, 4.5, 4.5);
        helper.assertTrue(RaceAPI.getRaceFrom(player).setRace(race, false), "race set");
        return player;
    }

    private static ManasRaceInstance instance(ServerPlayer player) {
        return RaceAPI.getRaceFrom(player).getRace().orElseThrow();
    }

    @GameTest(template = "platform")
    public static void racesAndRealmExist(GameTestHelper helper) {
        int count = 0;
        for (ManasRace race : RaceAPI.getRaceRegistry()) {
            if (race instanceof SpiritRace) {
                count++;
            }
        }
        helper.assertTrue(count == 11, "11 spirit races, found " + count);
        // The test server only creates the vanilla dimensions (Tensura's Hell is missing too), so check the realm's
        // data loaded: its dimension type, biome and terrain settings.
        var registries = helper.getLevel().registryAccess();
        var id = SpiritRealm.KEY.location();
        helper.assertTrue(registries.registryOrThrow(net.minecraft.core.registries.Registries.DIMENSION_TYPE).containsKey(id),
                "Spirit Realm dimension type");
        helper.assertTrue(registries.registryOrThrow(net.minecraft.core.registries.Registries.BIOME).containsKey(id),
                "Spirit Realm biome");
        helper.assertTrue(registries.registryOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS).containsKey(id),
                "Spirit Realm terrain");
        // The race selection menu's own list (what the screen shows).
        ServerPlayer player = TestPlayers.spawn(helper, 4.5, 4.5);
        var menu = new io.github.manasmods.tensura.menu.ReincarnationMenu(0, player.getInventory(), player);
        helper.assertTrue(menu.getRacePool().contains(SpiritRaces.LESSER.get()), "Lesser Spirit is on the race menu: "
                + menu.getRacePool().stream().map(ManasRace::getRegistryName).toList());
        helper.succeed();
    }

    /** A Lesser Spirit: born in the Spirit Realm, with Possession and the Book of Passage. */
    @GameTest(template = "platform")
    public static void lesserSpiritBasics(GameTestHelper helper) {
        ServerPlayer player = spirit(helper, SpiritRaces.LESSER.get());
        helper.assertTrue(instance(player).getRespawnDimension(player).getFirst() == SpiritRealm.KEY, "respawns in the Spirit Realm");
        helper.assertTrue(SkillAPI.getSkillsFrom(player).getSkill(IntrinsicSkills.POSSESSION.get()).isPresent(), "has Possession");
        helper.assertTrue(player.getInventory().countItem(ModRegistries.BOOK_OF_PASSAGE.get()) == 1, "carries the Book of Passage");
        helper.assertFalse(instance(player).is(io.github.manasmods.tensura.data.TensuraRaceTags.SPAWN_AS_SPIRITUAL),
                "not born a bodiless spirit (that's only for the material world)");
        helper.succeed();
    }

    /** Out in the material world without a body: the clock runs, and the spirit dies when it runs out. */
    @GameTest(template = "platform")
    public static void spiritFadesWithoutABody(GameTestHelper helper) {
        ServerPlayer player = spirit(helper, SpiritRaces.LESSER.get());
        IExistence existence = TensuraStorages.getExistenceFrom(player);
        existence.setSpiritualForm(true);
        SpiritPassageTestAccess.tick(player);
        long deadline = SpiritPassage.deadline(player);
        helper.assertTrue(deadline == helper.getLevel().getGameTime() + SpiritPassage.limitTicks(),
                "a 5 minute clock started, deadline " + deadline);
        player.setData(ModRegistries.SPIRIT_DEADLINE, helper.getLevel().getGameTime());
        SpiritPassageTestAccess.tick(player);
        helper.assertTrue(player.isDeadOrDying(), "faded and died");
        helper.succeed();
    }

    /** Taking a body (Tensura's Possession ends spiritual form) stops the clock: the spirit can stay. */
    @GameTest(template = "platform")
    public static void possessingABodyStopsTheClock(GameTestHelper helper) {
        ServerPlayer player = spirit(helper, SpiritRaces.LESSER.get());
        IExistence existence = TensuraStorages.getExistenceFrom(player);
        existence.setSpiritualForm(true);
        SpiritPassageTestAccess.tick(player);
        helper.assertTrue(SpiritPassage.deadline(player) != 0, "clock running");
        existence.setSpiritualForm(false);
        SpiritPassageTestAccess.tick(player);
        helper.assertTrue(SpiritPassage.deadline(player) == 0, "clock stopped");
        player.setData(ModRegistries.SPIRIT_DEADLINE, 0L);
        SpiritPassageTestAccess.tick(player);
        helper.assertTrue(player.isAlive() && SpiritPassage.deadline(player) == 0, "stays, alive");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void evolutionLine(GameTestHelper helper) {
        ServerPlayer player = spirit(helper, SpiritRaces.LESSER.get());
        helper.assertTrue(instance(player).getNextEvolutions(player).equals(java.util.List.of(SpiritRaces.GREATER.get())),
                "Lesser -> Greater");
        helper.assertTrue(RaceAPI.getRaceFrom(player).evolveRace(SpiritRaces.GREATER.get()), "evolved to Greater");
        helper.assertTrue(instance(player).getNextEvolutions(player).size() == 7, "Greater -> any of 7 elements");
        ManasRace flame = SpiritRaces.ELEMENTS.get(SpiritRace.SpiritElement.FLAME).get();
        helper.assertTrue(RaceAPI.getRaceFrom(player).evolveRace(flame), "evolved to Flame Spirit");
        helper.assertTrue(SkillAPI.getSkillsFrom(player).getSkill(IntrinsicSkills.FLAME_TRANSFORM.get()).isPresent(),
                "Flame Spirit can become flame");
        // Heroic only as a True Hero, Demonic only as a True Demon Lord.
        IExistence existence = TensuraStorages.getExistenceFrom(player);
        helper.assertTrue(instance(player).getEvolutionProgress(player, SpiritRaces.HEROIC.get()) == 0, "not a hero yet");
        helper.assertTrue(instance(player).getEvolutionProgress(player, SpiritRaces.DEMONIC.get()) == 0, "not a demon lord yet");
        existence.setTrueHero(true);
        helper.assertTrue(instance(player).getEvolutionProgress(player, SpiritRaces.HEROIC.get()) >= 100,
                "a True Hero can become a Heroic Spirit: " + instance(player).getEvolutionProgress(player, SpiritRaces.HEROIC.get()));
        helper.assertTrue(instance(player).getEvolutionProgress(player, SpiritRaces.DEMONIC.get()) == 0, "but not Demonic");
        helper.assertTrue(((SpiritRace) instance(player).getRace()).getAwakeningEvolution(instance(player), player)
                == SpiritRaces.HEROIC.get(), "hero awakening -> Heroic Spirit");
        helper.assertTrue(((SpiritRace) instance(player).getRace()).getHarvestFestivalEvolution(instance(player), player)
                == SpiritRaces.DEMONIC.get(), "demon lord awakening -> Demonic Spirit");
        helper.assertTrue(RaceAPI.getRaceFrom(player).evolveRace(SpiritRaces.HEROIC.get()), "evolved to Heroic Spirit");
        helper.assertTrue(SkillAPI.getSkillsFrom(player).getSkill(IntrinsicSkills.FLAME_TRANSFORM.get()).isPresent(),
                "the Heroic Spirit keeps its element");
        helper.succeed();
    }

    private static io.github.manasmods.manascore.skill.api.ManasSkillInstance release(ServerPlayer player) {
        return SkillAPI.getSkillsFrom(player).getSkill(com.tensurafragments.skill.ModSkills.SPIRIT_RELEASE.get()).orElseThrow();
    }

    /** Spirit Release is a spirit's own skill, and needs a body to release through. */
    @GameTest(template = "platform")
    public static void spiritReleaseNeedsABody(GameTestHelper helper) {
        ServerPlayer player = spirit(helper, SpiritRaces.LESSER.get());
        helper.assertTrue(SkillAPI.getSkillsFrom(player).getSkill(com.tensurafragments.skill.ModSkills.SPIRIT_RELEASE.get())
                .isPresent(), "spirits have Spirit Release");
        TensuraStorages.getExistenceFrom(player).setSpiritualForm(true);
        helper.assertFalse(com.tensurafragments.spiritrace.SpiritRelease.press(player, release(player), 0), "no body, no release");
        ServerPlayer human = TestPlayers.spawn(helper, 1.5, 1.5);
        helper.assertTrue(SkillAPI.getSkillsFrom(human).getSkill(com.tensurafragments.skill.ModSkills.SPIRIT_RELEASE.get())
                .isEmpty(), "other races don't have it");
        helper.succeed();
    }

    /** 50%: half as strong again for 5 minutes, then the body pays half its health. */
    @GameTest(template = "platform")
    public static void spiritReleaseHalfPower(GameTestHelper helper) {
        ServerPlayer player = spirit(helper, SpiritRaces.LESSER.get());
        TensuraStorages.getExistenceFrom(player).setSpiritualForm(false);
        double attack = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        double health = player.getMaxHealth();
        player.setHealth(player.getMaxHealth());
        helper.assertTrue(com.tensurafragments.spiritrace.SpiritRelease.press(player, release(player), 1), "released 50%");
        var state = com.tensurafragments.spiritrace.SpiritRelease.state(player);
        helper.assertTrue(state != null && state.percent() == 50
                && state.until() == helper.getLevel().getGameTime() + 300 * 20, "50% for 5 minutes");
        helper.assertTrue(Math.abs(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                - attack * 1.5) < 0.01, "attack x1.5");
        helper.assertTrue(Math.abs(player.getMaxHealth() - health * 1.5) < 0.01 && player.getHealth() == player.getMaxHealth(),
                "max health x1.5, filled in");
        // Time's up.
        player.setData(ModRegistries.SPIRIT_RELEASE, new com.tensurafragments.spiritrace.SpiritRelease.State(50,
                helper.getLevel().getGameTime()));
        com.tensurafragments.spiritrace.SpiritReleaseTestAccess.tick(player);
        helper.assertTrue(com.tensurafragments.spiritrace.SpiritRelease.state(player) == null, "ended");
        helper.assertTrue(Math.abs(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                - attack) < 0.01 && Math.abs(player.getMaxHealth() - health) < 0.01, "stats back to normal");
        helper.assertTrue(Math.abs(player.getHealth() - health / 2) < 0.01, "half its health, at " + player.getHealth());
        helper.assertFalse(SpiritPassage.isSpiritual(player), "still in its body");
        helper.assertFalse(com.tensurafragments.spiritrace.SpiritRelease.press(player, release(player), 0), "cooling down");
        helper.succeed();
    }

    /** 100%: double strength for 2 minutes, then the spirit tears free of its body. */
    @GameTest(template = "platform")
    public static void spiritReleaseFullPowerCostsTheBody(GameTestHelper helper) {
        ServerPlayer player = spirit(helper, SpiritRaces.LESSER.get());
        TensuraStorages.getExistenceFrom(player).setSpiritualForm(false);
        double attack = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        helper.assertTrue(com.tensurafragments.spiritrace.SpiritRelease.press(player, release(player), 2), "released 100%");
        var state = com.tensurafragments.spiritrace.SpiritRelease.state(player);
        helper.assertTrue(state.percent() == 100 && state.until() == helper.getLevel().getGameTime() + 120 * 20,
                "100% for 2 minutes");
        helper.assertTrue(Math.abs(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                - attack * 2) < 0.01, "attack x2");
        player.setData(ModRegistries.SPIRIT_RELEASE, new com.tensurafragments.spiritrace.SpiritRelease.State(100,
                helper.getLevel().getGameTime()));
        com.tensurafragments.spiritrace.SpiritReleaseTestAccess.tick(player);
        helper.assertTrue(SpiritPassage.isSpiritual(player), "torn free of its body");
        helper.assertTrue(player.isAlive(), "but alive");
        SpiritPassageTestAccess.tick(player);
        helper.assertTrue(SpiritPassage.deadline(player) != 0, "and the clock on the material world starts again");
        helper.succeed();
    }

    /** Pressing again ends it early, with the same cost. */
    @GameTest(template = "platform")
    public static void spiritReleaseEndsEarly(GameTestHelper helper) {
        ServerPlayer player = spirit(helper, SpiritRaces.LESSER.get());
        TensuraStorages.getExistenceFrom(player).setSpiritualForm(false);
        player.setHealth(player.getMaxHealth());
        helper.assertTrue(com.tensurafragments.spiritrace.SpiritRelease.press(player, release(player), 0), "released 10%");
        float before = player.getHealth();
        helper.assertTrue(com.tensurafragments.spiritrace.SpiritRelease.press(player, release(player), 0), "ended early");
        helper.assertTrue(com.tensurafragments.spiritrace.SpiritRelease.state(player) == null, "no longer released");
        helper.assertTrue(player.getHealth() <= before / 2 + 0.01, "half health, at " + player.getHealth());
        helper.succeed();
    }

    /** The book always turns you into a bodiless spirit and starts the clock. */
    @GameTest(template = "platform")
    public static void crossingMakesYouASpirit(GameTestHelper helper) {
        ServerPlayer player = spirit(helper, SpiritRaces.LESSER.get());
        helper.assertFalse(SpiritPassage.isSpiritual(player), "your own form to begin with");
        SpiritPassage.cross(player);
        helper.assertTrue(SpiritPassage.isSpiritual(player), "a bodiless spirit");
        helper.assertTrue(SpiritPassage.deadline(player) == helper.getLevel().getGameTime() + SpiritPassage.limitTicks(),
                "the clock started");
        helper.succeed();
    }

    /** Dying as a spirit: you come back in your own form, with no clock (it used to leave you a spirit). */
    @GameTest(template = "platform")
    public static void respawnInYourOwnForm(GameTestHelper helper) {
        ServerPlayer player = spirit(helper, SpiritRaces.LESSER.get());
        SpiritPassage.cross(player);
        player.hurt(player.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
        helper.assertTrue(player.isDeadOrDying(), "died");
        // However it died, it comes back in its own form.
        TensuraStorages.getExistenceFrom(player).setSpiritualForm(true);
        com.tensurafragments.spiritrace.SpiritRaceEvents.onRespawn(
                new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent(player, false));
        helper.assertFalse(SpiritPassage.isSpiritual(player), "respawned in your own form");
        helper.assertTrue(SpiritPassage.deadline(player) == 0, "no clock");
        helper.succeed();
    }

    /** Tensura drains magicules from spiritual forms outside its spirit dimensions; not from spirits. */
    @GameTest(template = "platform")
    public static void spiritsDontLoseMagicules(GameTestHelper helper) throws Exception {
        var regen = io.github.manasmods.tensura.storage.ep.ExistenceStorage.class.getDeclaredMethod("handleMagiculeRegen",
                IExistence.class, net.minecraft.world.entity.LivingEntity.class, double.class, double.class);
        regen.setAccessible(true);
        ServerPlayer spirit = spirit(helper, SpiritRaces.LESSER.get());
        SpiritPassage.cross(spirit);
        IExistence spiritExistence = TensuraStorages.getExistenceFrom(spirit);
        spiritExistence.setMagicule(1000);
        regen.invoke(null, spiritExistence, spirit, 0.0, 0.0);
        // (Tensura still trims 5 off anything over your max; the spiritual drain is 115.)
        helper.assertTrue(spiritExistence.getMagicule() >= 990, "a spirit keeps its magicules: " + spiritExistence.getMagicule());
        // Control: anything else in spiritual form does lose them.
        ServerPlayer other = TestPlayers.spawn(helper, 1.5, 1.5);
        IExistence otherExistence = TensuraStorages.getExistenceFrom(other);
        otherExistence.setSpiritualForm(true);
        otherExistence.setMagicule(1000);
        regen.invoke(null, otherExistence, other, 0.0, 0.0);
        helper.assertTrue(otherExistence.getMagicule() <= 1000 - 115, "others still drain: " + otherExistence.getMagicule());
        helper.succeed();
    }

    /** R (the race ability) toggles flight in your own form too. */
    @GameTest(template = "platform")
    public static void flyInAnyForm(GameTestHelper helper) {
        ServerPlayer player = spirit(helper, SpiritRaces.LESSER.get());
        helper.assertFalse(SpiritPassage.isSpiritual(player), "own form");
        instance(player).onActivateAbility(player);
        helper.assertTrue(player.getAbilities().mayfly, "can fly");
        instance(player).onActivateAbility(player);
        helper.assertFalse(player.getAbilities().mayfly, "and land again");
        helper.succeed();
    }
}
