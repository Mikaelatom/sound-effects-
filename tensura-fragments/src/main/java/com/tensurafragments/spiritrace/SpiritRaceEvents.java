package com.tensurafragments.spiritrace;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.ModRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** The spirit's clock, the book on every respawn, and the race on Tensura's selection menu. */
@EventBusSubscriber(modid = TensuraFragments.MODID)
public final class SpiritRaceEvents {
    private SpiritRaceEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpiritRelease.tick(player);
        }
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            if (SpiritRaces.isSpirit(player)) {
                SpiritPassage.tick(player);
            } else {
                SpiritPassage.stopClock(player);
            }
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpiritRelease.end(player, true);
            if (SpiritRaces.isSpirit(player)) {
                // Back in the Spirit Realm in your own form (Tensura would otherwise leave you as you died).
                SpiritPassage.comeHome(player);
                SpiritPassage.giveBook(player);
            } else {
                SpiritPassage.stopClock(player);
            }
        }
    }

    /** The book never drops: a spirit gets it back when it wakes up in the Spirit Realm. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            event.getDrops().removeIf(item -> item.getItem().is(ModRegistries.BOOK_OF_PASSAGE.get()));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpiritPassage.sync(player);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpiritPassage.sync(player);
        }
    }
}
