package com.tensurafragments.test;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.shikigami.Barrier;
import com.tensurafragments.shikigami.BarrierAnchorEntity;
import com.tensurafragments.shikigami.Paper;
import com.tensurafragments.shikigami.ShikigamiControl;
import com.tensurafragments.shikigami.ShikigamiEntity;
import com.tensurafragments.skill.EquippedSkills;
import com.tensurafragments.skill.ModSkills;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.ability.SkillHelper;
import java.util.List;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-world tests for Shikigami Control. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(TensuraFragments.MODID)
@PrefixGameTestTemplate(false)
public final class ShikigamiGameTests {
    private static final int GROUND = TestPlayers.GROUND;

    private ShikigamiGameTests() {
    }

    /** A survival player with paper, plenty of magicules and the skill. */
    private static ServerPlayer caster(GameTestHelper helper, double x, double z, int paper) {
        ServerPlayer player = TestPlayers.spawn(helper, x, z);
        TestPlayers.giveMagicules(player, 100_000);
        if (paper > 0) {
            player.getInventory().add(new ItemStack(Items.PAPER, paper));
        }
        SkillHelper.learnSkill(player, ModSkills.SHIKIGAMI_CONTROL.get());
        return player;
    }

    private static void lookAt(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(relative)));
    }

    private static ShikigamiEntity summonFrom(GameTestHelper helper, ServerPlayer player, BlockPos relative, Block block) {
        helper.setBlock(relative, block);
        lookAt(helper, player, relative);
        helper.assertTrue(ShikigamiControl.summon(player), "summon should succeed on " + block);
        List<ShikigamiEntity> all = ShikigamiControl.shikigami(player);
        return all.get(all.size() - 1);
    }

    /** A shikigami ignores a monster you haven't hit, and goes for it once you do. */
    @GameTest(template = "platform", timeoutTicks = 120)
    public static void shikigamiOnlyAttackWhatYouHit(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 1.5, 2);
        ShikigamiEntity shikigami = summonFrom(helper, player, new BlockPos(4, GROUND, 4), Blocks.STONE);
        net.minecraft.world.entity.monster.Husk husk = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.HUSK,
                new Vec3(4.5, GROUND, 7.5));
        helper.onEachTick(() -> {
            if (husk.getHealth() == husk.getMaxHealth()) {
                helper.assertTrue(shikigami.getTarget() == null, "went for a monster you didn't hit");
            }
        });
        helper.runAfterDelay(40, () -> {
            husk.hurt(player.damageSources().playerAttack(player), 1);
            helper.succeedWhen(() -> helper.assertTrue(shikigami.getTarget() == husk, "goes for what you hit"));
        });
    }

    @GameTest(template = "platform")
    public static void summonTurnsBlockIntoShikigami(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 1.5, 3);
        BlockPos pos = new BlockPos(4, GROUND, 5);
        ShikigamiEntity shikigami = summonFrom(helper, player, pos, Blocks.STONE);

        helper.assertBlockPresent(Blocks.AIR, pos);
        helper.assertTrue(Paper.count(player) == 2, "one paper used, had " + Paper.count(player));
        helper.assertTrue(shikigami.getBlock().is(Blocks.STONE), "shikigami keeps its block");
        helper.assertTrue(shikigami.isOwnedBy(player), "shikigami belongs to the caster");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void harderBlocksMakeTougherShikigami(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 1.5, 3);
        ShikigamiEntity dirt = summonFrom(helper, player, new BlockPos(2, GROUND, 5), Blocks.DIRT);
        ShikigamiEntity obsidian = summonFrom(helper, player, new BlockPos(6, GROUND, 5), Blocks.OBSIDIAN);

        helper.assertTrue(obsidian.getMaxHealth() > dirt.getMaxHealth() * 2,
                "obsidian " + obsidian.getMaxHealth() + " should be far tougher than dirt " + dirt.getMaxHealth());
        helper.assertTrue(dirt.getAttributeValue(Attributes.MOVEMENT_SPEED) > obsidian.getAttributeValue(Attributes.MOVEMENT_SPEED),
                "dirt should be faster than obsidian");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void noLimitOnShikigami(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 0.5, 8);
        for (int x = 1; x <= 7; x += 2) {
            summonFrom(helper, player, new BlockPos(x, GROUND, 4), Blocks.STONE);
            summonFrom(helper, player, new BlockPos(x, GROUND, 6), Blocks.STONE);
        }
        helper.assertTrue(ShikigamiControl.shikigami(player).size() == 8,
                "all 8 shikigami stay out, have " + ShikigamiControl.shikigami(player).size());
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void summonNeedsPaper(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 1.5, 0);
        BlockPos pos = new BlockPos(4, GROUND, 5);
        helper.setBlock(pos, Blocks.STONE);
        lookAt(helper, player, pos);

        helper.assertFalse(ShikigamiControl.summon(player), "no paper, no shikigami");
        helper.assertBlockPresent(Blocks.STONE, pos);
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void dismissGivesTheBlockBack(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 1.5, 1);
        summonFrom(helper, player, new BlockPos(4, GROUND, 5), Blocks.STONE);
        ShikigamiControl.dismissAll(player);

        helper.assertTrue(ShikigamiControl.shikigami(player).isEmpty(), "shikigami gone");
        helper.assertItemEntityPresent(Items.STONE, new BlockPos(4, GROUND, 5), 2.0);
        helper.succeed();
    }

    /** A real hit from a mob, which is what Substitution reacts to. */
    private static void hitByMob(GameTestHelper helper, ServerPlayer player, float amount) {
        Pig attacker = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(4.5, GROUND, 6.5));
        player.hurt(player.damageSources().mobAttack(attacker), amount);
    }

    @GameTest(template = "platform")
    public static void substitutionIsAutomaticWithPaper(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 4.5, 2);
        hitByMob(helper, player, 6.0F);

        helper.assertTrue(player.getHealth() == player.getMaxHealth(), "the paper doll took the hit, no button needed");
        helper.assertTrue(Paper.count(player) == 1, "one paper used");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void noPaperNoSubstitution(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 4.5, 0);
        hitByMob(helper, player, 6.0F);

        helper.assertTrue(player.getHealth() < player.getMaxHealth(), "without paper the hit lands");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void substitutionCanBeTurnedOff(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 4.5, 2);
        ShikigamiControl.toggleSubstitution(player);
        hitByMob(helper, player, 6.0F);

        helper.assertTrue(player.getHealth() < player.getMaxHealth(), "turned off: the hit lands");
        helper.assertTrue(Paper.count(player) == 2, "and no paper is used");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void fallingAndBurningDontUsePaper(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 4.5, 2);
        player.hurt(player.damageSources().fall(), 3.0F);
        player.invulnerableTime = 0;
        player.hurt(player.damageSources().onFire(), 1.0F);

        helper.assertTrue(player.getHealth() < player.getMaxHealth(), "environmental damage isn't dodged");
        helper.assertTrue(Paper.count(player) == 2, "so it doesn't eat paper");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void talismanExplodesOnImpact(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 0.5, 1);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(4.5, GROUND, 6.5));
        player.lookAt(EntityAnchorArgument.Anchor.EYES, pig.position().add(0, 0.5, 0));

        helper.assertTrue(ShikigamiControl.throwTalisman(player, Vec3.ZERO), "talisman thrown");
        helper.assertTrue(Paper.count(player) == 0, "talisman used the paper");
        helper.succeedWhen(() -> helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), "pig should be hit by the blast"));
    }

    @GameTest(template = "platform")
    public static void barrierNeedsThreeAnchors(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 0.5, 0.5, 0);
        anchor(helper, player, 1.5, 1.5, 0);
        anchor(helper, player, 7.5, 1.5, 1);

        helper.assertFalse(Barrier.raise(player), "two anchors can't make a barrier");
        helper.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void barrierKeepsHostilesAndProjectilesOut(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 0.5, 0.5, 0);
        anchor(helper, player, 1.5, 1.5, 0);
        anchor(helper, player, 7.5, 1.5, 1);
        anchor(helper, player, 7.5, 7.5, 2);
        anchor(helper, player, 1.5, 7.5, 3);
        helper.assertTrue(Barrier.raise(player), "four anchors raise the barrier");

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new Vec3(4.5, GROUND, 4.5));
        Arrow arrow = helper.spawn(EntityType.ARROW, new Vec3(4.5, GROUND + 2, 4.5));
        helper.succeedWhen(() -> {
            helper.assertTrue(arrow.isRemoved(), "a stranger's arrow inside the barrier is destroyed");
            Vec3 rel = helper.relativeVec(zombie.position());
            helper.assertTrue(rel.x < 1.5 || rel.x > 7.5 || rel.z < 1.5 || rel.z > 7.5,
                    "zombie should be pushed out, is at " + rel);
        });
    }

    @GameTest(template = "platform")
    public static void paperIsUsedBeforeLeaves(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 4.5, 1);
        player.getInventory().add(new ItemStack(Items.OAK_LEAVES, 3));

        helper.assertTrue(Paper.consume(player).potency() == 1.0F, "first talisman is paper");
        Paper.Talisman second = Paper.consume(player);
        helper.assertTrue(second.isLeaf() && second.potency() < 1.0F, "then leaves, at reduced strength");
        helper.assertTrue(Paper.count(player) == 0 && Paper.countLeaves(player) == 2, "one of each used");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void leafShikigamiAreWeaker(GameTestHelper helper) {
        ServerPlayer paperCaster = caster(helper, 2.5, 1.5, 1);
        ShikigamiEntity paper = summonFrom(helper, paperCaster, new BlockPos(2, GROUND, 5), Blocks.STONE);
        ServerPlayer leafCaster = caster(helper, 6.5, 1.5, 0);
        leafCaster.getInventory().add(new ItemStack(Items.BIRCH_LEAVES, 1));
        ShikigamiEntity leaf = summonFrom(helper, leafCaster, new BlockPos(6, GROUND, 5), Blocks.STONE);

        helper.assertTrue(leaf.isLeaf() && !paper.isLeaf(), "leaf flag set");
        helper.assertTrue(leaf.getMaxHealth() < paper.getMaxHealth(), "leaf shikigami has less health");
        helper.assertTrue(leaf.getLifetime() < paper.getLifetime(), "leaf shikigami doesn't last as long");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void leafDollOnlyBlocksPartOfTheHit(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 4.5, 0);
        player.getInventory().add(new ItemStack(Items.OAK_LEAVES, 1));
        hitByMob(helper, player, 8.0F);

        float taken = player.getMaxHealth() - player.getHealth();
        helper.assertTrue(taken > 0 && taken < 8.0F, "leaf doll should block some but not all of the hit, took " + taken);
        helper.assertTrue(Paper.countLeaves(player) == 0, "leaf used");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void hudFollowsTheEquippedSkill(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 4.5, 0);
        helper.assertFalse(EquippedSkills.isEquipped(player, ModSkills.SHIKIGAMI_CONTROL.get()), "learned but not equipped");

        TensuraStorages.getAbilityFrom(player).setAbilitySlot(0, ModSkills.SHIKIGAMI_CONTROL.get(), 0);
        helper.assertTrue(EquippedSkills.isEquipped(player, ModSkills.SHIKIGAMI_CONTROL.get()), "equipped in a slot");
        helper.assertFalse(EquippedSkills.isEquipped(player, ModSkills.GAMBIT_CARDS.get()), "cards not equipped");
        helper.succeed();
    }

    /** Four anchors in a square from (1.5, 1.5) to (7.5, 7.5), raised. */
    private static void squareBarrier(GameTestHelper helper, ServerPlayer owner) {
        anchor(helper, owner, 1.5, 1.5, 0);
        anchor(helper, owner, 7.5, 1.5, 1);
        anchor(helper, owner, 7.5, 7.5, 2);
        anchor(helper, owner, 1.5, 7.5, 3);
        helper.assertTrue(Barrier.raise(owner), "barrier raised");
    }

    private static boolean insideSquare(GameTestHelper helper, Entity entity) {
        Vec3 rel = helper.relativeVec(entity.position());
        return rel.x > 1.5 && rel.x < 7.5 && rel.z > 1.5 && rel.z < 7.5;
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void barrierStopsMobsWalkingIn(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 4.5, 0);
        squareBarrier(helper, player);
        // A passive mob, to check the wall isn't only for hostiles.
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new Vec3(0.6, GROUND, 4.5));
        helper.runAfterDelay(5, () -> {
            Vec3 in = helper.absoluteVec(new Vec3(2.6, GROUND, 4.5));
            cow.setPos(in.x, in.y, in.z); // steps through the wall between two ticks
        });
        helper.runAfterDelay(10, () -> {
            helper.assertFalse(insideSquare(helper, cow), "cow should be stopped at the wall, is at " + helper.relativeVec(cow.position()));
            helper.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 140)
    public static void huskCantReachOwnerInsideBarrier(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 4.5, 0);
        squareBarrier(helper, player);
        Husk husk = helper.spawn(EntityType.HUSK, new Vec3(0.5, GROUND, 4.5));
        husk.setTarget(player);
        helper.onEachTick(() -> {
            if (!husk.isRemoved()) {
                helper.assertFalse(insideSquare(helper, husk), "husk got inside at " + helper.relativeVec(husk.position()));
            }
        });
        helper.runAfterDelay(120, helper::succeed);
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void barrierStopsFastProjectilesPassingThrough(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 0.5, 0.5, 0);
        squareBarrier(helper, player);
        helper.runAfterDelay(2, () -> {
            Arrow arrow = helper.spawn(EntityType.ARROW, new Vec3(0.5, GROUND + 1.5, 4.5));
            arrow.setDeltaMovement(14, 0, 0); // fast enough to be on the far side after one tick
            helper.succeedWhen(() -> helper.assertTrue(arrow.isRemoved(), "arrow should be stopped by the wall"));
        });
    }

    private static void anchor(GameTestHelper helper, ServerPlayer owner, double x, double z, int order) {
        Vec3 pos = helper.absoluteVec(new Vec3(x, GROUND, z));
        helper.getLevel().addFreshEntity(BarrierAnchorEntity.create(owner, pos, order,
                new Paper.Talisman(new ItemStack(Items.PAPER), 1.0F)));
    }
}
