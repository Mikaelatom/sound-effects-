package com.tensurafragments.test;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.shikigami.ShikigamiControl;
import com.tensurafragments.shikigami.Spell;
import com.tensurafragments.shikigami.TalismanEntity;
import com.tensurafragments.skill.ModSkills;
import io.github.manasmods.tensura.ability.SkillHelper;
import java.util.List;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
}
