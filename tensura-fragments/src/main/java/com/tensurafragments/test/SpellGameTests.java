package com.tensurafragments.test;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.shikigami.Paper;
import com.tensurafragments.shikigami.ShikigamiControl;
import com.tensurafragments.shikigami.Spell;
import com.tensurafragments.shikigami.TalismanEntity;
import com.tensurafragments.skill.ModSkills;
import io.github.manasmods.tensura.ability.SkillHelper;
import java.util.List;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-world tests for Shikigami Control's spell talismans. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(TensuraFragments.MODID)
@PrefixGameTestTemplate(false)
public final class SpellGameTests {
    private static final int GROUND = TestPlayers.GROUND;

    private SpellGameTests() {
    }

    private static ServerPlayer caster(GameTestHelper helper, double x, double z) {
        ServerPlayer player = TestPlayers.spawn(helper, x, z);
        TestPlayers.giveMagicules(player, 100_000);
        player.getInventory().add(new ItemStack(Items.PAPER, 8));
        SkillHelper.learnSkill(player, ModSkills.SHIKIGAMI_CONTROL.get());
        return player;
    }

    private static Vec3 at(GameTestHelper helper, double x, double z) {
        return helper.absoluteVec(new Vec3(x, GROUND, z));
    }

    private static Pig pig(GameTestHelper helper, double x, double z) {
        return helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(x, GROUND, z));
    }

    @GameTest(template = "platform")
    public static void fireBurnsEnemiesButNotTheCaster(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 3.5, 4.5);
        Pig pig = pig(helper, 5.5, 4.5);
        Spell.FIRE.cast(helper.getLevel(), player, player, at(helper, 4.5, 4.5), 1.0F);

        helper.assertTrue(pig.isOnFire() && pig.getHealth() < pig.getMaxHealth(), "pig burned");
        helper.assertFalse(player.isOnFire(), "caster isn't set alight by their own fire");
        helper.assertTrue(player.getHealth() == player.getMaxHealth(), "caster unhurt");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void waterPushesSlowsAndPutsOutFire(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 0.5, 0.5);
        Pig pig = pig(helper, 5.5, 4.5);
        pig.igniteForSeconds(5);
        Spell.WATER.cast(helper.getLevel(), player, player, at(helper, 4.5, 4.5), 1.0F);

        helper.assertFalse(pig.isOnFire(), "fire put out");
        helper.assertTrue(pig.getDeltaMovement().x > 0.5, "pushed away from the splash");
        helper.assertTrue(pig.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "slowed");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void woodRootsEnemiesAndHealsTheCaster(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 3.5, 4.5);
        player.setHealth(10.0F);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(5.5, GROUND, 4.5));
        Spell.WOOD.cast(helper.getLevel(), player, player, at(helper, 4.5, 4.5), 1.0F);

        helper.assertTrue(player.getHealth() > 10.0F, "caster healed");
        helper.assertTrue(zombie.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                && zombie.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() >= 5, "zombie rooted");
        helper.assertFalse(player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "caster isn't rooted");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void lightningChainsBetweenEnemies(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 0.5, 0.5);
        Pig first = pig(helper, 4.5, 2.5);
        Pig second = pig(helper, 4.5, 5.5);
        Pig third = pig(helper, 4.5, 8.0);
        Spell.LIGHTNING.cast(helper.getLevel(), player, player, at(helper, 4.5, 2.0), 1.0F);

        for (Pig pig : List.of(first, second, third)) {
            helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), "every pig in the chain is struck");
        }
        helper.assertTrue(first.getHealth() < third.getHealth(), "each jump is weaker");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void earthLaunchesEnemies(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 0.5, 0.5);
        Pig pig = pig(helper, 5.0, 4.5);
        Spell.EARTH.cast(helper.getLevel(), player, player, at(helper, 4.5, 4.5), 1.0F);

        helper.assertTrue(pig.getDeltaMovement().y > 0.5, "launched upward");
        helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), "and hurt");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void sneakSwitchesTheTalismanThrown(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 1.5);
        helper.assertTrue(ShikigamiControl.selectedSpell(player) == Spell.EXPLOSIVE, "starts on Explosive");
        ShikigamiControl.cycleSpell(player);
        helper.assertTrue(ShikigamiControl.selectedSpell(player) == Spell.FIRE, "then Fire");

        helper.assertTrue(ShikigamiControl.throwTalisman(player, Vec3.ZERO), "thrown");
        List<TalismanEntity> thrown = helper.getLevel().getEntitiesOfClass(TalismanEntity.class, new AABB(player.blockPosition()).inflate(4));
        helper.assertTrue(thrown.size() == 1 && thrown.get(0).getSpell() == Spell.FIRE, "a Fire talisman flies");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void thrownTalismanGoesOffOnContact(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 0.5);
        ShikigamiControl.cycleSpell(player); // Fire
        Pig pig = pig(helper, 4.5, 6.5);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, pig.position().add(0, 0.5, 0));

        helper.assertTrue(ShikigamiControl.throwTalisman(player, Vec3.ZERO), "thrown");
        helper.succeedWhen(() -> helper.assertTrue(pig.isOnFire(), "the Fire talisman set the pig alight on contact"));
    }

    @GameTest(template = "platform")
    public static void iceFreezesEnemiesAndWater(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 0.5, 0.5);
        Pig pig = pig(helper, 5.5, 4.5);
        BlockPos water = new BlockPos(4, GROUND - 1, 4);
        helper.setBlock(water, Blocks.WATER);
        Spell.ICE.cast(helper.getLevel(), player, player, at(helper, 4.5, 4.5), 1.0F);

        helper.assertTrue(pig.isFullyFrozen(), "pig frozen solid");
        helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), "and hurt");
        helper.assertBlockPresent(Blocks.FROSTED_ICE, water);
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void windBlowsEnemiesAwayAndLiftsTheCaster(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 4.5);
        Pig pig = pig(helper, 6.5, 4.5);
        Arrow arrow = helper.spawn(EntityType.ARROW, new Vec3(4.5, GROUND + 1, 6.5));
        arrow.setDeltaMovement(0, 0, -1);
        Spell.WIND.cast(helper.getLevel(), player, player, at(helper, 4.5, 4.5), 1.0F);

        helper.assertTrue(pig.getDeltaMovement().x > 1.0, "pig blown away");
        helper.assertTrue(player.getDeltaMovement().y > 0.8, "caster launched up for a wind jump");
        helper.assertTrue(player.getHealth() == player.getMaxHealth(), "without being hurt");
        helper.assertTrue(arrow.getDeltaMovement().z > 0, "a stranger's arrow is thrown back");
        helper.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void teleportTalismanMovesTheCaster(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 0.5);
        while (ShikigamiControl.selectedSpell(player) != Spell.TELEPORT) {
            ShikigamiControl.cycleSpell(player);
        }
        player.lookAt(EntityAnchorArgument.Anchor.EYES, at(helper, 4.5, 7.5));
        helper.assertTrue(ShikigamiControl.throwTalisman(player, Vec3.ZERO), "thrown");
        helper.succeedWhen(() -> {
            Vec3 rel = helper.relativeVec(player.position());
            helper.assertTrue(rel.z > 5.5, "caster should appear where the talisman landed, is at " + rel);
            helper.assertTrue(rel.y >= GROUND - 0.01, "standing on the floor, not in it");
        });
    }

    /** Talismans go where you aim: far and nearly straight (up in open air, away from the other tests). */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void talismansFlyFar(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 4.5);
        Vec3 sky = player.position().add(0, 40, 0);
        player.moveTo(sky.x, sky.y, sky.z, 0, 0);
        while (ShikigamiControl.selectedSpell(player) != Spell.WIND) {
            ShikigamiControl.cycleSpell(player);
        }
        player.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition().add(0, 0, 10));
        helper.assertTrue(ShikigamiControl.throwTalisman(player, Vec3.ZERO), "thrown");
        TalismanEntity talisman = helper.getLevel().getEntitiesOfClass(TalismanEntity.class,
                new AABB(player.blockPosition()).inflate(4)).get(0);
        Vec3 from = talisman.position();
        // Flown by hand for 12 ticks: the test world stops ticking entities that leave the area near the tests.
        for (int i = 0; i < 12; i++) {
            talisman.tick();
        }
        helper.assertTrue(talisman.isAlive(), "still flying after 12 ticks");
        Vec3 moved = talisman.position().subtract(from);
        helper.assertTrue(moved.z > 32, "flew far, " + moved.z + " blocks");
        helper.assertTrue(moved.y > -0.5, "and nearly straight, dropped " + -moved.y);
        helper.assertTrue(talisman.getDeltaMovement().z > 2.5, "still fast: " + talisman.getDeltaMovement().z);
        helper.succeed();
    }

    /** The Teleport talisman takes you where it lands, even 150 blocks away. */
    @GameTest(template = "platform")
    public static void teleportTalismanGoesFar(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 4.5, 4.5);
        Vec3 start = player.position();
        Vec3 far = start.add(150, 40, 0);
        TalismanEntity talisman = TalismanEntity.create(player, new Paper.Talisman(new ItemStack(Items.PAPER), 1.0F), Spell.TELEPORT);
        talisman.setPos(far.x, far.y, far.z);
        Spell.TELEPORT.cast(helper.getLevel(), talisman, player, far, 1.0F);
        helper.assertTrue(player.position().distanceTo(start) > 140, "teleported " + player.position().distanceTo(start) + " blocks");
        helper.succeed();
    }
}
