package com.tensurafragments.test;

import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.ally.Alliances;
import com.tensurafragments.ally.Allies;
import com.tensurafragments.ally.Companions;
import com.tensurafragments.shikigami.BeastKind;
import com.tensurafragments.shikigami.PaperBeastEntity;
import com.tensurafragments.shikigami.PaperBeasts;
import com.tensurafragments.shikigami.ShikigamiEntity;
import com.tensurafragments.skill.ModSkills;
import com.tensurafragments.soul.CapturedSoul;
import com.tensurafragments.soul.SoulBond;
import com.tensurafragments.soul.SoulReaper;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.network.c2s.RequestNamingKeyPacket;
import io.github.manasmods.tensura.network.c2s.RequestNamingMenuPacket;
import io.github.manasmods.tensura.storage.TensuraStorages;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-world tests for named companions: name tags on summons, and named ones staying with you for good. */
@GameTestHolder(TensuraFragments.MODID)
@PrefixGameTestTemplate(false)
public final class CompanionGameTests {
    private static final int GROUND = TestPlayers.GROUND;

    private CompanionGameTests() {
    }

    private static ServerPlayer player(GameTestHelper helper, double x, double z) {
        ServerPlayer player = TestPlayers.spawn(helper, x, z);
        TestPlayers.giveMagicules(player, 100_000);
        player.getInventory().add(new ItemStack(Items.PAPER, 20));
        player.setData(ModRegistries.SUBSTITUTION_ENABLED, false);
        return player;
    }

    private static ItemStack nameTag(String name) {
        ItemStack tag = new ItemStack(Items.NAME_TAG);
        tag.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return tag;
    }

    private static PaperBeastEntity hound(GameTestHelper helper, ServerPlayer player) {
        SkillHelper.learnSkill(player, ModSkills.SHIKIGAMI_CONTROL.get());
        player.setData(ModRegistries.SELECTED_BEAST, BeastKind.HOUND.ordinal());
        helper.assertTrue(PaperBeasts.fold(player), "folded a hound");
        List<PaperBeastEntity> beasts = PaperBeasts.beasts(player);
        return beasts.get(beasts.size() - 1);
    }

    /** A name tag works on a summoned soul, and the named soul outlasts its timer and isn't recalled. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void namedSoulStaysForGood(GameTestHelper helper) {
        ServerPlayer player = player(helper, 4.5, 1.5);
        SkillHelper.learnSkill(player, ModSkills.SOUL_REAPER.get());
        CapturedSoul husk = new CapturedSoul("minecraft:husk", "Husk", 40);
        player.setData(ModRegistries.SOULS, new ArrayList<>(List.of(husk)));
        player.setData(ModRegistries.SELECTED_SOUL, 0);
        TensuraStorages.getExistenceFrom(player).setSoulPoints(5000);
        helper.assertTrue(SoulReaper.summon(player), "summoned");
        Mob ghost = SoulReaper.summons(player).get(0);
        player.setItemInHand(InteractionHand.MAIN_HAND, nameTag("Rex"));
        player.interactOn(ghost, InteractionHand.MAIN_HAND);
        helper.assertTrue(Companions.isNamed(ghost) && "Rex".equals(ghost.getCustomName().getString()), "named Rex");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the name tag was used up");
        // Its time runs out...
        SoulBond bond = SoulBond.get(ghost);
        ghost.setData(ModRegistries.SOUL_BOND, new SoulBond(bond.owner(), 0, true, bond.stacks(), bond.soul()));
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(ghost.isAlive(), "...and it stays");
            helper.assertFalse(SoulReaper.recall(player), "named souls aren't recalled");
            helper.assertTrue(ghost.isAlive(), "still here");
            helper.succeed();
        });
    }

    /** A name tag on someone else's summon does nothing special. */
    @GameTest(template = "platform")
    public static void cantNameSomeoneElsesSummon(GameTestHelper helper) {
        ServerPlayer owner = player(helper, 4.5, 1.5);
        ServerPlayer other = player(helper, 2.5, 1.5);
        PaperBeastEntity hound = hound(helper, owner);
        other.setItemInHand(InteractionHand.MAIN_HAND, nameTag("Thief"));
        other.interactOn(hound, InteractionHand.MAIN_HAND);
        helper.assertFalse(Companions.isNamed(hound), "not a companion of someone else");
        helper.succeed();
    }

    /** Asking each other makes two players allies; one asking alone doesn't. */
    @GameTest(template = "platform")
    public static void askingEachOtherMakesAllies(GameTestHelper helper) {
        ServerPlayer a = player(helper, 4.5, 1.5);
        ServerPlayer b = player(helper, 2.5, 1.5);
        helper.assertFalse(Alliances.ask(a, b), "one asking isn't enough");
        helper.assertFalse(Alliances.areAllies(a.server, a.getUUID(), b.getUUID()), "not allies yet");
        helper.assertTrue(Alliances.ask(b, a), "asking back makes them allies");
        helper.assertTrue(Alliances.areAllies(a.server, b.getUUID(), a.getUUID()), "allies both ways");
        helper.assertTrue(Alliances.alliesOf(a.server, a.getUUID()).equals(List.of(b.getUUID())), "listed");
        helper.assertTrue(Alliances.unally(a.server, a.getUUID(), b.getUUID())
                && !Alliances.areAllies(a.server, a.getUUID(), b.getUUID()), "and can part ways");
        helper.succeed();
    }

    /** An ally can name your summons (and it stays yours); summons never turn on an ally or an ally's summons. */
    @GameTest(template = "platform")
    public static void alliesCanNameEachOthersSummons(GameTestHelper helper) {
        ServerPlayer owner = player(helper, 4.5, 1.5);
        ServerPlayer friend = player(helper, 2.5, 1.5);
        PaperBeastEntity hound = hound(helper, owner);
        PaperBeastEntity friendsHound = hound(helper, friend);
        Alliances.ask(owner, friend);
        Alliances.ask(friend, owner);
        friend.setItemInHand(InteractionHand.MAIN_HAND, nameTag("Buddy"));
        friend.interactOn(hound, InteractionHand.MAIN_HAND);
        helper.assertTrue(Companions.isNamed(hound) && "Buddy".equals(hound.getCustomName().getString()), "the ally named it");
        helper.assertTrue(owner.getUUID().equals(hound.getOwnerUUID()), "still the summoner's");
        helper.assertTrue(Allies.isFriendly(friend, owner) && Allies.isFriendly(friendsHound, owner),
                "an ally and their summons are friendly");
        friend.hurt(friend.damageSources().playerAttack(owner), 1);
        helper.assertTrue(Allies.target(owner) == null, "hitting your ally doesn't send your summons after them");
        helper.succeed();
    }

    /** Tensura's Naming works on any creature now, not only the ones on its list (a pig isn't on it). */
    @GameTest(template = "platform")
    public static void tensuraNamingWorksOnAnyCreature(GameTestHelper helper) {
        ServerPlayer player = player(helper, 4.5, 1.5);
        net.minecraft.world.entity.animal.Pig pig = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.PIG,
                new net.minecraft.world.phys.Vec3(4.5, GROUND, 3.5));
        // Weakened, so it submits.
        pig.setHealth(1);
        helper.assertTrue(RequestNamingKeyPacket.canName(player, pig), "a pig can be named");
        helper.succeed();
    }

    /**
     * Tensura's Naming on a summon: yours or an ally's can be named right away (no need to weaken it), someone else's
     * can't, and the named summon becomes a companion.
     */
    @GameTest(template = "platform")
    public static void tensuraNamingOnSummons(GameTestHelper helper) {
        ServerPlayer owner = player(helper, 4.5, 1.5);
        ServerPlayer stranger = player(helper, 2.5, 1.5);
        PaperBeastEntity hound = hound(helper, owner);
        var ex = io.github.manasmods.tensura.storage.TensuraStorages.getExistenceFrom(hound);
        helper.assertTrue(RequestNamingKeyPacket.canName(owner, hound), "you can name your own summon: hound EP "
                + io.github.manasmods.tensura.util.EnergyHelper.getMaxEP(hound) + " vs your base "
                + io.github.manasmods.tensura.util.EnergyHelper.getBaseMaxEP(owner) + ", name " + ex.getName()
                + ", permanent owner " + ex.getPermanentOwner() + ", subordinate-of-it "
                + io.github.manasmods.tensura.util.SubordinateHelper.isSubordinate(hound, owner));
        helper.assertFalse(RequestNamingKeyPacket.canName(stranger, hound), "a stranger can't");
        Alliances.ask(owner, stranger);
        Alliances.ask(stranger, owner);
        helper.assertTrue(RequestNamingKeyPacket.canName(stranger, hound), "an ally can");
        // Enough magicules to pay for the naming.
        io.github.manasmods.tensura.util.EnergyHelper.increaseMaxEP(owner, 1_000_000);
        TestPlayers.giveMagicules(owner, 1_000_000);
        RequestNamingMenuPacket.name(hound, owner, RequestNamingMenuPacket.NamingType.LOW, "Rex");
        helper.assertTrue("Rex".equals(io.github.manasmods.tensura.storage.TensuraStorages.getExistenceFrom(hound).getName()),
                "Tensura named it Rex");
        helper.assertTrue(Companions.isNamed(hound), "and it's a companion for good");
        helper.succeed();
    }

    /** Naming something, even the strongest kind of naming, never takes from your max EP. */
    @GameTest(template = "platform")
    public static void namingKeepsYourMaxEp(GameTestHelper helper) {
        ServerPlayer owner = player(helper, 4.5, 1.5);
        PaperBeastEntity hound = hound(helper, owner);
        io.github.manasmods.tensura.util.EnergyHelper.increaseMaxEP(owner, 1_000_000);
        TestPlayers.giveMagicules(owner, 1_000_000);
        double before = io.github.manasmods.tensura.util.EnergyHelper.getBaseMaxEP(owner);
        // Tensura takes from your maximum by chance; make it certain, so this would catch it.
        double chance = io.github.manasmods.tensura.menu.NamingMenu.CONFIG.endowLostChance;
        io.github.manasmods.tensura.menu.NamingMenu.CONFIG.endowLostChance = 100;
        try {
            RequestNamingMenuPacket.name(hound, owner, RequestNamingMenuPacket.NamingType.HIGH, "Rex");
        } finally {
            io.github.manasmods.tensura.menu.NamingMenu.CONFIG.endowLostChance = chance;
        }
        helper.assertTrue("Rex".equals(io.github.manasmods.tensura.storage.TensuraStorages.getExistenceFrom(hound).getName()),
                "named");
        double after = io.github.manasmods.tensura.util.EnergyHelper.getBaseMaxEP(owner);
        helper.assertTrue(after >= before, "max EP kept: " + before + " -> " + after);
        helper.succeed();
    }

    /** Named shikigami have no time limit; named beasts ignore being dismissed. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void namedShikigamiAndBeastsDontGo(GameTestHelper helper) {
        ServerPlayer player = player(helper, 4.5, 1.5);
        PaperBeastEntity named = hound(helper, player);
        PaperBeastEntity unnamed = hound(helper, player);
        Companions.name(named, Component.literal("Patch"));
        BlockPos at = helper.absolutePos(new BlockPos(6, GROUND, 4));
        ShikigamiEntity shikigami = ShikigamiEntity.create(player, Blocks.STONE.defaultBlockState(), 1.5F, at, 1F);
        helper.getLevel().addFreshEntity(shikigami);
        Companions.name(shikigami, Component.literal("Rocky"));
        shikigami.tickCount = shikigami.getLifetime() + 1;
        PaperBeasts.dismissAll(player);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(named.isAlive() && !unnamed.isAlive(), "only the unnamed beast unfolded");
            helper.assertTrue(shikigami.isAlive(), "the named shikigami outlasted its time");
            helper.succeed();
        });
    }

    /** A named companion left far behind catches up. */
    @GameTest(template = "platform")
    public static void companionCatchesUp(GameTestHelper helper) {
        ServerPlayer player = player(helper, 4.5, 1.5);
        PaperBeastEntity hound = hound(helper, player);
        Companions.name(hound, Component.literal("Patch"));
        player.teleportTo(player.getX() + 40, player.getY(), player.getZ());
        Companions.gather(player);
        helper.assertTrue(hound.distanceTo(player) < 4, "caught up, " + hound.distanceTo(player) + " away");
        helper.succeed();
    }

    /** Named companions leave with you when you log off and come back when you log in. */
    @GameTest(template = "platform")
    public static void companionsLeaveAndReturnWithYou(GameTestHelper helper) {
        ServerPlayer player = player(helper, 4.5, 1.5);
        PaperBeastEntity hound = hound(helper, player);
        PaperBeastEntity unnamed = hound(helper, player);
        Companions.name(hound, Component.literal("Patch"));
        float health = hound.getHealth();
        Companions.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        helper.assertTrue(hound.isRemoved() && unnamed.isAlive(), "the named hound left with you");
        helper.assertTrue(player.getData(ModRegistries.AWAY_COMPANIONS).size() == 1, "kept with you");
        Companions.onLogin(new PlayerEvent.PlayerLoggedInEvent(player));
        List<Entity> back = Companions.companions(player);
        helper.assertTrue(back.size() == 1 && back.get(0) instanceof PaperBeastEntity beast && beast.getKind() == BeastKind.HOUND
                && "Patch".equals(beast.getCustomName().getString()) && beast.getHealth() == health
                && beast.distanceTo(player) < 4, "Patch is back: " + back);
        helper.assertTrue(player.getData(ModRegistries.AWAY_COMPANIONS).isEmpty(), "nothing left away");
        helper.succeed();
    }
}
