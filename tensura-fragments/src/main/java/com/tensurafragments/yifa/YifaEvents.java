package com.tensurafragments.yifa;

import com.tensurafragments.TensuraFragments;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = TensuraFragments.MODID)
public final class YifaEvents {
    private YifaEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        SpiritCommunion.runPendingBursts();
    }

    /** The fire ball or wind sphere carrying released spirits is gone (it hit, or ran out): the spell goes off there. */
    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity().getRemovalReason() != null
                && event.getEntity().getRemovalReason().shouldDestroy()) {
            SpiritCommunion.onCarrierEnds(level, event.getEntity());
        }
    }
}
