package com.tensurafragments.test;

import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.combat.CombatMode;
import com.tensurafragments.combat.FightingStyle;
import com.tensurafragments.combat.StyleMoves;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Down power (no infinite combos) and the fighting styles' moves. */
@GameTestHolder(TensuraFragments.MODID)
@PrefixGameTestTemplate(false)
public final class StyleGameTests {
    private static final int GROUND = TestPlayers.GROUND;

    private StyleGameTests() {
    }

    private static ServerPlayer fighter(GameTestHelper helper, FightingStyle style, double x, double z) {
        ServerPlayer player = TestPlayers.spawn(helper, x, z);
        player.getInventory().clearContent();
        player.setData(ModRegistries.COMBAT_MODE, true);
        player.setData(ModRegistries.COMBAT_STYLE, style.ordinal());
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

    private static void punch(ServerPlayer player, Husk husk) {
        husk.invulnerableTime = 0;
        player.attack(husk);
    }

    /** Hits fill the down gauge; a full one knocks down: no stun, no grab, and the gauge starts over. */
    @GameTest(template = "platform")
    public static void downPowerEndsCombos(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, FightingStyle.BRAWLER, 4.5, 1.5);
        Husk husk = dummy(helper, 4.5, 3.0);
        int hits = 0;
        while (!CombatMode.isDowned(husk) && hits < 20) {
            punch(player, husk);
            hits++;
            husk.setPos(helper.absoluteVec(new Vec3(4.5, GROUND, 3.0)));
            husk.setDeltaMovement(Vec3.ZERO);
        }
        helper.assertTrue(CombatMode.isDowned(husk), "knocked down");
        helper.assertTrue(hits >= 6 && hits <= 9, "after one and a bit combos, took " + hits);
        helper.assertFalse(CombatMode.isStunned(husk), "the stun ends when it goes down");
        punch(player, husk);
        helper.assertFalse(CombatMode.isStunned(husk), "and it can't be stunned while down");
        helper.assertFalse(CombatMode.grab(player, husk), "or grabbed");
        helper.assertTrue(CombatMode.downPower(husk) == 0, "the gauge starts over");
        husk.discard();
        helper.succeed();
    }

    /** Styles change damage and the finisher's place in the combo; Y cycles through them. */
    @GameTest(template = "platform")
    public static void stylesChangeThePunch(GameTestHelper helper) {
        ServerPlayer brawler = fighter(helper, FightingStyle.BRAWLER, 1.5, 1.5);
        ServerPlayer titan = fighter(helper, FightingStyle.TITAN, 4.5, 1.5);
        ServerPlayer swift = fighter(helper, FightingStyle.SWIFT, 7.5, 1.5);
        Husk a = dummy(helper, 1.5, 3.0);
        Husk b = dummy(helper, 4.5, 3.0);
        Husk c = dummy(helper, 7.5, 3.0);
        punch(brawler, a);
        punch(titan, b);
        punch(swift, c);
        float base = 1000 - a.getHealth();
        helper.assertTrue(Math.abs((1000 - b.getHealth()) / base - 1.35F) < 0.05, "Titan hits harder");
        helper.assertTrue(Math.abs((1000 - c.getHealth()) / base - 0.8F) < 0.05, "Swift lighter");
        helper.assertTrue(FightingStyle.TITAN.finisherHit() == 3 && FightingStyle.SWIFT.finisherHit() == 5,
                "Titan's finisher is the 3rd hit, Swift's the 5th");
        CombatMode.cycleStyle(brawler);
        helper.assertTrue(CombatMode.style(brawler) == FightingStyle.SWIFT, "cycles to the next style");
        a.discard();
        b.discard();
        c.discard();
        helper.succeed();
    }

    /** Swift's counter stance turns a hit aside and strikes back from behind. */
    @GameTest(template = "platform")
    public static void swiftCounter(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, FightingStyle.SWIFT, 4.5, 1.5);
        Husk husk = dummy(helper, 4.5, 3.0);
        husk.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6);
        helper.assertTrue(StyleMoves.grabKey(player) && StyleMoves.isCountering(player), "counter stance");
        float health = player.getHealth();
        husk.doHurtTarget(player);
        helper.assertTrue(player.getHealth() == health, "the hit is turned aside");
        helper.assertTrue(husk.getHealth() < 1000 && CombatMode.isStunned(husk), "and answered");
        helper.assertTrue(player.getZ() > husk.getZ(), "from behind it");
        husk.discard();
        helper.succeed();
    }

    /** Titan's tackle bowls over what's in front; iron body shrugs off stun. */
    @GameTest(template = "platform")
    public static void titanTackleAndIronBody(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, FightingStyle.TITAN, 4.5, 1.5);
        Husk husk = dummy(helper, 4.5, 3.2);
        helper.assertTrue(StyleMoves.grabKey(player) && StyleMoves.isTackling(player), "tackling");
        helper.assertTrue(StyleMoves.isArmored(player), "with super armour");
        StyleMoves.tickTackle(player);
        helper.assertTrue(husk.getHealth() < 1000 && husk.getDeltaMovement().z > 0.8, "bowled over, " + husk.getDeltaMovement());
        husk.discard();
        helper.assertTrue(StyleMoves.dashKey(player, Vec3.ZERO) && StyleMoves.isIronBody(player), "iron body");
        CombatMode.stun(player, 40);
        helper.assertFalse(CombatMode.isStunned(player), "can't be stunned");
        helper.succeed();
    }

    /** Titan's hammer fist bounces a grounded target up. */
    @GameTest(template = "platform")
    public static void titanHammerFist(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, FightingStyle.TITAN, 4.5, 1.5);
        Husk husk = dummy(helper, 4.5, 3.0);
        husk.setOnGround(true);
        CombatMode.markUppercut(player);
        punch(player, husk);
        helper.assertTrue(husk.getDeltaMovement().y > 0.4 && CombatMode.isStunned(husk), "bounced, " + husk.getDeltaMovement());
        husk.discard();
        helper.succeed();
    }

    /** Ki burst works even while stunned, and knocks down everything around. */
    @GameTest(template = "platform")
    public static void kiBurstBreaksCombos(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, FightingStyle.KI, 4.5, 1.5);
        Husk husk = dummy(helper, 4.5, 3.5);
        CombatMode.stun(player, 60);
        helper.assertTrue(StyleMoves.grabKey(player), "burst while stunned");
        helper.assertFalse(CombatMode.isStunned(player), "free");
        helper.assertTrue(CombatMode.isDowned(husk) && husk.getHealth() < 1000, "the husk is knocked down");
        helper.assertTrue(husk.getDeltaMovement().z > 0.8, "and thrown off, " + husk.getDeltaMovement());
        helper.assertFalse(StyleMoves.grabKey(player), "not again straight away");
        husk.discard();
        helper.succeed();
    }

    /** Ki palm (jump + attack) blasts the target away; vanish puts you behind what you look at. */
    @GameTest(template = "platform")
    public static void kiPalmAndVanish(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, FightingStyle.KI, 4.5, 1.5);
        Husk husk = dummy(helper, 4.5, 3.0);
        CombatMode.markUppercut(player);
        punch(player, husk);
        helper.assertTrue(husk.getDeltaMovement().z > 1.0, "blasted away, " + husk.getDeltaMovement());
        husk.setDeltaMovement(Vec3.ZERO);
        husk.setPos(helper.absoluteVec(new Vec3(4.5, GROUND, 6.0)));
        helper.assertTrue(StyleMoves.dashKey(player, Vec3.ZERO), "vanished");
        helper.assertTrue(player.getZ() > husk.getZ() && CombatMode.isDodging(player), "behind it, untouchable for a moment");
        husk.discard();
        helper.succeed();
    }

    /** Swift's dive kick spikes the first thing it meets and bounces you off it. */
    @GameTest(template = "platform")
    public static void swiftDiveKick(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, FightingStyle.SWIFT, 4.5, 2.5);
        Husk husk = dummy(helper, 4.5, 3.0);
        player.setOnGround(false);
        helper.assertTrue(CombatMode.airSpecial(player) && StyleMoves.isDiving(player), "diving");
        StyleMoves.tickDive(player);
        helper.assertTrue(husk.getHealth() < 1000 && CombatMode.isStunned(husk), "kicked");
        helper.assertFalse(StyleMoves.isDiving(player), "the dive ends on a hit");
        helper.assertTrue(player.getDeltaMovement().y > 0.4, "bouncing you up");
        husk.discard();
        helper.succeed();
    }

    /** Ki bomb blasts the ground below. */
    @GameTest(template = "platform")
    public static void kiBomb(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, FightingStyle.KI, 4.5, 4.5);
        Husk husk = dummy(helper, 5.5, 4.5);
        Vec3 up = helper.absoluteVec(new Vec3(4.5, GROUND + 4, 4.5));
        player.teleportTo(up.x, up.y, up.z);
        player.setOnGround(false);
        helper.assertTrue(CombatMode.airSpecial(player), "ki bomb");
        helper.assertTrue(husk.getHealth() < 1000, "hit the ground below");
        helper.assertTrue(player.getDeltaMovement().y > 0.3, "you hang in the air");
        husk.discard();
        helper.succeed();
    }

    /** Swift's whirlwind finisher (the 5th hit) catches everything around. */
    @GameTest(template = "platform")
    public static void swiftWhirlwind(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, FightingStyle.SWIFT, 4.5, 1.5);
        Husk target = dummy(helper, 4.5, 3.0);
        Husk beside = dummy(helper, 6.2, 1.5);
        for (int hit = 0; hit < 4; hit++) {
            punch(player, target);
            target.setPos(helper.absoluteVec(new Vec3(4.5, GROUND, 3.0)));
        }
        helper.assertTrue(beside.getHealth() == 1000, "untouched until the finisher");
        punch(player, target);
        helper.assertTrue(beside.getHealth() < 1000, "the whirlwind catches it");
        target.discard();
        beside.discard();
        helper.succeed();
    }
}
