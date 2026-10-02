package com.tensurafragments.test;

import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.skill.ModSkills;
import com.tensurafragments.soul.CapturedSoul;
import com.tensurafragments.soul.SoulBond;
import com.tensurafragments.soul.SoulReaper;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.util.EnergyHelper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-world tests for Soul Reaper: souls from kills, Soul Summon, Soul Absorb and Soul Possession. */
@GameTestHolder(TensuraFragments.MODID)
@PrefixGameTestTemplate(false)
public final class SoulGameTests {
    private static final int GROUND = TestPlayers.GROUND;

    private SoulGameTests() {
    }

    private static ServerPlayer reaper(GameTestHelper helper, double x, double z) {
        ServerPlayer player = TestPlayers.spawn(helper, x, z);
        TestPlayers.giveMagicules(player, 100_000);
        SkillHelper.learnSkill(player, ModSkills.SOUL_REAPER.get());
        return player;
    }

    private static void giveSouls(ServerPlayer player, CapturedSoul... souls) {
        player.setData(ModRegistries.SOULS, new ArrayList<>(List.of(souls)));
        player.setData(ModRegistries.SELECTED_SOUL, 0);
    }

    private static void setSoulPoints(ServerPlayer player, int points) {
        TensuraStorages.getExistenceFrom(player).setSoulPoints(points);
    }

    /** Not a Demon Lord Seed, and the kill still gives souls and captures the husk's soul. */
    @GameTest(template = "platform")
    public static void killingGivesSoulsAnyway(GameTestHelper helper) {
        ServerPlayer player = reaper(helper, 4.5, 4.5);
        helper.assertFalse(TensuraStorages.getExistenceFrom(player).isDemonLordSeed(), "not a seed");
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(4.5, GROUND, 6.5));
        husk.hurt(player.damageSources().playerAttack(player), 10_000);
        helper.assertTrue(husk.isDeadOrDying(), "husk killed");
        helper.assertTrue(SoulReaper.soulPoints(player) >= 1000, "souls went up, at " + SoulReaper.soulPoints(player));
        List<CapturedSoul> souls = SoulReaper.souls(player);
        helper.assertTrue(souls.size() == 1 && souls.get(0).type().equals("minecraft:husk"), "captured the husk's soul: " + souls);
        helper.succeed();
    }

    /** Kills by your summoned soul count for you; summoned souls themselves give no soul. */
    @GameTest(template = "platform")
    public static void summonedSoulFightsForYou(GameTestHelper helper) {
        ServerPlayer player = reaper(helper, 4.5, 1.5);
        giveSouls(player, new CapturedSoul("minecraft:husk", "Husk", 40));
        setSoulPoints(player, 5000);
        player.setYRot(0);
        helper.assertTrue(SoulReaper.summon(player), "summoned");
        List<Mob> summons = SoulReaper.summons(player);
        helper.assertTrue(summons.size() == 1 && summons.get(0) instanceof Husk, "a husk soul came back");
        Mob ghost = summons.get(0);
        int worth = SoulReaper.soulValue(new CapturedSoul("minecraft:husk", "Husk", 40));
        helper.assertTrue(SoulReaper.soulPoints(player) == 5000 - worth, "paid in souls, left " + SoulReaper.soulPoints(player));
        helper.assertTrue(SoulReaper.souls(player).isEmpty(), "the soul is used up");
        // It won't target its reaper.
        ghost.setTarget(player);
        helper.assertTrue(ghost.getTarget() == null, "never turns on you");
        // Its kills are yours.
        Zombie enemy = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(1.5, GROUND, 7.5));
        enemy.hurt(ghost.damageSources().mobAttack(ghost), 10_000);
        helper.assertTrue(SoulReaper.souls(player).size() == 1, "the zombie's soul is yours");
        // Killing the ghost gives no soul.
        ghost.hurt(player.damageSources().playerAttack(player), 10_000);
        helper.assertTrue(SoulReaper.souls(player).size() == 1, "a summoned soul gives nothing");
        helper.succeed();
    }

    /** One kill, one summon: two husk kills give two husk summons and no more, even in creative. */
    @GameTest(template = "platform")
    public static void eachSoulSummonsOnce(GameTestHelper helper) {
        ServerPlayer player = reaper(helper, 4.5, 1.5);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        for (int i = 0; i < 2; i++) {
            Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(1.5 + i * 6, GROUND, 7.5));
            husk.hurt(player.damageSources().playerAttack(player), 10_000);
        }
        helper.assertTrue(SoulReaper.souls(player).size() == 2, "two souls");
        int points = SoulReaper.soulPoints(player);
        helper.assertTrue(SoulReaper.summon(player), "first summon");
        helper.assertTrue(SoulReaper.soulPoints(player) < points, "the count went down");
        helper.assertTrue(SoulReaper.summon(player), "second summon");
        helper.assertFalse(SoulReaper.summon(player), "no third: those souls are spent");
        helper.assertTrue(SoulReaper.summons(player).size() == 2 && SoulReaper.souls(player).isEmpty(), "two ghosts, no souls left");
        helper.assertTrue(SoulReaper.soulPoints(player) == 0, "count back to 0, at " + SoulReaper.soulPoints(player));
        helper.succeed();
    }

    /** Recalling gives the summoned souls back, worth and all, to summon again later. */
    @GameTest(template = "platform")
    public static void recallGivesSoulsBack(GameTestHelper helper) {
        ServerPlayer player = reaper(helper, 4.5, 1.5);
        CapturedSoul husk = new CapturedSoul("minecraft:husk", "Husk", 40);
        CapturedSoul zombie = new CapturedSoul("minecraft:zombie", "Zombie", 20);
        giveSouls(player, husk, zombie);
        int worth = SoulReaper.soulValue(husk) + SoulReaper.soulValue(zombie);
        setSoulPoints(player, worth);
        helper.assertFalse(SoulReaper.recall(player), "nothing out to recall");
        helper.assertTrue(SoulReaper.summon(player) && SoulReaper.summon(player), "both summoned");
        helper.assertTrue(SoulReaper.souls(player).isEmpty() && SoulReaper.soulPoints(player) == 0, "both souls spent");
        helper.assertTrue(SoulReaper.recall(player), "recalled");
        helper.assertTrue(SoulReaper.summons(player).isEmpty(), "the ghosts are gone");
        helper.assertTrue(SoulReaper.souls(player).size() == 2 && SoulReaper.souls(player).containsAll(List.of(husk, zombie)),
                "both souls back: " + SoulReaper.souls(player));
        helper.assertTrue(SoulReaper.soulPoints(player) == worth, "the count is back, at " + SoulReaper.soulPoints(player));
        helper.assertTrue(SoulReaper.summon(player), "and can be summoned again");
        helper.succeed();
    }

    /** A player's soul comes back as a zombie wearing their head. */
    @GameTest(template = "platform")
    public static void playerSoulsWearTheirHead(GameTestHelper helper) {
        ServerPlayer player = reaper(helper, 4.5, 1.5);
        giveSouls(player, new CapturedSoul(CapturedSoul.PLAYER, "Steve", 100));
        setSoulPoints(player, 5000);
        helper.assertTrue(SoulReaper.summon(player), "summoned");
        Mob ghost = SoulReaper.summons(player).get(0);
        helper.assertTrue(ghost instanceof Zombie && ghost.getItemBySlot(EquipmentSlot.HEAD).is(Items.PLAYER_HEAD), "zombie with a head");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void absorbingTakesAllItsEp(GameTestHelper helper) {
        ServerPlayer player = reaper(helper, 4.5, 4.5);
        CapturedSoul soul = new CapturedSoul("minecraft:husk", "Husk", 5000);
        giveSouls(player, soul);
        setSoulPoints(player, 10_000);
        double before = EnergyHelper.getMaxEP(player);
        helper.assertTrue(SoulReaper.absorb(player), "absorbed");
        helper.assertTrue(SoulReaper.soulPoints(player) == 10_000 - SoulReaper.soulValue(soul), "the count went down");
        double after = EnergyHelper.getMaxEP(player);
        helper.assertTrue(after - before >= 4999, "gained its EP: " + before + " -> " + after);
        helper.assertTrue(SoulReaper.souls(player).isEmpty(), "the soul is used up");
        helper.succeed();
    }

    /** Possession: a soul strong enough takes the creature over, and every soul makes it stronger. */
    @GameTest(template = "platform")
    public static void possessionMakesItYoursAndStronger(GameTestHelper helper) {
        ServerPlayer player = reaper(helper, 4.5, 0.5);
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(4.5, GROUND, 5.5));
        double huskEp = EnergyHelper.getMaxEP(husk);
        double baseHealth = husk.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
        player.lookAt(EntityAnchorArgument.Anchor.EYES, husk.position().add(0, 1, 0));
        // Too weak a soul can't take it over.
        giveSouls(player, new CapturedSoul("minecraft:chicken", "Chicken", huskEp - 1),
                new CapturedSoul("minecraft:husk", "Husk", huskEp + 100), new CapturedSoul("minecraft:husk", "Husk", 10));
        helper.assertFalse(SoulReaper.possess(player), "a chicken's soul is too weak (husk EP " + huskEp + ")");
        helper.assertTrue(SoulBond.get(husk) == null && SoulReaper.souls(player).size() == 3, "nothing changed");
        player.setData(ModRegistries.SELECTED_SOUL, 1);
        setSoulPoints(player, 100_000);
        helper.assertTrue(SoulReaper.possess(player), "possessed");
        helper.assertTrue(SoulReaper.soulPoints(player) < 100_000, "the count went down");
        SoulBond bond = SoulBond.get(husk);
        helper.assertTrue(bond != null && bond.owner().equals(player.getUUID()) && bond.stacks() == 1, "it's yours");
        helper.assertTrue(husk.getMaxHealth() >= baseHealth * 1.49, "stronger: " + husk.getMaxHealth());
        helper.assertTrue(EnergyHelper.getMaxEP(husk) > huskEp, "took on the soul's EP");
        // Once it's yours, any soul makes it stronger still.
        player.setData(ModRegistries.SELECTED_SOUL, 1);
        helper.assertTrue(SoulReaper.possess(player), "possessed again");
        helper.assertTrue(SoulBond.get(husk).stacks() == 2 && husk.getMaxHealth() >= baseHealth * 1.99,
                "two souls: " + husk.getMaxHealth());
        husk.setTarget(player);
        helper.assertTrue(husk.getTarget() == null, "never turns on you");
        helper.succeed();
    }

    /** A possessed zombie with its normal AI: it never goes for you, and it attacks what you punch. */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void possessedHostileFightsForYou(GameTestHelper helper) {
        ServerPlayer player = reaper(helper, 1.5, 1.5);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new Vec3(4.5, GROUND, 4.5));
        player.lookAt(EntityAnchorArgument.Anchor.EYES, zombie.position().add(0, 1, 0));
        giveSouls(player, new CapturedSoul("minecraft:zombie", "Zombie", 1.0E6));
        helper.assertTrue(SoulReaper.possess(player), "possessed");
        fightsForYou(helper, player, zombie);
    }

    /** A summoned zombie soul with its normal AI: same thing. */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void summonedHostileFightsForYou(GameTestHelper helper) {
        ServerPlayer player = reaper(helper, 1.5, 1.5);
        giveSouls(player, new CapturedSoul("minecraft:zombie", "Zombie", 20));
        player.setYRot(-45);
        helper.assertTrue(SoulReaper.summon(player), "summoned");
        fightsForYou(helper, player, SoulReaper.summons(player).get(0));
    }

    private static Mob tensuraZombie(GameTestHelper helper, double x, double z) {
        EntityType<?> type = EntityType.byString("tensura:zombie").orElseThrow();
        return (Mob) helper.spawn(type, new Vec3(x, GROUND, z));
    }

    private static boolean brainTargets(Mob mob, net.minecraft.world.entity.LivingEntity target) {
        var brain = mob.getBrain();
        return mob.getTarget() == target || (brain.checkMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET,
                net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)
                && brain.getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET).orElse(null) == target);
    }

    /** Control: a Tensura zombie (a SmartBrainLib brain mob) left alone does go for you, so the tests below mean something. */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void tensuraZombieAttacksNormally(GameTestHelper helper) {
        ServerPlayer player = reaper(helper, 1.5, 1.5);
        Mob zombie = tensuraZombie(helper, 5.5, 5.5);
        helper.succeedWhen(() -> helper.assertTrue(brainTargets(zombie, player), "goes for you"));
    }

    /** A possessed Tensura zombie: its brain never targets you, and it attacks what you punch. */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void possessedBrainMobFightsForYou(GameTestHelper helper) {
        ServerPlayer player = reaper(helper, 1.5, 1.5);
        Mob zombie = tensuraZombie(helper, 4.5, 4.5);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, zombie.position().add(0, 1, 0));
        giveSouls(player, new CapturedSoul("tensura:zombie", "Zombie", 1.0E9));
        helper.assertTrue(SoulReaper.possess(player), "possessed");
        fightsForYou(helper, player, zombie);
    }

    /** A summoned Tensura zombie soul: same thing. */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void summonedBrainMobFightsForYou(GameTestHelper helper) {
        ServerPlayer player = reaper(helper, 1.5, 1.5);
        giveSouls(player, new CapturedSoul("tensura:zombie", "Zombie", 20));
        player.setYRot(-45);
        helper.assertTrue(SoulReaper.summon(player), "summoned");
        fightsForYou(helper, player, SoulReaper.summons(player).get(0));
    }

    private static void fightsForYou(GameTestHelper helper, ServerPlayer player, Mob soul) {
        float health = player.getHealth();
        // A while with you standing right there: it must never go for you.
        helper.onEachTick(() -> {
            helper.assertFalse(brainTargets(soul, player), "it targeted you");
            helper.assertTrue(player.getHealth() >= health, "it hurt you");
        });
        helper.runAfterDelay(60, () -> {
            net.minecraft.world.entity.animal.Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(7.5, GROUND, 7.5));
            pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
            pig.setHealth(200);
            // You punch the pig.
            pig.hurt(player.damageSources().playerAttack(player), 1);
            player.setLastHurtMob(pig);
            helper.succeedWhen(() -> helper.assertTrue(pig.getHealth() < 195,
                    "it attacks what you punched: pig at " + pig.getHealth() + ", its target " + soul.getTarget()
                            + ", it is at " + helper.relativeVec(soul.position()) + " baby " + soul.isBaby()
                            + " navigating " + soul.getNavigation().isInProgress()
                            + " path " + (soul.getNavigation().getPath() == null ? null : soul.getNavigation().getPath().getTarget())
                            + " pig at " + helper.relativeVec(pig.position()) + " speed "
                            + soul.getAttributeValue(Attributes.MOVEMENT_SPEED) + " vehicle " + soul.getVehicle()
                            + " moving " + soul.getDeltaMovement() + " collided " + soul.horizontalCollision
                            + " noAi " + soul.isNoAi() + " goals "
                            + soul.goalSelector.getAvailableGoals().stream().filter(g -> g.isRunning())
                            .map(g -> g.getGoal().getClass().getSimpleName()).toList()));
        });
    }

    @GameTest(template = "platform")
    public static void sneakCyclesSouls(GameTestHelper helper) {
        ServerPlayer player = reaper(helper, 4.5, 4.5);
        giveSouls(player, new CapturedSoul("minecraft:husk", "Husk", 1), new CapturedSoul("minecraft:zombie", "Zombie", 2));
        helper.assertTrue(SoulReaper.selected(player).name().equals("Husk"), "first");
        SoulReaper.cycle(player);
        helper.assertTrue(SoulReaper.selected(player).name().equals("Zombie"), "second");
        SoulReaper.cycle(player);
        helper.assertTrue(SoulReaper.selected(player).name().equals("Husk"), "round again");
        helper.succeed();
    }
}
