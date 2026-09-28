package com.tensurafragments.test;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.card.CardEntity;
import com.mojang.authlib.GameProfile;
import com.tensurafragments.skill.GambitCards;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-world tests for Gambit Cards. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(TensuraFragments.MODID)
@PrefixGameTestTemplate(false)
public final class CardGameTests {
    private CardGameTests() {
    }

    /** An offline player that can take damage (NeoForge's FakePlayer is normally invulnerable). */
    private static final class TestPlayer extends FakePlayer {
        TestPlayer(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "card_tester"));
            // Fake players never tick, so their 3 second spawn protection would never run out.
            ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class, this, 0, "spawnInvulnerableTime");
        }

        @Override
        public boolean isInvulnerableTo(DamageSource source) {
            return false;
        }

        /** A FakePlayer has no connection, so the vanilla player teleport (which goes through it) does nothing. */
        @Override
        public void teleportTo(double x, double y, double z) {
            moveTo(x, y, z, getYRot(), getXRot());
        }
    }

    private static ServerPlayer player(GameTestHelper helper, double x, double z) {
        ServerPlayer player = new TestPlayer(helper.getLevel());
        Vec3 pos = helper.absoluteVec(new Vec3(x, 1, z));
        player.moveTo(pos.x, pos.y, pos.z, 0, 0);
        helper.getLevel().addNewPlayer(player); // so blasts can find it
        return player;
    }

    private static CardEntity card(GameTestHelper helper, ServerPlayer owner, double x, double z) {
        CardEntity card = CardEntity.create(owner);
        Vec3 pos = helper.absoluteVec(new Vec3(x, 1.2, z));
        card.setPos(pos.x, pos.y, pos.z);
        helper.getLevel().addFreshEntity(card);
        return card;
    }

    @GameTest(template = "platform")
    public static void blastHurtsNearbyButNotFar(GameTestHelper helper) {
        ServerPlayer owner = player(helper, 0.5, 0.5);
        Pig near = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(4.5, 1, 4.5));
        Pig far = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(4.5, 1, 8.5)); // 4 blocks away: outside the blast
        card(helper, owner, 4.5, 4.0).detonate();

        helper.assertTrue(near.getHealth() < near.getMaxHealth(), "pig next to the card should be hurt");
        helper.assertTrue(far.getHealth() == far.getMaxHealth(), "pig outside the blast radius should be untouched");
        helper.assertTrue(owner.getHealth() == owner.getMaxHealth(), "owner far away should be untouched");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void blastHurtsOwnerWhenTooClose(GameTestHelper helper) {
        // Explosions do nothing to players on Peaceful.
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        ServerPlayer owner = player(helper, 4.5, 4.5);
        card(helper, owner, 4.5, 5.0).detonate();
        helper.assertTrue(owner.getHealth() < owner.getMaxHealth(), "owner standing on the card should take self damage");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void blastChainsIntoNearbyCards(GameTestHelper helper) {
        ServerPlayer owner = player(helper, 0.5, 0.5);
        CardEntity first = card(helper, owner, 4.5, 3.0);
        CardEntity second = card(helper, owner, 4.5, 5.5);
        first.detonate();

        helper.assertTrue(second.isAlive(), "chained card waits a few ticks before going off");
        helper.succeedWhen(() -> helper.assertTrue(second.isRemoved(), "second card should chain-detonate"));
    }

    @GameTest(template = "platform")
    public static void teleportMovesOwnerToCard(GameTestHelper helper) {
        ServerPlayer owner = player(helper, 0.5, 0.5);
        CardEntity card = card(helper, owner, 7.5, 7.5);
        GambitCards.teleport(owner);

        helper.assertTrue(owner.position().distanceTo(card.position()) < 2.0, "owner should land at the card");
        helper.assertTrue(card.isRemoved(), "card is used up by the teleport");
        helper.succeed();
    }
}
