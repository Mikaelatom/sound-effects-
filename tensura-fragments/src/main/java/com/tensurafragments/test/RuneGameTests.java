package com.tensurafragments.test;

import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.rune.Rune;
import com.tensurafragments.rune.RuneMagic;
import com.tensurafragments.rune.RunePaperItem;
import com.tensurafragments.rune.WeaponRune;
import com.tensurafragments.skill.ModSkills;
import com.tensurafragments.skill.StartingSkill;
import io.github.manasmods.manascore.race.api.RaceAPI;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.registry.race.TensuraRaces;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-world tests for Rune Magic and the starting skill choice. */
@GameTestHolder(TensuraFragments.MODID)
@PrefixGameTestTemplate(false)
public final class RuneGameTests {
    private static final int GROUND = TestPlayers.GROUND;

    private RuneGameTests() {
    }

    private static ServerPlayer runeUser(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.spawn(helper, 4.5, 1.5);
        TestPlayers.giveMagicules(player, 10_000);
        SkillHelper.learnSkill(player, ModSkills.RUNE_MAGIC.get());
        player.getInventory().clearContent();
        return player;
    }

    private static Husk dummy(GameTestHelper helper, double x, double z) {
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(x, GROUND, z));
        husk.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
        husk.setHealth(1000);
        return husk;
    }

    /** The same lines however you draw them (stroke order and direction), and every rune is its own shape. */
    @GameTest(template = "platform")
    public static void runesMatchHoweverTheyreDrawn(GameTestHelper helper) {
        Set<Set<Integer>> shapes = new HashSet<>();
        for (Rune rune : Rune.values()) {
            List<Integer> backwards = new ArrayList<>();
            int[][] strokes = rune.strokes();
            for (int s = strokes.length - 1; s >= 0; s--) {
                int[] stroke = strokes[s];
                for (int i = stroke.length - 2; i >= 2; i -= 2) {
                    helper.assertTrue(Rune.line(stroke[i], stroke[i + 1], stroke[i - 2], stroke[i - 1], backwards),
                            rune + " has only straight lines");
                }
            }
            helper.assertTrue(Rune.match(backwards) == rune, rune + " matches drawn backwards");
            helper.assertTrue(shapes.add(rune.segments()), rune + " is its own shape");
        }
        List<Integer> partial = new ArrayList<>(Rune.FROST.segments());
        partial.remove(0);
        helper.assertTrue(Rune.match(partial) == null, "a rune missing a line isn't a rune");
        List<Integer> extra = new ArrayList<>(Rune.FIRE.segments());
        extra.add(Rune.segment(Rune.dot(0, 0), Rune.dot(1, 0)));
        helper.assertTrue(Rune.match(extra) == null, "nor is one with a line too many");
        helper.succeed();
    }

    /** Drawing a rune right uses a paper and gives a rune paper; drawing it wrong keeps the paper. */
    @GameTest(template = "platform")
    public static void drawingARuneOntoPaper(GameTestHelper helper) {
        ServerPlayer player = runeUser(helper);
        player.getInventory().add(new ItemStack(Items.PAPER, 3));
        helper.assertTrue(RuneMagic.finishDrawing(player, List.of(Rune.segment(Rune.dot(0, 0), Rune.dot(1, 1)))) == null,
                "a stray line isn't a rune");
        helper.assertTrue(player.getInventory().countItem(Items.PAPER) == 3, "paper kept");
        helper.assertTrue(RuneMagic.finishDrawing(player, Rune.THUNDER.segments()) == Rune.THUNDER, "drew Thunder");
        helper.assertTrue(player.getInventory().countItem(Items.PAPER) == 2, "used one paper");
        boolean got = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            got |= RunePaperItem.runeOf(player.getInventory().getItem(i)) == Rune.THUNDER;
        }
        helper.assertTrue(got, "got a Rune of Thunder");
        player.getInventory().clearContent();
        helper.assertTrue(RuneMagic.finishDrawing(player, Rune.FIRE.segments()) == null, "no paper, no rune");
        helper.succeed();
    }

    /** On a foe a rune harms; on yourself it helps. */
    @GameTest(template = "platform")
    public static void runesOnCreatures(GameTestHelper helper) {
        ServerPlayer player = runeUser(helper);
        Husk husk = dummy(helper, 4.5, 4.5);
        RuneMagic.useOn(player, husk, Rune.FIRE);
        helper.assertTrue(husk.isOnFire() && husk.getHealth() < 1000, "Fire burned the husk");
        RuneMagic.useOn(player, husk, Rune.BIND);
        helper.assertTrue(husk.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Bind rooted it");
        player.setHealth(4);
        RuneMagic.useOn(player, player, Rune.LIFE);
        helper.assertTrue(player.getHealth() > 4 && player.hasEffect(MobEffects.REGENERATION), "Life healed you");
        RuneMagic.useOn(player, player, Rune.FIRE);
        helper.assertTrue(player.hasEffect(MobEffects.FIRE_RESISTANCE) && !player.isOnFire(), "Fire on yourself protects you");
        helper.succeed();
    }

    /** Using the rune paper from the off hand inscribes the held weapon; its hits carry the rune and use charges. */
    @GameTest(template = "platform")
    public static void inscribedWeaponCarriesTheRune(GameTestHelper helper) {
        ServerPlayer player = runeUser(helper);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        player.setItemInHand(InteractionHand.OFF_HAND, RunePaperItem.of(Rune.FIRE));
        player.getItemInHand(InteractionHand.OFF_HAND).use(player.level(), player, InteractionHand.OFF_HAND);
        WeaponRune inscribed = player.getMainHandItem().get(ModRegistries.WEAPON_RUNE.get());
        helper.assertTrue(inscribed != null && inscribed.rune() == Rune.FIRE, "the sword carries Fire");
        helper.assertTrue(player.getOffhandItem().isEmpty(), "the rune paper was used");
        Husk husk = dummy(helper, 4.5, 3.0);
        husk.hurt(player.damageSources().playerAttack(player), 4);
        helper.assertTrue(husk.isOnFire(), "the hit set it alight");
        WeaponRune after = player.getMainHandItem().get(ModRegistries.WEAPON_RUNE.get());
        helper.assertTrue(after != null && after.charges() == inscribed.charges() - 1, "one charge used");
        helper.succeed();
    }

    /** A new player (with a race) picks one skill; Rune Magic comes with the codex; there's only one pick. */
    @GameTest(template = "platform")
    public static void startingSkillPick(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.spawn(helper, 4.5, 1.5);
        player.getInventory().clearContent();
        helper.assertTrue(RaceAPI.getRaceFrom(player).setRace(TensuraRaces.HUMAN.get(), false), "race set");
        StartingSkill.grantOrOffer(player);
        helper.assertFalse(StartingSkill.hasPicked(player), "asked, not yet picked");
        helper.assertFalse(StartingSkill.hasAnyChoice(player), "no skill handed out");
        helper.assertFalse(StartingSkill.pick(player, "minecraft:stone"), "only the listed skills");
        helper.assertTrue(StartingSkill.pick(player, ModSkills.RUNE_MAGIC.getId().toString()), "picked Rune Magic");
        helper.assertTrue(SkillAPI.getSkillsFrom(player).getSkill(ModSkills.RUNE_MAGIC.getId()).isPresent(), "has it");
        helper.assertTrue(player.getInventory().countItem(ModRegistries.RUNE_CODEX.get()) == 1, "and the Rune Codex");
        helper.assertFalse(StartingSkill.pick(player, ModSkills.SOUL_REAPER.getId().toString()), "one pick only");
        helper.assertFalse(SkillAPI.getSkillsFrom(player).getSkill(ModSkills.SOUL_REAPER.getId()).isPresent(),
                "no second skill");
        helper.succeed();
    }

    /** Someone who already had this addon's skills keeps them all and isn't asked. */
    @GameTest(template = "platform")
    public static void existingPlayersKeepTheirSkills(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.spawn(helper, 4.5, 1.5);
        SkillHelper.learnSkill(player, ModSkills.SOUL_REAPER.get());
        SkillHelper.learnSkill(player, ModSkills.GAMBIT_CARDS.get());
        RaceAPI.getRaceFrom(player).setRace(TensuraRaces.HUMAN.get(), false);
        StartingSkill.grantOrOffer(player);
        helper.assertTrue(StartingSkill.hasPicked(player), "counts as already picked");
        var skills = SkillAPI.getSkillsFrom(player);
        helper.assertTrue(skills.getSkill(ModSkills.SOUL_REAPER.getId()).isPresent()
                && skills.getSkill(ModSkills.GAMBIT_CARDS.getId()).isPresent(), "kept both");
        helper.succeed();
    }
}
