package com.tensurafragments.test;

import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.grimoire.Binding;
import com.tensurafragments.grimoire.GrimoireContents;
import com.tensurafragments.grimoire.SealingGrimoire;
import com.tensurafragments.grimoire.SealingGrimoireItem;
import com.tensurafragments.skill.ModSkills;
import io.github.manasmods.tensura.ability.SkillHelper;
import java.util.List;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-world tests for Sealing Grimoire. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(TensuraFragments.MODID)
@PrefixGameTestTemplate(false)
public final class GrimoireGameTests {
    private static final int GROUND = TestPlayers.GROUND;

    private GrimoireGameTests() {
    }

    /** A survival player with a book, paper, plenty of magicules and the skill. */
    private static ServerPlayer sealer(GameTestHelper helper, double x, double z, int paper) {
        ServerPlayer player = TestPlayers.spawn(helper, x, z);
        TestPlayers.giveMagicules(player, 100_000);
        player.getInventory().add(new ItemStack(Items.BOOK));
        if (paper > 0) {
            player.getInventory().add(new ItemStack(Items.PAPER, paper));
        }
        SkillHelper.learnSkill(player, ModSkills.SEALING_GRIMOIRE.get());
        return player;
    }

    private static GrimoireContents contents(ServerPlayer player) {
        ItemStack grimoire = SealingGrimoire.grimoire(player);
        return grimoire == null ? GrimoireContents.EMPTY : SealingGrimoireItem.contents(grimoire);
    }

    private static void lookAt(ServerPlayer player, LivingEntity target) {
        player.lookAt(EntityAnchorArgument.Anchor.EYES, target.position().add(0, target.getBbHeight() / 2, 0));
    }

    @GameTest(template = "platform")
    public static void firstUseTurnsABookIntoTheGrimoire(GameTestHelper helper) {
        ServerPlayer player = sealer(helper, 4.5, 4.5, 0);
        helper.assertTrue(SealingGrimoire.grimoire(player) != null, "grimoire made from the book");
        helper.assertTrue(player.getInventory().countItem(Items.BOOK) == 0, "the book was used");
        helper.assertTrue(player.getInventory().countItem(ModRegistries.SEALING_GRIMOIRE.get()) == 1, "exactly one grimoire");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void healthyCreatureResistsAndBurnsTheTalisman(GameTestHelper helper) {
        ServerPlayer player = sealer(helper, 4.5, 1.5, 1);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(4.5, GROUND, 5.5));
        lookAt(player, pig);

        helper.assertFalse(SealingGrimoire.sealCreature(player), "a healthy pig can't be sealed");
        helper.assertTrue(pig.isAlive(), "pig still there");
        helper.assertTrue(player.getInventory().countItem(Items.PAPER) == 0, "failed seal burns the paper");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void weakenedCreatureIsSealed(GameTestHelper helper) {
        ServerPlayer player = sealer(helper, 4.5, 1.5, 1);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(4.5, GROUND, 5.5));
        pig.setHealth(2.0F);
        lookAt(player, pig);

        helper.assertTrue(SealingGrimoire.sealCreature(player), "a weakened pig is sealed");
        helper.assertTrue(pig.isRemoved(), "pig is in the book now");
        List<GrimoireContents.Page> pages = contents(player).pages();
        helper.assertTrue(pages.size() == 1 && pages.get(0).kind() == GrimoireContents.Kind.CREATURE, "one creature page");
        helper.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void releasedCreatureServesThenReturns(GameTestHelper helper) {
        ServerPlayer player = sealer(helper, 4.5, 1.5, 1);
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(4.5, GROUND, 5.5));
        husk.setHealth(2.0F);
        lookAt(player, husk);
        helper.assertTrue(SealingGrimoire.sealCreature(player), "husk sealed");

        helper.assertTrue(SealingGrimoire.release(player), "husk released");
        helper.assertTrue(contents(player).isEmpty(), "page used");
        List<Husk> released = helper.getLevel().getEntitiesOfClass(Husk.class, new AABB(player.blockPosition()).inflate(8));
        helper.assertTrue(released.size() == 1, "one husk out, found " + released.size());
        Husk servant = released.get(0);
        helper.assertTrue(Binding.isBoundTo(servant, player), "bound to the caster");
        helper.assertTrue(servant.getHealth() == servant.getMaxHealth(), "healed inside the book");
        servant.setTarget(player);
        helper.assertTrue(servant.getTarget() == null, "never turns on its binder");

        // Cut its time short: it should go back into the book by itself.
        Binding.bind(servant, player, helper.getLevel().getGameTime() + 5);
        helper.succeedWhen(() -> {
            helper.assertTrue(servant.isRemoved(), "back in the book");
            helper.assertTrue(contents(player).pages().size() == 1, "page restored");
        });
    }

    /** A released Tensura zombie (a brain-driven mob) never goes for you, and attacks what you punch. */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void releasedBrainMobFightsForYou(GameTestHelper helper) {
        ServerPlayer player = sealer(helper, 1.5, 1.5, 1);
        net.minecraft.world.entity.Mob zombie = SoulGameTests.tensuraZombie(helper, 4.5, 4.5);
        zombie.setHealth(1.0F);
        lookAt(player, zombie);
        helper.assertTrue(SealingGrimoire.sealCreature(player), "sealed");
        helper.assertTrue(SealingGrimoire.release(player), "released");
        List<net.minecraft.world.entity.Mob> out = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                new AABB(player.blockPosition()).inflate(8), m -> Binding.isBoundTo(m, player));
        helper.assertTrue(out.size() == 1, "one creature out");
        SoulGameTests.fightsForYou(helper, player, out.get(0));
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void catchesIncomingMagicAndFiresItBack(GameTestHelper helper) {
        ServerPlayer player = sealer(helper, 4.5, 1.5, 1);
        helper.onEachTick(() -> SealingGrimoire.tickCatch(player)); // fake players don't tick themselves
        helper.assertTrue(SealingGrimoire.readyCatch(player), "book opened");
        Arrow arrow = helper.spawn(EntityType.ARROW, new Vec3(4.5, GROUND + 1.5, 6.5));
        arrow.setDeltaMovement(0, 0, -0.8);

        helper.succeedWhen(() -> {
            helper.assertTrue(arrow.isRemoved(), "arrow caught");
            List<GrimoireContents.Page> pages = contents(player).pages();
            helper.assertTrue(pages.size() == 1 && pages.get(0).kind() == GrimoireContents.Kind.MAGIC, "one magic page");
            helper.assertTrue(player.getInventory().countItem(Items.PAPER) == 0, "catch used the paper");

            player.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(4.5, GROUND + 1.5, 8.5)));
            helper.assertTrue(SealingGrimoire.release(player), "fired back");
            List<Arrow> fired = helper.getLevel().getEntitiesOfClass(Arrow.class, new AABB(player.blockPosition()).inflate(4));
            helper.assertTrue(fired.size() == 1 && fired.get(0).getOwner() == player, "the arrow is now the caster's");
        });
    }

    @GameTest(template = "platform")
    public static void bossesAreUnsealable(GameTestHelper helper) {
        helper.assertTrue(EntityType.WITHER.is(SealingGrimoire.UNSEALABLE), "wither");
        helper.assertTrue(EntityType.ENDER_DRAGON.is(SealingGrimoire.UNSEALABLE), "ender dragon");
        helper.assertFalse(EntityType.ZOMBIE.is(SealingGrimoire.UNSEALABLE), "zombies are fair game");
        helper.succeed();
    }
}
