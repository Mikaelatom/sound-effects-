package com.tensurafragments.test;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.shikigami.Barrier;
import com.tensurafragments.shikigami.BarrierAnchorEntity;
import com.tensurafragments.shikigami.Paper;
import com.tensurafragments.shikigami.ShikigamiControl;
import com.tensurafragments.shikigami.ShikigamiEntity;
import com.tensurafragments.skill.ModSkills;
import io.github.manasmods.tensura.ability.SkillHelper;
import java.util.List;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
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

    @GameTest(template = "platform")
    public static void substitutionTakesTheHit(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 4.5, 2);
        helper.assertTrue(ShikigamiControl.readySubstitution(player), "doll readied");
        player.hurt(player.damageSources().generic(), 6.0F);

        helper.assertTrue(player.getHealth() == player.getMaxHealth(), "the paper doll took the hit");
        helper.assertTrue(Paper.count(player) == 1, "one paper used");
        helper.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void mistimedSubstitutionGoesOnCooldown(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 4.5, 2);
        helper.assertTrue(ShikigamiControl.readySubstitution(player), "doll readied");
        helper.runAfterDelay(20, () -> {
            player.hurt(player.damageSources().generic(), 6.0F);
            helper.assertTrue(player.getHealth() < player.getMaxHealth(), "hit after the window lands");
            helper.assertTrue(Paper.count(player) == 2, "no paper used");
            helper.assertFalse(ShikigamiControl.readySubstitution(player), "whiff puts it on cooldown");
            helper.succeed();
        });
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

    private static void anchor(GameTestHelper helper, ServerPlayer owner, double x, double z, int order) {
        Vec3 pos = helper.absoluteVec(new Vec3(x, GROUND, z));
        helper.getLevel().addFreshEntity(BarrierAnchorEntity.create(owner, pos, order));
    }
}
