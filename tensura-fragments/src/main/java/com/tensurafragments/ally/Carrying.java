package com.tensurafragments.ally;

import com.tensurafragments.TensuraFragments;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Riding your allies: right-click an ally with an empty hand to climb onto their shoulders, and you go wherever they
 * go (flying included). Sneak to get off; the one carrying can drop you with {@code /ally drop}.
 */
@EventBusSubscriber(modid = TensuraFragments.MODID)
public final class Carrying {
    private Carrying() {
    }

    /** Whether {@code rider} may climb onto {@code carrier} right now. */
    public static boolean canRide(ServerPlayer rider, ServerPlayer carrier) {
        return rider != carrier && rider.isAlive() && carrier.isAlive() && !rider.isSpectator() && !carrier.isSpectator()
                && Alliances.areAllies(rider.server, rider.getUUID(), carrier.getUUID())
                && !rider.isPassenger() && !rider.isVehicle()
                // One rider at a time, and no riding someone who's riding.
                && !carrier.isVehicle() && !carrier.isPassenger();
    }

    public static boolean ride(ServerPlayer rider, ServerPlayer carrier) {
        if (!canRide(rider, carrier) || !rider.startRiding(carrier, true)) {
            return false;
        }
        rider.displayClientMessage(Component.translatable("tensurafragments.ride.on", carrier.getDisplayName())
                .withStyle(ChatFormatting.GREEN), true);
        carrier.displayClientMessage(Component.translatable("tensurafragments.ride.carrying", rider.getDisplayName())
                .withStyle(ChatFormatting.GREEN), true);
        carrier.level().playSound(null, carrier.getX(), carrier.getY(), carrier.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                SoundSource.PLAYERS, 1F, 1.2F);
        return true;
    }

    /** Lets down whoever is riding {@code carrier}. Returns whether anyone was. */
    public static boolean drop(ServerPlayer carrier) {
        boolean any = false;
        for (Entity passenger : carrier.getPassengers()) {
            if (passenger instanceof Player) {
                passenger.stopRiding();
                any = true;
            }
        }
        return any;
    }

    /** Right-click an ally with an empty hand (not sneaking: that's for asking to be allies). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || event.getEntity().isShiftKeyDown()
                || !event.getEntity().getMainHandItem().isEmpty()
                || !(event.getEntity() instanceof ServerPlayer rider) || !(event.getTarget() instanceof ServerPlayer carrier)) {
            return;
        }
        if (ride(rider, carrier)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        } else if (!Alliances.areAllies(rider.server, rider.getUUID(), carrier.getUUID())) {
            rider.displayClientMessage(Component.translatable("tensurafragments.ride.not_ally", carrier.getDisplayName()), true);
        }
    }

    /**
     * Off before leaving: when a player riding another logs off, the game would otherwise take the one carrying them
     * with it.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.getVehicle() instanceof Player) {
                player.stopRiding();
            }
            drop(player);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ally").then(Commands.literal("drop").executes(context -> {
            ServerPlayer carrier = context.getSource().getPlayerOrException();
            boolean dropped = drop(carrier);
            context.getSource().sendSuccess(() -> Component.translatable(dropped
                    ? "tensurafragments.ride.dropped" : "tensurafragments.ride.nobody"), false);
            return dropped ? 1 : 0;
        })));
    }
}
