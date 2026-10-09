package com.tensurafragments.test;

import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.combat.CombatMode;
import com.tensurafragments.combat.ExplosionMoves;
import com.tensurafragments.combat.FightingStyle;
import com.tensurafragments.skill.ModSkills;
import io.github.manasmods.tensura.ability.SkillHelper;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Explosion skill and its fighting style. */
@GameTestHolder(TensuraFragments.MODID)
@PrefixGameTestTemplate(false)
public final class ExplosionGameTests {
    private static final int GROUND = TestPlayers.GROUND;

    private ExplosionGameTests() {
    }

    /** A creative player (so magicule costs are paid) with the Explosion skill, in the Explosion style. */
    private static ServerPlayer bomber(GameTestHelper helper, boolean combat) {
        ServerPlayer player = TestPlayers.spawn(helper, 4.5, 1.5);
        player.getInventory().clearContent();
        player.setGameMode(GameType.CREATIVE);
        SkillHelper.learnSkill(player, ModSkills.EXPLOSION.get());
        player.setData(ModRegistries.COMBAT_MODE, combat);
        player.setData(ModRegistries.COMBAT_STYLE, FightingStyle.EXPLOSION.ordinal());
        TestPlayers.clearEp(player);
        player.setYRot(0);
        player.setYHeadRot(0);
        player.setXRot(0);
        return player;
    }

    private static Husk dummy(GameTestHelper helper, double x, double z) {
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(x, GROUND, z));
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        husk.setHealth(1000);
        return husk;
    }

    /** Everything this player has waiting goes off now (moves that queue more, like the punch's blast, included). */
    private static void landAll(ServerPlayer player) {
        for (int i = 0; i < 40 && CombatMode.hasPending(player); i++) {
            CombatMode.landNow(player);
        }
    }

    /** Only players with the Explosion skill get the Explosion style when they switch styles. */
    @GameTest(template = "platform")
    public static void styleNeedsTheSkill(GameTestHelper helper) {
        ServerPlayer without = TestPlayers.spawn(helper, 2.5, 1.5);
        without.setData(ModRegistries.COMBAT_STYLE, FightingStyle.KI.ordinal());
        CombatMode.cycleStyle(without);
        helper.assertTrue(CombatMode.style(without) == FightingStyle.BRAWLER, "skipped without the skill: "
                + CombatMode.style(without));
        ServerPlayer with = bomber(helper, true);
        with.setData(ModRegistries.COMBAT_STYLE, FightingStyle.KI.ordinal());
        CombatMode.cycleStyle(with);
        helper.assertTrue(CombatMode.style(with) == FightingStyle.EXPLOSION, "offered with it: " + CombatMode.style(with));
        helper.succeed();
    }

    /** An Explosion punch goes off in its face, catching what's right behind it too. */
    @GameTest(template = "platform")
    public static void punchesExplode(GameTestHelper helper) {
        ServerPlayer player = bomber(helper, true);
        player.setGameMode(GameType.SURVIVAL);
        Husk target = dummy(helper, 4.5, 3.0);
        Husk behind = dummy(helper, 4.5, 4.0);
        player.attack(target);
        helper.assertTrue(target.getHealth() == 1000, "held back for the palm");
        landAll(player);
        helper.assertTrue(target.getHealth() < 1000, "the punch landed");
        helper.assertTrue(behind.getHealth() < 1000, "and the blast caught the one behind");
        target.discard();
        behind.discard();
        helper.succeed();
    }

    /** AP Shot flies to what you aim at and blows up on it. */
    @GameTest(template = "platform")
    public static void apShotHitsFarAway(GameTestHelper helper) {
        ServerPlayer player = bomber(helper, false);
        Husk far = dummy(helper, 4.5, 7.5);
        helper.assertTrue(ExplosionMoves.apShot(player), "fired");
        helper.assertTrue(far.getHealth() == 1000, "not before the palm opens");
        landAll(player);
        helper.assertTrue(far.getHealth() < 1000, "hit 6 blocks away, at " + far.getHealth());
        far.discard();
        helper.succeed();
    }

    /** Grab, then detonate what you hold: it's hurt and flung. */
    @GameTest(template = "platform")
    public static void grabAndDetonate(GameTestHelper helper) {
        ServerPlayer player = bomber(helper, true);
        player.setGameMode(GameType.SURVIVAL);
        Husk husk = dummy(helper, 4.5, 3.0);
        helper.assertTrue(CombatMode.grab(player, husk), "grabbed");
        CombatMode.grabOrThrow(player);
        landAll(player);
        helper.assertFalse(CombatMode.isHeld(husk), "let go");
        helper.assertTrue(husk.getHealth() < 1000, "blown up");
        helper.assertTrue(husk.getDeltaMovement().z > 0.8, "and flung forward, " + husk.getDeltaMovement());
        husk.discard();
        helper.succeed();
    }

    /** Howitzer Impact's explosion hits everything around where you come down. */
    @GameTest(template = "platform")
    public static void howitzerHitsAround(GameTestHelper helper) {
        ServerPlayer player = bomber(helper, false);
        Husk a = dummy(helper, 3.0, 3.5);
        Husk b = dummy(helper, 6.0, 3.5);
        helper.assertTrue(ExplosionMoves.howitzer(player), "launched");
        landAll(player);
        helper.assertTrue(a.getHealth() < 1000 && b.getHealth() < 1000, "both caught: " + a.getHealth() + ", "
                + b.getHealth());
        a.discard();
        b.discard();
        helper.succeed();
    }

    /** Hovering keeps you from fall damage and shows as hovering. */
    @GameTest(template = "platform")
    public static void hovering(GameTestHelper helper) {
        ServerPlayer player = bomber(helper, true);
        player.setOnGround(false);
        player.fallDistance = 10;
        ExplosionMoves.hover(player);
        helper.assertTrue(ExplosionMoves.isHovering(player), "hovering");
        helper.assertTrue(player.fallDistance == 0, "no fall damage waiting");
        helper.succeed();
    }
}
