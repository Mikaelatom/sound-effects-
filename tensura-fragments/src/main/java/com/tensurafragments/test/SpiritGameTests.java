package com.tensurafragments.test;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.rainbow.RainbowMagic;
import com.tensurafragments.skill.ModSkills;
import com.tensurafragments.spirit.SpiritControl;
import com.tensurafragments.spirit.SpiritEntity;
import com.tensurafragments.spirit.SpiritKind;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.entity.projectile.TensuraFlyingProjectile;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-world tests for Spirit Control. */
@GameTestHolder(TensuraFragments.MODID)
@PrefixGameTestTemplate(false)
public final class SpiritGameTests {
    private static final int GROUND = TestPlayers.GROUND;

    private SpiritGameTests() {
    }

    private static ServerPlayer caller(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.spawn(helper, 4.5, 0.5);
        TestPlayers.giveMagicules(player, 100_000);
        SkillHelper.learnSkill(player, ModSkills.SPIRIT_CONTROL.get());
        return player;
    }

    private static Husk dummy(GameTestHelper helper, double x, double z) {
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(x, GROUND, z));
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        husk.setHealth(1000);
        return husk;
    }

    private static List<SpiritEntity> spirits(GameTestHelper helper, ServerPlayer player) {
        return helper.getLevel().getEntitiesOfClass(SpiritEntity.class, new AABB(player.blockPosition()).inflate(4, 3, 8));
    }

    /** Each spirit, called on a husk, hurts it with its own attack and then vanishes. */
    private static void spiritStrikes(GameTestHelper helper, SpiritKind kind) {
        ServerPlayer player = caller(helper);
        Husk husk = dummy(helper, 4.5, 5.5);
        player.setData(ModRegistries.SPIRIT_INDEX, kind.ordinal());
        helper.assertTrue(SpiritControl.call(player, husk, husk.getBoundingBox().getCenter()), "call " + kind);
        helper.assertTrue(spirits(helper, player).size() == 1 && spirits(helper, player).get(0).getKind() == kind,
                kind + " appeared");
        helper.runAfterDelay(kind.lifetime() + 20, () -> {
            helper.assertTrue(husk.getHealth() < 1000, kind + " hurt the husk");
            helper.assertTrue(spirits(helper, player).isEmpty(), kind + " vanished after its one attack");
            helper.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void ifritStrikes(GameTestHelper helper) {
        spiritStrikes(helper, SpiritKind.IFRIT);
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void sylphideStrikes(GameTestHelper helper) {
        spiritStrikes(helper, SpiritKind.SYLPHIDE);
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void undineStrikes(GameTestHelper helper) {
        spiritStrikes(helper, SpiritKind.UNDINE);
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void warGnomeStrikes(GameTestHelper helper) {
        spiritStrikes(helper, SpiritKind.WAR_GNOME);
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void bladeTigerStrikes(GameTestHelper helper) {
        spiritStrikes(helper, SpiritKind.BLADE_TIGER);
    }

    /** Each attack brings the next spirit in line, and they can't be spammed faster than the interval. */
    @GameTest(template = "platform", timeoutTicks = 120)
    public static void spiritsComeInTurn(GameTestHelper helper) {
        ServerPlayer player = caller(helper);
        Husk husk = dummy(helper, 4.5, 6.5);
        helper.assertTrue(SpiritControl.onMeleeHit(player, husk), "a melee hit calls a spirit");
        helper.assertFalse(SpiritControl.onMeleeHit(player, husk), "not again straight away");
        helper.assertTrue(SpiritControl.nextKind(player) == SpiritKind.SYLPHIDE, "Sylphide is next after Ifrit");
        for (int i = 1; i < SpiritKind.values().length; i++) {
            SpiritKind expected = SpiritKind.values()[i];
            helper.runAfterDelay(12L * i, () -> {
                helper.assertTrue(SpiritControl.nextKind(player) == expected, "next is " + expected);
                helper.assertTrue(SpiritControl.onMeleeHit(player, husk), "called " + expected);
            });
        }
        helper.runAfterDelay(12L * SpiritKind.values().length, () -> {
            helper.assertTrue(SpiritControl.nextKind(player) == SpiritKind.IFRIT, "back round to Ifrit");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void spiritLinkOffStopsMeleeSpirits(GameTestHelper helper) {
        ServerPlayer player = caller(helper);
        Husk husk = dummy(helper, 4.5, 4.5);
        SpiritControl.toggleLink(player);
        helper.assertFalse(SpiritControl.onMeleeHit(player, husk), "link off: no spirit on hits");
        helper.assertTrue(spirits(helper, player).isEmpty(), "none appeared");
        helper.succeed();
    }

    /** Rainbow Magic's spirits: recoloured in rainbow, and their attack carries every element on top. */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void rainbowSpiritStrikesWithEveryElement(GameTestHelper helper) {
        ServerPlayer player = caller(helper);
        SkillHelper.learnSkill(player, ModSkills.RAINBOW_MAGIC.get());
        Husk husk = dummy(helper, 4.5, 5.5);
        player.setData(ModRegistries.RAINBOW_SPIRIT_INDEX, SpiritKind.WAR_GNOME.ordinal());
        helper.assertTrue(SpiritControl.callRainbow(player, husk, husk.getBoundingBox().getCenter()), "rainbow spirit called");
        List<SpiritEntity> called = spirits(helper, player);
        helper.assertTrue(called.size() == 1 && RainbowMagic.isRainbow(called.get(0)), "the spirit is a rainbow spirit");
        helper.assertTrue(SpiritControl.nextRainbowKind(player) == SpiritKind.BLADE_TIGER, "rainbow spirits take turns");
        helper.assertTrue(SpiritControl.nextKind(player) == SpiritKind.IFRIT, "without touching Spirit Control's turn");
        float plain = Config.SPIRIT_DAMAGE.get().floatValue() * SpiritKind.WAR_GNOME.damageMultiplier();
        helper.runAfterDelay(SpiritKind.WAR_GNOME.lifetime() + 5, () -> {
            helper.assertTrue(husk.getHealth() < 1000 - plain - RainbowMagic.prismDamage() + 0.5F,
                    "stomp plus every element, husk at " + husk.getHealth());
            helper.assertTrue(husk.isOnFire(), "the fire element burns");
            helper.succeed();
        });
    }

    /** A rainbow Sylphide's wind blade is a rainbow spell too. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void rainbowSpiritMagicIsRainbow(GameTestHelper helper) {
        ServerPlayer player = caller(helper);
        Husk husk = dummy(helper, 4.5, 7.5);
        player.setData(ModRegistries.RAINBOW_SPIRIT_INDEX, SpiritKind.SYLPHIDE.ordinal());
        SpiritControl.callRainbow(player, husk, husk.getBoundingBox().getCenter());
        helper.runAfterDelay(SpiritKind.SYLPHIDE.strikeTick() + 1, () -> {
            List<TensuraFlyingProjectile> blades = helper.getLevel().getEntitiesOfClass(TensuraFlyingProjectile.class,
                    new AABB(player.blockPosition()).inflate(4, 3, 8));
            helper.assertTrue(!blades.isEmpty() && blades.stream().allMatch(RainbowMagic::isRainbow), "a rainbow wind blade");
            helper.succeed();
        });
    }

    /** The War Gnome's stomp throws enemies but spares your pets. */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void spiritsSpareAllies(GameTestHelper helper) {
        ServerPlayer player = caller(helper);
        Husk husk = dummy(helper, 4.5, 5.5);
        Wolf wolf = helper.spawnWithNoFreeWill(EntityType.WOLF, new Vec3(5.5, GROUND, 5.5));
        wolf.tame(player);
        float wolfHealth = wolf.getHealth();
        player.setData(ModRegistries.SPIRIT_INDEX, SpiritKind.WAR_GNOME.ordinal());
        SpiritControl.call(player, husk, husk.getBoundingBox().getCenter());
        helper.runAfterDelay(SpiritKind.WAR_GNOME.lifetime() + 5, () -> {
            helper.assertTrue(husk.getHealth() < 1000, "the husk was stomped");
            helper.assertTrue(wolf.getHealth() == wolfHealth, "your wolf wasn't");
            helper.succeed();
        });
    }
}
