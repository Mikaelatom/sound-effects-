package com.tensurafragments.test;

import com.mojang.authlib.GameProfile;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Offline players for GameTests. */
final class TestPlayers {
    /** Relative y of the top of the "platform" structure's floor (the structure sits one block above its origin). */
    static final int GROUND = 2;

    private TestPlayers() {
    }

    /** A FakePlayer that can take damage and teleport (NeoForge's is invulnerable and has no connection). */
    static final class TestPlayer extends FakePlayer {
        TestPlayer(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "fragments_tester"));
            // Fake players never tick, so their 3 second spawn protection would never run out.
            ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class, this, 0, "spawnInvulnerableTime");
        }

        @Override
        public boolean isInvulnerableTo(DamageSource source) {
            return false;
        }

        /** The vanilla player teleport goes through the (missing) connection, so just move. */
        @Override
        public void teleportTo(double x, double y, double z) {
            moveTo(x, y, z, getYRot(), getXRot());
        }
    }

    /**
     * A plain server player (not a FakePlayer, which refuses to ride anything) with NeoForge's do-nothing connection,
     * for riding tests.
     */
    static final class RidingPlayer extends ServerPlayer {
        RidingPlayer(ServerLevel level) {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), "fragments_rider"),
                    net.minecraft.server.level.ClientInformation.createDefault());
            // Borrow a FakePlayer's connection (it's package-private): everything sent through it goes nowhere.
            connection = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "fragments_rider_link")).connection;
        }
    }

    static ServerPlayer spawnRider(GameTestHelper helper, double x, double z) {
        ServerPlayer player = new RidingPlayer(helper.getLevel());
        Vec3 pos = helper.absoluteVec(new Vec3(x, GROUND, z));
        player.moveTo(pos.x, pos.y, pos.z, 0, 0);
        helper.getLevel().addNewPlayer(player);
        return player;
    }

    /** A survival player at the given spot inside the test structure, added to the world so blasts can find it. */
    static ServerPlayer spawn(GameTestHelper helper, double x, double z) {
        ServerPlayer player = new TestPlayer(helper.getLevel());
        Vec3 pos = helper.absoluteVec(new Vec3(x, GROUND, z));
        player.moveTo(pos.x, pos.y, pos.z, 0, 0);
        helper.getLevel().addNewPlayer(player);
        return player;
    }

    static void giveMagicules(ServerPlayer player, double amount) {
        IExistence existence = TensuraStorages.getExistenceFrom(player);
        if (existence != null) {
            existence.setMagicule(amount);
        }
    }
}
