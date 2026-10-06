package com.tensurafragments.test;

import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.combat.CombatMode;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-world tests for Combat Mode: combos, hit stun, finishers and down slams. */
@GameTestHolder(TensuraFragments.MODID)
@PrefixGameTestTemplate(false)
public final class CombatGameTests {
    private static final int GROUND = TestPlayers.GROUND;

    private CombatGameTests() {
    }

    private static ServerPlayer fighter(GameTestHelper helper, boolean combat) {
        ServerPlayer player = TestPlayers.spawn(helper, 4.5, 1.5);
        player.getInventory().clearContent();
        player.setData(ModRegistries.COMBAT_MODE, combat);
        return player;
    }

    private static Husk dummy(GameTestHelper helper, double x, double z) {
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(x, GROUND, z));
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        husk.setHealth(1000);
        return husk;
    }

    /** Hits chain into a combo and stun; the 4th is a finisher that hits harder and launches. */
    @GameTest(template = "platform")
    public static void comboEndsInAFinisher(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, true);
        Husk husk = dummy(helper, 4.5, 3.0);
        float last = husk.getHealth();
        for (int hit = 1; hit <= 3; hit++) {
            player.attack(husk);
            CombatMode.landNow(player);
            helper.assertTrue(husk.getHealth() < last, "hit " + hit + " landed");
            helper.assertTrue(CombatMode.isStunned(husk), "hit " + hit + " stunned it");
            helper.assertTrue(CombatMode.combo(player) == hit, "combo at " + hit + ", is " + CombatMode.combo(player));
            last = husk.getHealth();
        }
        husk.setDeltaMovement(Vec3.ZERO);
        player.attack(husk);
        CombatMode.landNow(player);
        float finisherDamage = last - husk.getHealth();
        helper.assertTrue(finisherDamage >= 3, "the finisher hit hard, " + finisherDamage);
        helper.assertTrue(husk.getDeltaMovement().y > 0.3, "and launched it, " + husk.getDeltaMovement());
        helper.assertTrue(CombatMode.combo(player) == 0, "the combo starts over");
        // Launched husks fly into neighbouring tests otherwise.
        husk.discard();
        helper.succeed();
    }

    /** A punch lands when the fist does in its hook animation (0.22 s in), with the charge it was thrown with. */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void punchLandsWithTheAnimation(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, true);
        Husk husk = dummy(helper, 4.5, 3.0);
        player.attack(husk);
        helper.assertTrue(husk.getHealth() == 1000 && CombatMode.hasPending(player), "not yet: the fist is still coming");
        helper.runAfterDelay(3, () -> helper.assertTrue(husk.getHealth() == 1000, "still not at 3 ticks"));
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(husk.getHealth() < 1000 && CombatMode.isStunned(husk), "landed by the strike");
            helper.assertFalse(CombatMode.hasPending(player), "nothing left waiting");
            husk.discard();
            helper.succeed();
        });
    }

    /** A punch thrown while jumping is an uppercut: it launches the target straight up. */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void jumpingPunchIsAnUppercut(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, true);
        Husk husk = dummy(helper, 4.5, 3.0);
        husk.setDeltaMovement(Vec3.ZERO);
        CombatMode.markUppercut(player);
        player.attack(husk);
        CombatMode.landNow(player);
        Vec3 motion = husk.getDeltaMovement();
        helper.assertTrue(motion.y > 0.9, "launched up, " + motion);
        helper.assertTrue(Math.abs(motion.x) < 0.3 && Math.abs(motion.z) < 0.3, "straight up, " + motion);
        helper.assertTrue(husk.getHealth() <= 1000 - 2, "with extra damage, at " + husk.getHealth());
        helper.assertTrue(CombatMode.isStunned(husk), "and stunned");
        helper.assertTrue(player.getDeltaMovement().y > 0.9, "and you go up with it, " + player.getDeltaMovement());
        double startY = husk.getY();
        // The stun mustn't hold it down: two ticks later it's two blocks up (the test space's ceiling is just above).
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(husk.getY() - startY > 1.8, "flew up, " + (husk.getY() - startY));
            husk.discard();
            helper.succeed();
        });
    }

    /** Hits stun in mid-air too: a stunned creature hangs there instead of falling. */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void stunnedHangsInTheAir(GameTestHelper helper) {
        Husk husk = dummy(helper, 4.5, 3.0);
        Vec3 up = helper.absoluteVec(new Vec3(4.5, GROUND + 1.5, 3.0));
        husk.teleportTo(up.x, up.y, up.z);
        husk.setOnGround(false);
        CombatMode.stun(husk, 40);
        double startY = husk.getY();
        helper.runAfterDelay(6, () -> {
            double fell = startY - husk.getY();
            helper.assertTrue(fell > 0 && fell < 0.5, "it hung in the air, fell " + fell);
            husk.discard();
            helper.succeed();
        });
    }

    /** A hit thrown in mid-air keeps you up, so you can keep hitting what hangs there. */
    @GameTest(template = "platform")
    public static void airHitsKeepYouUp(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, true);
        Husk husk = dummy(helper, 4.5, 3.0);
        player.setOnGround(false);
        player.setDeltaMovement(0, -0.4, 0);
        player.attack(husk);
        CombatMode.landNow(player);
        helper.assertTrue(player.getDeltaMovement().y > 0.2, "lifted, " + player.getDeltaMovement());
        husk.discard();
        helper.succeed();
    }

    /** Blocking: a hit right as the block goes up is parried (no damage, attacker stunned); later ones are cut down. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void blockAndParry(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, true);
        player.setYRot(0);
        Husk first = dummy(helper, 4.0, 3.0);
        Husk second = dummy(helper, 5.0, 3.0);
        first.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(8);
        second.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(8);
        CombatMode.setBlocking(player, true);
        helper.assertTrue(CombatMode.isBlocking(player), "blocking");
        float health = player.getHealth();
        first.doHurtTarget(player);
        helper.assertTrue(player.getHealth() == health, "parried: no damage");
        helper.assertTrue(CombatMode.isStunned(first), "and the attacker is stunned");
        helper.runAfterDelay(12, () -> {
            player.invulnerableTime = 0;
            float before = player.getHealth();
            second.doHurtTarget(player);
            float blocked = before - player.getHealth();
            CombatMode.setBlocking(player, false);
            player.invulnerableTime = 0;
            second.getPersistentData().remove("tensurafragments_stun_until");
            float before2 = player.getHealth();
            second.doHurtTarget(player);
            float open = before2 - player.getHealth();
            helper.assertTrue(blocked > 0 && blocked < open * 0.5, "the block cut it down: " + blocked + " vs " + open);
            first.discard();
            second.discard();
            helper.succeed();
        });
    }

    /** Grab holds a creature in front of you; grabbing again throws it. A grab goes through a block. */
    @GameTest(template = "platform")
    public static void grabAndThrow(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, true);
        player.setYRot(0);
        Husk husk = dummy(helper, 4.5, 3.0);
        helper.assertTrue(CombatMode.grab(player, husk), "grabbed");
        helper.assertTrue(CombatMode.isHeld(husk) && CombatMode.isStunned(husk), "held and stunned");
        helper.assertTrue(husk.distanceTo(player) < 2.5 && husk.getZ() > player.getZ(), "in front of you");
        CombatMode.grabOrThrow(player);
        helper.assertTrue(CombatMode.isHeld(husk), "held on through the wind-up");
        CombatMode.landNow(player);
        helper.assertFalse(CombatMode.isHeld(husk), "let go");
        helper.assertTrue(husk.getHealth() < 1000, "the throw hurt");
        helper.assertTrue(husk.getDeltaMovement().z > 0.8 && husk.getDeltaMovement().y > 0.2, "and flung it forward, "
                + husk.getDeltaMovement());
        husk.discard();

        ServerPlayer other = TestPlayers.spawn(helper, 4.5, 3.0);
        other.setData(ModRegistries.COMBAT_MODE, true);
        CombatMode.setBlocking(other, true);
        helper.assertTrue(CombatMode.grab(player, other), "grabbed a blocking player");
        helper.assertFalse(CombatMode.isBlocking(other), "breaking the block");
        CombatMode.throwHeld(player);
        CombatMode.landNow(player);
        other.discard();
        helper.succeed();
    }

    /** A dash makes you untouchable for a moment, then has a cooldown. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void dashHasIFrames(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, true);
        Husk husk = dummy(helper, 4.5, 3.0);
        helper.assertTrue(CombatMode.dash(player, new Vec3(0, 0, 1)), "dashed");
        helper.assertFalse(CombatMode.dash(player, new Vec3(0, 0, 1)), "not again straight away");
        float health = player.getHealth();
        player.hurt(player.damageSources().mobAttack(husk), 4);
        helper.assertTrue(player.getHealth() == health, "the hit went straight through you");
        helper.runAfterDelay(com.tensurafragments.Config.COMBAT_DASH_IFRAMES.get() + 2, () -> {
            player.invulnerableTime = 0;
            player.hurt(player.damageSources().mobAttack(husk), 4);
            helper.assertTrue(player.getHealth() < health, "but not once the dash is over");
            husk.discard();
            helper.succeed();
        });
    }

    /** A stunned creature can't hurt anyone. */
    @GameTest(template = "platform")
    public static void stunnedCantHurt(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, true);
        Husk husk = dummy(helper, 4.5, 2.5);
        husk.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6);
        CombatMode.stun(husk, 40);
        float health = player.getHealth();
        husk.doHurtTarget(player);
        helper.assertTrue(player.getHealth() == health, "the stunned husk's hit did nothing");
        helper.succeed();
    }

    /** A down slam's landing hurts and knocks up everything around (not you). */
    @GameTest(template = "platform")
    public static void downSlamShockwave(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, true);
        Husk near = dummy(helper, 6.0, 1.5);
        Husk far = dummy(helper, 4.5, 8.5);
        player.teleportTo(player.getX(), player.getY() + 4, player.getZ());
        player.setOnGround(false);
        helper.assertTrue(CombatMode.slam(player) && CombatMode.isSlamming(player), "slamming");
        helper.assertTrue(player.getDeltaMovement().y < -2, "diving down");
        player.teleportTo(player.getX(), player.getY() - 4, player.getZ());
        CombatMode.land(player);
        helper.assertTrue(near.getHealth() < 1000 && near.getDeltaMovement().y > 0.2, "the shockwave hit the near husk");
        helper.assertTrue(far.getHealth() == 1000, "but not the far one");
        helper.assertTrue(player.getHealth() == player.getMaxHealth(), "and not you");
        near.discard();
        far.discard();
        helper.succeed();
    }

    /** With Combat Mode off, hits are ordinary: no combo, no stun. */
    @GameTest(template = "platform")
    public static void offMeansOrdinaryHits(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, false);
        Husk husk = dummy(helper, 4.5, 3.0);
        player.attack(husk);
        CombatMode.landNow(player);
        helper.assertFalse(CombatMode.isStunned(husk), "no stun");
        helper.assertTrue(CombatMode.combo(player) == 0, "no combo");
        helper.assertFalse(CombatMode.slam(player), "no slam");
        helper.succeed();
    }
}
