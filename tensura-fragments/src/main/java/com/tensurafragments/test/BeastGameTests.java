package com.tensurafragments.test;

import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.shikigami.BeastKind;
import com.tensurafragments.shikigami.Paper;
import com.tensurafragments.shikigami.PaperBeastEntity;
import com.tensurafragments.shikigami.PaperBeasts;
import com.tensurafragments.skill.ModSkills;
import io.github.manasmods.tensura.ability.SkillHelper;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-world tests for Shikigami Control's paper beasts and possession. */
@GameTestHolder(TensuraFragments.MODID)
@PrefixGameTestTemplate(false)
public final class BeastGameTests {
    private static final int GROUND = TestPlayers.GROUND;

    private BeastGameTests() {
    }

    private static ServerPlayer caster(GameTestHelper helper, double x, double z, int paper) {
        ServerPlayer player = TestPlayers.spawn(helper, x, z);
        TestPlayers.giveMagicules(player, 100_000);
        if (paper > 0) {
            player.getInventory().add(new ItemStack(Items.PAPER, paper));
        }
        SkillHelper.learnSkill(player, ModSkills.SHIKIGAMI_CONTROL.get());
        // Keep the paper for beasts, and let hits through.
        player.setData(ModRegistries.SUBSTITUTION_ENABLED, false);
        return player;
    }

    private static PaperBeastEntity fold(GameTestHelper helper, ServerPlayer player, BeastKind kind) {
        player.setData(ModRegistries.SELECTED_BEAST, kind.ordinal());
        helper.assertTrue(PaperBeasts.fold(player), "fold " + kind);
        List<PaperBeastEntity> beasts = PaperBeasts.beasts(player);
        return beasts.get(beasts.size() - 1);
    }

    /** A paper hound ignores a monster you haven't hit, and goes for it once you do. */
    @GameTest(template = "platform", timeoutTicks = 120)
    public static void beastsOnlyAttackWhatYouHit(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 1.5, 3);
        PaperBeastEntity hound = fold(helper, player, BeastKind.HOUND);
        Husk husk = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.HUSK,
                new net.minecraft.world.phys.Vec3(4.5, TestPlayers.GROUND, 7.5));
        helper.onEachTick(() -> {
            if (husk.getHealth() == husk.getMaxHealth()) {
                helper.assertTrue(hound.getTarget() == null, "went for a monster you didn't hit");
            }
        });
        helper.runAfterDelay(40, () -> {
            husk.hurt(player.damageSources().playerAttack(player), 1);
            helper.succeedWhen(() -> helper.assertTrue(hound.getTarget() == husk, "goes for what you hit"));
        });
    }

    private static Husk dummy(GameTestHelper helper, double x, double z) {
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(x, GROUND, z));
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        husk.setHealth(1000);
        return husk;
    }

    @GameTest(template = "platform")
    public static void everyBeastFoldsFromPaper(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 1.5, 20);
        int paperNeeded = 0;
        for (BeastKind kind : BeastKind.values()) {
            PaperBeastEntity beast = fold(helper, player, kind);
            paperNeeded += kind.paper();
            helper.assertTrue(beast.getKind() == kind && beast.getType() == kind.type(), "folded a " + kind);
            helper.assertTrue(beast.isOwnedBy(player), kind + " belongs to the caster");
        }
        helper.assertTrue(PaperBeasts.beasts(player).size() == BeastKind.values().length, "one of each beast");
        helper.assertTrue(Paper.count(player) == 20 - paperNeeded, "paper used per beast, left " + Paper.count(player));
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void beastNeedsEnoughPaper(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 1.5, 2);
        player.setData(ModRegistries.SELECTED_BEAST, BeastKind.HOUND.ordinal());
        helper.assertFalse(PaperBeasts.fold(player), "a hound takes 3 paper");
        helper.assertTrue(Paper.count(player) == 2 && PaperBeasts.beasts(player).isEmpty(), "nothing used");
        helper.succeed();
    }

    /** Possess the hound and steer it forward: it runs, your body stays. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void possessedHoundGoesWhereYouSteer(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 0.5, 5);
        PaperBeastEntity hound = fold(helper, player, BeastKind.HOUND);
        helper.assertTrue(PaperBeasts.togglePossession(player), "possess the hound");
        helper.assertTrue(PaperBeasts.possessed(player) == hound && hound.isControlled(), "seeing through the hound");
        Vec3 body = player.position();
        double startZ = hound.getZ();
        // Yaw 0 is +Z (south).
        helper.onEachTick(() -> PaperBeasts.steer(player, 1, 0, false, false, 0, 0, false));
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(hound.getZ() - startZ > 2.5, "the hound ran forward, moved " + (hound.getZ() - startZ));
            helper.assertTrue(player.position().distanceTo(body) < 0.01, "the body didn't move");
            PaperBeasts.togglePossession(player);
            helper.assertTrue(PaperBeasts.possessed(player) == null && !hound.isControlled(), "back in your body");
            helper.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void possessedOwlFlies(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 1.5, 5);
        PaperBeastEntity owl = fold(helper, player, BeastKind.OWL);
        PaperBeasts.possess(player, owl);
        double startY = owl.getY();
        double startZ = owl.getZ();
        // Climb for 3 ticks (the test platform has a low roof), then glide forward.
        helper.onEachTick(() -> {
            boolean climbing = owl.tickCount < 4;
            PaperBeasts.steer(player, climbing ? 0 : 1, 0, climbing, false, 0, 0, false);
        });
        helper.runAfterDelay(3, () -> helper.assertTrue(owl.getY() - startY > 1.2,
                "the owl climbed, rose " + (owl.getY() - startY)));
        helper.runAfterDelay(9, () -> {
            helper.assertTrue(owl.getZ() - startZ > 2, "the owl flew forward, moved " + (owl.getZ() - startZ));
            helper.assertTrue(owl.getY() - startY > 1.0, "without falling");
            helper.succeed();
        });
    }

    /** Attacking while possessed bites what the beast faces, nothing else. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void possessedAttackHitsWhatItFaces(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 0.5, 5);
        PaperBeastEntity hound = fold(helper, player, BeastKind.HOUND);
        hound.setPos(helper.absoluteVec(new Vec3(4.5, GROUND, 3.5)));
        Husk ahead = dummy(helper, 4.5, 5.3);
        Husk behind = dummy(helper, 4.5, 1.5);
        PaperBeasts.possess(player, hound);
        PaperBeasts.steer(player, 0, 0, false, false, 0, 0, true);
        helper.assertTrue(ahead.getHealth() < 1000, "bit the husk ahead");
        helper.assertTrue(behind.getHealth() == 1000, "the husk behind is untouched");
        helper.assertTrue(player.getHealth() == player.getMaxHealth(), "never bites its owner");
        helper.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void hurtBodySnapsYouBack(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 1.5, 5);
        PaperBeastEntity cat = fold(helper, player, BeastKind.CAT);
        PaperBeasts.possess(player, cat);
        Husk husk = dummy(helper, 1.5, 1.5);
        player.hurt(player.damageSources().mobAttack(husk), 2.0F);
        helper.assertTrue(PaperBeasts.possessed(player) == null && !cat.isControlled(), "the hit threw you back into your body");
        helper.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void paperBurns(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 1.5, 5);
        PaperBeastEntity rabbit = fold(helper, player, BeastKind.RABBIT);
        float before = rabbit.getHealth();
        rabbit.hurt(rabbit.damageSources().inFire(), 1.0F);
        helper.assertTrue(before - rabbit.getHealth() >= 2.9F, "fire tears through paper, lost " + (before - rabbit.getHealth()));
        helper.succeed();
    }
}
