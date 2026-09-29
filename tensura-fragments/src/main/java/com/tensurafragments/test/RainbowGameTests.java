package com.tensurafragments.test;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.rainbow.RainbowMagic;
import com.tensurafragments.rainbow.RainbowSpell;
import com.tensurafragments.skill.ModSkills;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.entity.projectile.TensuraFlyingProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.FireBallProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.WaterBladeProjectile;
import java.util.List;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-world tests for Rainbow Magic (Tensura's own spells, in rainbow). Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(TensuraFragments.MODID)
@PrefixGameTestTemplate(false)
public final class RainbowGameTests {
    private static final int GROUND = TestPlayers.GROUND;

    private RainbowGameTests() {
    }

    private static ServerPlayer mage(GameTestHelper helper, double x, double z) {
        ServerPlayer player = TestPlayers.spawn(helper, x, z);
        TestPlayers.giveMagicules(player, 100_000);
        SkillHelper.learnSkill(player, ModSkills.RAINBOW_MAGIC.get()); // no Shikigami Control needed
        return player;
    }

    private static void select(ServerPlayer player, RainbowSpell spell) {
        while (RainbowMagic.selectedSpell(player) != spell) {
            RainbowMagic.cycleSpell(player);
        }
    }

    private static Husk target(GameTestHelper helper, ServerPlayer player, double x, double z) {
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(x, GROUND, z));
        player.lookAt(EntityAnchorArgument.Anchor.EYES, husk.position().add(0, husk.getBbHeight() / 2, 0));
        return husk;
    }

    @GameTest(template = "platform")
    public static void castsTensurasOwnSpellMarkedRainbow(GameTestHelper helper) {
        ServerPlayer player = mage(helper, 4.5, 1.5);
        helper.assertTrue(RainbowMagic.selectedSpell(player) == RainbowSpell.FIRE_BALL, "starts on Fire Ball");
        helper.assertTrue(RainbowMagic.cast(player), "cast");

        List<FireBallProjectile> cast = helper.getLevel().getEntitiesOfClass(FireBallProjectile.class,
                new AABB(player.blockPosition()).inflate(5));
        helper.assertTrue(cast.size() == 1, "one Tensura Fire Ball in the world, found " + cast.size());
        helper.assertTrue(RainbowMagic.isRainbow(cast.get(0)), "marked as a rainbow spell");
        helper.assertTrue(cast.get(0).getOwner() == player, "cast by the player");
        helper.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void rainbowHitStrikesWithEveryElement(GameTestHelper helper) {
        ServerPlayer player = mage(helper, 4.5, 0.5);
        select(player, RainbowSpell.WATER_BLADE); // water on its own would never burn or freeze
        Husk husk = target(helper, player, 4.5, 5.5);
        helper.assertTrue(RainbowMagic.cast(player), "cast");

        helper.succeedWhen(() -> {
            helper.assertTrue(husk.isDeadOrDying() || husk.getHealth() < husk.getMaxHealth(), "hit");
            helper.assertTrue(husk.isOnFire(), "burning");
            helper.assertTrue(husk.getTicksFrozen() > 0, "freezing");
            helper.assertTrue(husk.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "slowed");
        });
    }

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void plainTensuraSpellsAreNotAffected(GameTestHelper helper) {
        ServerPlayer player = mage(helper, 4.5, 0.5);
        Husk husk = target(helper, player, 4.5, 5.5);
        // Tensura's own Water Blade, not cast through Rainbow Magic.
        TensuraFlyingProjectile blade = new WaterBladeProjectile(helper.getLevel(), player);
        blade.setDamage(6.0F);
        blade.setSpeed(2.0F);
        blade.setPosAndShoot(player);
        helper.getLevel().addFreshEntity(blade);

        helper.succeedWhen(() -> {
            helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "hit");
            helper.assertFalse(husk.getTicksFrozen() > 0 || husk.isOnFire(), "no rainbow elements on a normal spell");
        });
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void everyRainbowSpellWorks(GameTestHelper helper) {
        ServerPlayer player = mage(helper, 4.5, 0.5);
        for (RainbowSpell spell : RainbowSpell.values()) {
            select(player, spell);
            player.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(4.5, GROUND, 8.5)));
            helper.assertTrue(RainbowMagic.cast(player), spell + " cast");
        }
        List<TensuraFlyingProjectile> cast = helper.getLevel().getEntitiesOfClass(TensuraFlyingProjectile.class,
                new AABB(player.blockPosition()).inflate(6), RainbowMagic::isRainbow);
        helper.assertTrue(cast.size() == RainbowSpell.values().length, "all six spells in flight, found " + cast.size());
        // Let them all fly and land without anything going wrong.
        helper.runAfterDelay(60, helper::succeed);
    }
}
