package com.tensurafragments.test;

import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.flame.FireSpell;
import com.tensurafragments.flame.FlameEmperor;
import com.tensurafragments.flame.HellStormEntity;
import com.tensurafragments.skill.ModSkills;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.entity.projectile.TensuraFlyingProjectile;
import java.util.List;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-world tests for Flame Emperor. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(TensuraFragments.MODID)
@PrefixGameTestTemplate(false)
public final class FlameGameTests {
    private static final int GROUND = TestPlayers.GROUND;

    private FlameGameTests() {
    }

    private static ServerPlayer emperor(GameTestHelper helper, double x, double z) {
        ServerPlayer player = TestPlayers.spawn(helper, x, z);
        TestPlayers.giveMagicules(player, 1_000_000);
        SkillHelper.learnSkill(player, ModSkills.FLAME_EMPEROR.get());
        return player;
    }

    @GameTest(template = "platform", timeoutTicks = 120)
    public static void everyFireSpellCasts(GameTestHelper helper) {
        ServerPlayer player = emperor(helper, 4.5, 0.5);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(4.5, GROUND + 3, 8.5)));
        for (FireSpell ignored : FireSpell.values()) {
            helper.assertTrue(FlameEmperor.castFire(player), FlameEmperor.selectedSpell(player) + " cast");
            FlameEmperor.cycleSpell(player);
        }
        List<TensuraFlyingProjectile> cast = helper.getLevel().getEntitiesOfClass(TensuraFlyingProjectile.class,
                new AABB(player.blockPosition()).inflate(6));
        helper.assertTrue(cast.size() == FireSpell.values().length, "all ten fire spells in flight, found " + cast.size());
        // Let them all fly and land without anything going wrong.
        helper.runAfterDelay(100, helper::succeed);
    }

    @GameTest(template = "platform", timeoutTicks = 140)
    public static void hellStormBurnsWhatItTouchesForever(GameTestHelper helper) {
        ServerPlayer player = emperor(helper, 4.5, 0.5);
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(4.5, GROUND, 6.5));
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        husk.setHealth(1000);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, husk.position().add(0, 1, 0));
        helper.assertTrue(FlameEmperor.hellStorm(player), "hell storm cast");

        helper.runAfterDelay(15, () -> helper.assertTrue(helper.getLevel().getEntitiesOfClass(HellStormEntity.class,
                new AABB(player.blockPosition()).inflate(4)).isEmpty(), "the circle charges before the storm comes"));
        helper.runAfterDelay(45, () -> {
            helper.assertFalse(helper.getLevel().getEntitiesOfClass(HellStormEntity.class,
                    new AABB(player.blockPosition()).inflate(4)).isEmpty(), "storm pouring out");
            helper.assertTrue(husk.getHealth() < 1000 - 100, "huge fire damage, husk at " + husk.getHealth());
            helper.assertTrue(husk.hasEffect(ModRegistries.DRACONIC_HELLFIRE), "Draconic Hellfire applied");
            helper.assertTrue(player.getHealth() == player.getMaxHealth(), "the caster isn't burned");
        });
        helper.runAfterDelay(130, () -> {
            helper.assertTrue(husk.hasEffect(ModRegistries.DRACONIC_HELLFIRE) && husk.isOnFire(),
                    "still burning long after the storm ended");
            helper.succeed();
        });
    }

    private static Husk dummy(GameTestHelper helper, double x, double z) {
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(x, GROUND, z));
        husk.setNoGravity(true);
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        husk.setHealth(1000);
        return husk;
    }

    /** The storm burns the cone the mist is drawn as: thin at the hand, wide far out, nothing behind or beside you. */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void hellStormHitsOnlyTheMistInFront(GameTestHelper helper) {
        ServerPlayer player = emperor(helper, 4.5, 0.5);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition().add(0, 0, 30));
        Husk ahead = dummy(helper, 4.5, 6.5);
        Husk behind = dummy(helper, 4.5, -2.5);
        Husk besideTheHand = dummy(helper, 7.0, 3.0);
        Husk wideFarOut = dummy(helper, 7.0, 8.0);
        helper.assertTrue(FlameEmperor.hellStorm(player), "hell storm cast");

        helper.runAfterDelay(50, () -> {
            HellStormEntity storm = helper.getLevel().getEntitiesOfClass(HellStormEntity.class,
                    new AABB(player.blockPosition()).inflate(4)).stream().findFirst().orElse(null);
            helper.assertTrue(storm != null, "storm pouring out");
            helper.assertTrue(storm.position().subtract(player.getEyePosition()).dot(player.getLookAngle()) > 0.5,
                    "the storm starts in front of the caster");
            // The next test's structure is a wall about 7 blocks out; the storm stops there and keeps its cone shape.
            helper.assertTrue(storm.getLength() > 6.5, "the storm reaches out, length " + storm.getLength());
            helper.assertTrue(ahead.getHealth() < 1000, "straight ahead burns");
            helper.assertTrue(wideFarOut.getHealth() < 1000, "the wide far end of the mist burns");
            helper.assertTrue(behind.getHealth() == 1000 && !behind.hasEffect(ModRegistries.DRACONIC_HELLFIRE),
                    "nothing behind the caster burns");
            helper.assertTrue(besideTheHand.getHealth() == 1000 && !besideTheHand.hasEffect(ModRegistries.DRACONIC_HELLFIRE),
                    "the mist is thin at the hand, so beside it is safe");
            helper.succeed();
        });
    }

    /** Hellfire is its own damage: fire-immune mobs with Fire Resistance, in armour, still burn. */
    @GameTest(template = "platform", timeoutTicks = 160)
    public static void hellStormBurnsTheFireproof(GameTestHelper helper) {
        ServerPlayer player = emperor(helper, 4.5, 0.5);
        Blaze blaze = helper.spawnWithNoFreeWill(EntityType.BLAZE, new Vec3(4.5, GROUND, 5.5));
        blaze.setNoGravity(true);
        blaze.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        blaze.setHealth(1000);
        blaze.getAttribute(Attributes.ARMOR).setBaseValue(30);
        blaze.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION));
        blaze.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 3));
        player.lookAt(EntityAnchorArgument.Anchor.EYES, blaze.position().add(0, 1, 0));
        helper.assertTrue(FlameEmperor.hellStorm(player), "hell storm cast");
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(blaze.getHealth() <= 1000 - 100, "fireproof, armoured blaze still burns, at " + blaze.getHealth());
            helper.assertTrue(blaze.hasEffect(ModRegistries.DRACONIC_HELLFIRE), "Draconic Hellfire applied");
        });
        float[] afterStorm = new float[1];
        helper.runAfterDelay(100, () -> afterStorm[0] = blaze.getHealth());
        helper.runAfterDelay(145, () -> {
            helper.assertTrue(blaze.hasEffect(ModRegistries.DRACONIC_HELLFIRE), "the burn doesn't end");
            helper.assertTrue(blaze.getHealth() < afterStorm[0], "and keeps hurting the fireproof blaze after the storm");
            helper.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void hellfireCannotBeCured(GameTestHelper helper) {
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(4.5, GROUND, 4.5));
        husk.addEffect(new MobEffectInstance(ModRegistries.DRACONIC_HELLFIRE, MobEffectInstance.INFINITE_DURATION));
        husk.removeEffectsCuredBy(EffectCures.MILK);
        husk.removeEffectsCuredBy(EffectCures.PROTECTED_BY_TOTEM);
        husk.clearFire();
        helper.assertTrue(husk.hasEffect(ModRegistries.DRACONIC_HELLFIRE), "milk and totems don't cure it");
        float before = husk.getHealth();
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(husk.isOnFire(), "relit straight away");
            helper.assertTrue(husk.getHealth() < before, "and it keeps hurting");
            helper.succeed();
        });
    }
}
