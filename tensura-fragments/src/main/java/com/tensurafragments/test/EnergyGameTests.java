package com.tensurafragments.test;

import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.energy.EnergyMagic;
import com.tensurafragments.energy.EnergySpell;
import com.tensurafragments.skill.ModSkills;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.entity.projectile.TensuraFlyingProjectile;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Energy Magic: Tensura's spells at twice the power, paid for with experience levels. */
@GameTestHolder(TensuraFragments.MODID)
@PrefixGameTestTemplate(false)
public final class EnergyGameTests {
    private EnergyGameTests() {
    }

    private static ServerPlayer caster(GameTestHelper helper, int levels) {
        ServerPlayer player = TestPlayers.spawn(helper, 4.5, 0.5);
        SkillHelper.learnSkill(player, ModSkills.ENERGY_MAGIC.get());
        player.setExperienceLevels(levels);
        return player;
    }

    @GameTest(template = "platform")
    public static void energySpellCostsLevelsAndHitsTwiceAsHard(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 10);
        player.setData(ModRegistries.ENERGY_SPELL, EnergySpell.FIRE_LANCE.ordinal());
        helper.assertTrue(EnergyMagic.cast(player), "cast");
        helper.assertTrue(player.experienceLevel == 10 - EnergyMagic.levelCost(EnergySpell.FIRE_LANCE),
                "paid in levels, now at " + player.experienceLevel);
        List<TensuraFlyingProjectile> cast = helper.getLevel().getEntitiesOfClass(TensuraFlyingProjectile.class,
                new AABB(player.blockPosition()).inflate(4));
        helper.assertTrue(cast.size() == 1, "one spell flies");
        TensuraFlyingProjectile lance = cast.get(0);
        helper.assertTrue(EnergyMagic.isEnergy(lance), "it's an energy spell (drawn glowing green)");
        helper.assertTrue(lance.getDamage() == EnergySpell.FIRE_LANCE.baseDamage() * 2, "twice the normal damage: " + lance.getDamage());
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void energyNeedsEnoughLevels(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 2);
        player.setData(ModRegistries.ENERGY_SPELL, EnergySpell.PLASMA_BALL.ordinal());
        helper.assertFalse(EnergyMagic.cast(player), "Plasma Ball needs 5 levels");
        helper.assertTrue(player.experienceLevel == 2, "nothing spent");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(TensuraFlyingProjectile.class,
                new AABB(player.blockPosition()).inflate(4)).isEmpty(), "nothing cast");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void energySpellsSwitch(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 0);
        helper.assertTrue(EnergyMagic.selectedSpell(player) == EnergySpell.FIRE_BOLT, "starts on Fire Bolt");
        for (int i = 1; i < EnergySpell.values().length; i++) {
            EnergyMagic.cycleSpell(player);
            helper.assertTrue(EnergyMagic.selectedSpell(player) == EnergySpell.values()[i], "then " + EnergySpell.values()[i]);
        }
        EnergyMagic.cycleSpell(player);
        helper.assertTrue(EnergyMagic.selectedSpell(player) == EnergySpell.FIRE_BOLT, "and round again");
        helper.succeed();
    }

    /** Every energy spell can actually be cast. */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void everyEnergySpellCasts(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 1000);
        for (EnergySpell spell : EnergySpell.values()) {
            player.setData(ModRegistries.ENERGY_SPELL, spell.ordinal());
            helper.assertTrue(EnergyMagic.cast(player), spell + " cast");
        }
        helper.runAfterDelay(60, helper::succeed);
    }
}
