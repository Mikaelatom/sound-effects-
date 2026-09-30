package com.tensurafragments.test;

import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.skill.ModSkills;
import com.tensurafragments.yifa.FireWhirlEntity;
import com.tensurafragments.yifa.SpiritCommunion;
import com.tensurafragments.yifa.SpiritElement;
import com.tensurafragments.yifa.WispEntity;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.entity.projectile.TensuraFlyingProjectile;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-world tests for Spirit Communion (Yifa) and the Magisteel Spirit Bell and Lantern. */
@GameTestHolder(TensuraFragments.MODID)
@PrefixGameTestTemplate(false)
public final class YifaGameTests {
    private static final int GROUND = TestPlayers.GROUND;

    private YifaGameTests() {
    }

    private static ServerPlayer yifa(GameTestHelper helper, double x, double z) {
        ServerPlayer player = TestPlayers.spawn(helper, x, z);
        TestPlayers.giveMagicules(player, 100_000);
        SkillHelper.learnSkill(player, ModSkills.SPIRIT_COMMUNION.get());
        return player;
    }

    private static WispEntity wisp(GameTestHelper helper, SpiritElement element, double x, double y, double z) {
        WispEntity wisp = WispEntity.wild(helper.getLevel(), element, helper.absoluteVec(new Vec3(x, y, z)));
        helper.getLevel().addFreshEntity(wisp);
        return wisp;
    }

    /** Spirits bound to the player, then released at a husk in front of them. */
    private static Husk castAtHusk(GameTestHelper helper, ServerPlayer player, SpiritElement... elements) {
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(4.5, GROUND, 5.5));
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        husk.setHealth(1000);
        SpiritCommunion.setSight(player, true);
        player.setShiftKeyDown(true);
        for (SpiritElement element : elements) {
            wisp(helper, element, 4.5, GROUND + 1, 2.5);
        }
        helper.assertTrue(SpiritCommunion.call(player), "spirits called");
        player.setShiftKeyDown(false);
        helper.assertTrue(SpiritCommunion.bound(player).size() == elements.length, "all bound");
        player.lookAt(EntityAnchorArgument.Anchor.EYES, husk.position().add(0, 1, 0));
        helper.assertTrue(SpiritCommunion.release(player), "released");
        helper.assertTrue(SpiritCommunion.bound(player).isEmpty(), "spent");
        return husk;
    }

    @GameTest(template = "platform")
    public static void spiritsGatherByTheirElement(GameTestHelper helper) {
        BlockPos near = helper.absolutePos(new BlockPos(2, GROUND + 1, 2));
        helper.setBlock(new BlockPos(1, GROUND, 1), Blocks.CAMPFIRE);
        helper.assertTrue(SpiritCommunion.elementAt(helper.getLevel(), near, helper.getLevel().random) == SpiritElement.FIRE,
                "fire spirits by a campfire");
        BlockPos wet = helper.absolutePos(new BlockPos(7, GROUND + 1, 7));
        helper.setBlock(new BlockPos(7, GROUND, 7), Blocks.WATER);
        helper.assertTrue(SpiritCommunion.elementAt(helper.getLevel(), wet, helper.getLevel().random) == SpiritElement.WATER,
                "water spirits by water");
        BlockPos high = helper.absolutePos(new BlockPos(4, GROUND + 1, 4)).atY(140);
        helper.assertTrue(SpiritCommunion.elementAt(helper.getLevel(), high, helper.getLevel().random) == SpiritElement.WIND,
                "wind spirits high up");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void callNeedsSightAndBindsWhatYouLookAt(GameTestHelper helper) {
        ServerPlayer player = yifa(helper, 4.5, 0.5);
        WispEntity ahead = wisp(helper, SpiritElement.FIRE, 4.5, GROUND + 1.6, 5.5);
        WispEntity aside = wisp(helper, SpiritElement.WIND, 8.5, GROUND + 1.6, 1.5);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, ahead.position());
        helper.assertFalse(SpiritCommunion.call(player), "can't call what you can't see");
        SpiritCommunion.setSight(player, true);
        helper.assertTrue(SpiritCommunion.call(player), "called");
        helper.assertTrue(ahead.isBoundTo(player) && aside.isWild(), "bound the one looked at, not the other");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void sneakCallGathersUpToTheLimit(GameTestHelper helper) {
        ServerPlayer player = yifa(helper, 4.5, 4.5);
        SpiritCommunion.setSight(player, true);
        for (int i = 0; i < 7; i++) {
            wisp(helper, SpiritElement.WIND, 1.5 + i, GROUND + 1, 6.5);
        }
        player.setShiftKeyDown(true);
        helper.assertTrue(SpiritCommunion.call(player), "gathered");
        helper.assertTrue(SpiritCommunion.bound(player).size() == 5, "five at most, got " + SpiritCommunion.bound(player).size());
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void fireSpiritsBurst(GameTestHelper helper) {
        ServerPlayer player = yifa(helper, 4.5, 0.5);
        Husk husk = castAtHusk(helper, player, SpiritElement.FIRE, SpiritElement.FIRE);
        helper.assertTrue(husk.getHealth() <= 1000 - 9 && husk.isOnFire(), "burned, husk at " + husk.getHealth());
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void windSpiritsGale(GameTestHelper helper) {
        ServerPlayer player = yifa(helper, 4.5, 0.5);
        Husk husk = castAtHusk(helper, player, SpiritElement.WIND, SpiritElement.WIND);
        helper.assertTrue(husk.getHealth() < 1000 && husk.getDeltaMovement().y > 0.3, "blown into the air");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void earthSpiritsBind(GameTestHelper helper) {
        ServerPlayer player = yifa(helper, 4.5, 0.5);
        Husk husk = castAtHusk(helper, player, SpiritElement.EARTH);
        helper.assertTrue(husk.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "held in place");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void waterSpiritsHeal(GameTestHelper helper) {
        ServerPlayer player = yifa(helper, 4.5, 0.5);
        player.setHealth(6);
        castAtHusk(helper, player, SpiritElement.WATER, SpiritElement.WATER);
        helper.assertTrue(player.getHealth() >= 14, "healed, at " + player.getHealth());
        helper.succeed();
    }

    /** Fire and wind together: a whirl that travels forward and burns what it passes. */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void fireAndWindMakeAWhirl(GameTestHelper helper) {
        ServerPlayer player = yifa(helper, 4.5, 0.5);
        Husk husk = castAtHusk(helper, player, SpiritElement.FIRE, SpiritElement.WIND, SpiritElement.WIND);
        List<FireWhirlEntity> whirls = helper.getLevel().getEntitiesOfClass(FireWhirlEntity.class,
                new AABB(player.blockPosition()).inflate(6));
        helper.assertTrue(whirls.size() == 1, "a fire whirl");
        helper.assertTrue(whirls.get(0).direction().z > 0.9, "heading where you looked");
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(husk.getHealth() < 1000 && husk.isOnFire(), "the whirl burned the husk");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void jutsuWorksWithoutSpirits(GameTestHelper helper) {
        ServerPlayer player = yifa(helper, 4.5, 0.5);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition().add(0, 0, 5));
        helper.assertTrue(SpiritCommunion.jutsu(player), "fire jutsu");
        helper.assertFalse(helper.getLevel().getEntitiesOfClass(TensuraFlyingProjectile.class,
                new AABB(player.blockPosition()).inflate(3)).isEmpty(), "a fire bolt flies");
        helper.succeed();
    }

    /** The Magisteel Spirit Bell: wild spirits come flying and bind to you. */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void spiritBellCallsSpiritsToYou(GameTestHelper helper) {
        ServerPlayer player = yifa(helper, 1.5, 1.5);
        List<WispEntity> wild = new ArrayList<>();
        wild.add(wisp(helper, SpiritElement.FIRE, 7.5, GROUND + 2, 7.5));
        wild.add(wisp(helper, SpiritElement.WIND, 7.5, GROUND + 3, 1.5));
        player.getInventory().add(new ItemStack(ModRegistries.SPIRIT_BELL.get()));
        helper.assertTrue(SpiritCommunion.ringBell(player) >= 2, "the bell reached them");
        helper.succeedWhen(() -> helper.assertTrue(wild.stream().allMatch(w -> w.isAlive() && w.isBoundTo(player)),
                "both flew over and bound themselves"));
    }

    /** The Magisteel Spirit Lantern: wild spirits drift in to wait by you (without binding). */
    @GameTest(template = "platform", timeoutTicks = 120)
    public static void spiritLanternDrawsSpiritsIn(GameTestHelper helper) {
        ServerPlayer player = yifa(helper, 1.5, 1.5);
        WispEntity far = wisp(helper, SpiritElement.WATER, 8.5, GROUND + 2, 8.5);
        player.getInventory().add(new ItemStack(ModRegistries.SPIRIT_LANTERN.get()));
        helper.assertTrue(SpiritCommunion.carriesLantern(player), "carrying the lantern");
        SpiritCommunion.tick(player);
        helper.succeedWhen(() -> {
            helper.assertTrue(far.isAlive() && far.distanceTo(player) < 3.5, "drawn in, at " + far.distanceTo(player));
            helper.assertTrue(far.isWild(), "waiting, not bound");
        });
    }
}
