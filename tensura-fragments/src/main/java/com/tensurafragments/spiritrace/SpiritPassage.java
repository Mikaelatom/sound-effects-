package com.tensurafragments.spiritrace;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.network.SyncSpiritPassagePayload;
import io.github.manasmods.manascore.race.api.SpawnPointHelper;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A spirit's time in the material world. In the Spirit Realm a spirit has its own form. The Book of Passage carries it
 * to the overworld as a bodiless spirit (Tensura's spiritual form), and a clock starts (5 minutes by default). Take a
 * body with Tensura's Possession before it runs out and you can stay; otherwise your spirit fades, you die, and you
 * wake up back in the Spirit Realm in your own form. Any spirit in spiritual form outside the realm is on the clock.
 */
public final class SpiritPassage {
    /** Your spirit fading away. */
    public static final ResourceKey<DamageType> FADED =
            ResourceKey.create(Registries.DAMAGE_TYPE, TensuraFragments.id("spirit_faded"));

    private SpiritPassage() {
    }

    public static boolean isSpiritual(Player player) {
        IExistence existence = TensuraStorages.getExistenceFrom(player);
        return existence != null && existence.isSpiritualForm();
    }

    public static boolean inSpiritRealm(Player player) {
        return player.level().dimension() == SpiritRealm.KEY;
    }

    /** Game time the spirit fades at, or 0 if no clock is running. */
    public static long deadline(Player player) {
        return player.getData(ModRegistries.SPIRIT_DEADLINE);
    }

    public static int limitTicks() {
        return Config.SPIRIT_MATERIAL_SECONDS.get() * 20;
    }

    /** The book: from the Spirit Realm out to the overworld, or (early) home again. */
    public static boolean use(ServerPlayer player) {
        if (!SpiritRaces.isSpirit(player)) {
            player.displayClientMessage(Component.translatable("tensurafragments.spirit_race.not_spirit"), true);
            return false;
        }
        if (inSpiritRealm(player)) {
            ServerLevel overworld = player.server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            BlockPos ground = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
            effects(player);
            SpawnPointHelper.teleportToAcrossDimensions(player, overworld, ground.getX() + 0.5, ground.getY(),
                    ground.getZ() + 0.5, player.getYRot(), player.getXRot());
            cross(player);
            effects(player);
            return true;
        }
        // Back home, ahead of time (with or without a body), in your own form.
        if (player.server.getLevel(SpiritRealm.KEY) == null) {
            return false;
        }
        effects(player);
        SpawnPointHelper.teleportToNewSpawn(player, SpiritRealm.KEY, net.minecraft.world.level.block.Blocks.CALCITE.defaultBlockState());
        comeHome(player);
        effects(player);
        return true;
    }

    /** Out into the material world: always as a bodiless spirit, with the clock running. */
    public static void cross(ServerPlayer player) {
        setSpiritual(player, true);
        startClock(player);
        player.displayClientMessage(Component.translatable("tensurafragments.spirit_race.crossed",
                limitTicks() / 20 / 60).withColor(0x9FD8FF), false);
    }

    /** In the Spirit Realm a spirit has its own form: no spiritual form, no clock. */
    public static void comeHome(ServerPlayer player) {
        SpiritRelease.end(player, true);
        setSpiritual(player, false);
        player.setData(ModRegistries.SPIRIT_DEADLINE, 0L);
        sync(player);
    }

    static void setSpiritual(ServerPlayer player, boolean spiritual) {
        IExistence existence = TensuraStorages.getExistenceFrom(player);
        if (existence != null && existence.isSpiritualForm() != spiritual) {
            existence.setSpiritualForm(spiritual);
            existence.markDirty();
        }
    }

    private static void effects(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 40, 0.4, 0.8, 0.4, 0.05);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 1.5F, 0.6F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    static void startClock(ServerPlayer player) {
        player.setData(ModRegistries.SPIRIT_DEADLINE, player.level().getGameTime() + limitTicks());
        sync(player);
    }

    static void stopClock(ServerPlayer player) {
        player.setData(ModRegistries.SPIRIT_DEADLINE, 0L);
        sync(player);
    }

    static void sync(ServerPlayer player) {
        long deadline = deadline(player);
        int left = deadline == 0 ? 0 : (int) Math.max(0, deadline - player.level().getGameTime());
        SpiritRelease.State release = SpiritRelease.state(player);
        int releaseLeft = release == null ? 0 : (int) Math.max(0, release.until() - player.level().getGameTime());
        PacketDistributor.sendToPlayer(player, new SyncSpiritPassagePayload(left, release == null ? 0 : release.percent(),
                releaseLeft));
    }

    /** Checked every second for spirits. */
    static void tick(ServerPlayer player) {
        if (inSpiritRealm(player)) {
            // Home: your own form, no clock (whatever state you arrived in).
            if (isSpiritual(player) || deadline(player) != 0) {
                comeHome(player);
            }
            return;
        }
        boolean exposed = isSpiritual(player) && !inSpiritRealm(player) && !player.isCreative() && !player.isSpectator();
        long deadline = deadline(player);
        if (!exposed) {
            if (deadline != 0) {
                if (!isSpiritual(player) && !inSpiritRealm(player)) {
                    player.displayClientMessage(Component.translatable("tensurafragments.spirit_race.anchored")
                            .withColor(0x9FD8FF), false);
                }
                stopClock(player);
            }
            return;
        }
        if (deadline == 0) {
            // Out in the material world without a body (whatever way it got here): the clock starts.
            startClock(player);
            return;
        }
        long left = deadline - player.level().getGameTime();
        // Re-sent every second, so the HUD never shows a stale clock.
        sync(player);
        if (left <= 0) {
            fade(player);
        } else if (left <= 20 * 30 && left % (20 * 10) < 20) {
            player.displayClientMessage(Component.translatable("tensurafragments.spirit_race.fading", left / 20)
                    .withColor(0xFF9090), true);
        }
    }

    /** Out of time: the spirit fades and goes home the hard way. */
    static void fade(ServerPlayer player) {
        player.setData(ModRegistries.SPIRIT_DEADLINE, 0L);
        sync(player);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 40, 0.4, 0.8, 0.4, 0.05);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 2F, 0.5F);
        player.setHealth(1);
        player.hurt(player.damageSources().source(FADED), Float.MAX_VALUE);
        if (player.isAlive()) {
            // Something saved it (a totem, a skill): the fade still wins.
            player.setHealth(0);
            player.die(player.damageSources().source(FADED));
        }
    }

    /** Every spirit carries the book. */
    public static void giveBook(Player player) {
        if (!player.getInventory().hasAnyMatching(stack -> stack.is(ModRegistries.BOOK_OF_PASSAGE.get()))) {
            ItemStack book = new ItemStack(ModRegistries.BOOK_OF_PASSAGE.get());
            if (!player.getInventory().add(book)) {
                player.drop(book, false);
            }
        }
    }
}
