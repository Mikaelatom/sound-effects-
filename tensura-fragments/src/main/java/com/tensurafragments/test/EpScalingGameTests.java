package com.tensurafragments.test;

import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.magic.Blast;
import com.tensurafragments.skill.EpScaling;
import io.github.manasmods.tensura.storage.TensuraStorages;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Damage scaling with EP: Combat Mode punches and this addon's skills hit harder with more EP. */
@GameTestHolder(TensuraFragments.MODID)
@PrefixGameTestTemplate(false)
public final class EpScalingGameTests {
    private EpScalingGameTests() {
    }

    private static void setEp(ServerPlayer player, double ep) {
        var existence = TensuraStorages.getExistenceFrom(player);
        existence.setMagicule(ep);
        existence.setAura(0);
        existence.setEP(ep);
    }

    private static Husk dummy(GameTestHelper helper, double x, double z) {
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(x, TestPlayers.GROUND, z));
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        husk.setHealth(1000);
        return husk;
    }

    /** The curve: nothing extra at the base, a step per tenfold above it. */
    @GameTest(template = "platform")
    public static void multiplierCurve(GameTestHelper helper) {
        helper.assertTrue(EpScaling.multiplierFor(0) == 1 && EpScaling.multiplierFor(100) == 1, "1x up to the base");
        helper.assertTrue(Math.abs(EpScaling.multiplierFor(1000) - 1.6F) < 0.01, "1.6x at 1,000");
        helper.assertTrue(Math.abs(EpScaling.multiplierFor(1_000_000) - 3.4F) < 0.01, "3.4x at a million");
        helper.assertTrue(EpScaling.multiplierFor(1.0E40) == 10, "capped at 10x");
        helper.succeed();
    }

    /** A Combat Mode punch from someone with a million EP hits about 3.4 times as hard. */
    @GameTest(template = "platform")
    public static void punchesScaleWithEp(GameTestHelper helper) {
        ServerPlayer weak = TestPlayers.spawn(helper, 2.5, 1.5);
        ServerPlayer strong = TestPlayers.spawn(helper, 6.5, 1.5);
        for (ServerPlayer player : new ServerPlayer[] {weak, strong}) {
            player.getInventory().clearContent();
            player.setData(ModRegistries.COMBAT_MODE, true);
        }
        setEp(weak, 0);
        setEp(strong, 1_000_000);
        Husk a = dummy(helper, 2.5, 3.0);
        Husk b = dummy(helper, 6.5, 3.0);
        weak.attack(a);
        strong.attack(b);
        float weakHit = 1000 - a.getHealth();
        float strongHit = 1000 - b.getHealth();
        helper.assertTrue(weakHit > 0 && Math.abs(strongHit / weakHit - EpScaling.multiplierFor(1_000_000)) < 0.05,
                "punch " + weakHit + " vs " + strongHit);
        a.discard();
        b.discard();
        helper.succeed();
    }

    /** A skill's blast (cards, talismans) from someone with lots of EP hurts more. */
    @GameTest(template = "platform")
    public static void skillsScaleWithEp(GameTestHelper helper) {
        ServerPlayer weak = TestPlayers.spawn(helper, 1.5, 1.5);
        ServerPlayer strong = TestPlayers.spawn(helper, 7.5, 1.5);
        setEp(weak, 0);
        setEp(strong, 10_000);
        Husk a = dummy(helper, 2.5, 6.0);
        Husk b = dummy(helper, 6.5, 6.0);
        Blast.explode(helper.getLevel(), weak, weak, a.position().add(0, 1, 0), 1.5, 4, 0, 0, 1);
        Blast.explode(helper.getLevel(), strong, strong, b.position().add(0, 1, 0), 1.5, 4, 0, 0, 1);
        float weakHit = 1000 - a.getHealth();
        float strongHit = 1000 - b.getHealth();
        helper.assertTrue(weakHit > 0 && Math.abs(strongHit / weakHit - EpScaling.multiplierFor(10_000)) < 0.05,
                "blast " + weakHit + " vs " + strongHit);
        a.discard();
        b.discard();
        helper.succeed();
    }
}
