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
            helper.assertTrue(husk.getHealth() < last, "hit " + hit + " landed");
            helper.assertTrue(CombatMode.isStunned(husk), "hit " + hit + " stunned it");
            helper.assertTrue(CombatMode.combo(player) == hit, "combo at " + hit + ", is " + CombatMode.combo(player));
            last = husk.getHealth();
        }
        husk.setDeltaMovement(Vec3.ZERO);
        player.attack(husk);
        float finisherDamage = last - husk.getHealth();
        helper.assertTrue(finisherDamage >= 3, "the finisher hit hard, " + finisherDamage);
        helper.assertTrue(husk.getDeltaMovement().y > 0.3, "and launched it, " + husk.getDeltaMovement());
        helper.assertTrue(CombatMode.combo(player) == 0, "the combo starts over");
        // Launched husks fly into neighbouring tests otherwise.
        husk.discard();
        helper.succeed();
    }

    /** A punch thrown while jumping is an uppercut: it launches the target straight up. */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void jumpingPunchIsAnUppercut(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, true);
        Husk husk = dummy(helper, 4.5, 3.0);
        husk.setDeltaMovement(Vec3.ZERO);
        CombatMode.markUppercut(player);
        player.attack(husk);
        Vec3 motion = husk.getDeltaMovement();
        helper.assertTrue(motion.y > 0.9, "launched up, " + motion);
        helper.assertTrue(Math.abs(motion.x) < 0.3 && Math.abs(motion.z) < 0.3, "straight up, " + motion);
        helper.assertTrue(husk.getHealth() <= 1000 - 2, "with extra damage, at " + husk.getHealth());
        helper.assertTrue(CombatMode.isStunned(husk), "and stunned");
        double startY = husk.getY();
        // The stun mustn't hold it down: two ticks later it's two blocks up (the test space's ceiling is just above).
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(husk.getY() - startY > 1.8, "flew up, " + (husk.getY() - startY));
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
        helper.assertFalse(CombatMode.isStunned(husk), "no stun");
        helper.assertTrue(CombatMode.combo(player) == 0, "no combo");
        helper.assertFalse(CombatMode.slam(player), "no slam");
        helper.succeed();
    }
}
