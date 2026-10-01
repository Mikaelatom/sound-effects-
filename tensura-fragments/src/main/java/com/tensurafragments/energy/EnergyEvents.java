package com.tensurafragments.energy;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.network.EnergyEntityPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = TensuraFragments.MODID)
public final class EnergyEvents {
    private EnergyEvents() {
    }

    /** Players who come into range of an energy spell need to know to draw it glowing green. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && EnergyMagic.isEnergy(event.getTarget())) {
            PacketDistributor.sendToPlayer(player, new EnergyEntityPayload(event.getTarget().getId()));
        }
    }
}
