package com.tensurafragments.client;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.network.CombatPosePayload;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Each player's Combat Mode state (blocking, holding, held, knocked down...), as the server last told us. */
@EventBusSubscriber(modid = TensuraFragments.MODID, value = Dist.CLIENT)
public final class CombatPoses {
    private static final Map<Integer, Integer> POSES = new HashMap<>();

    private CombatPoses() {
    }

    public static void set(int entityId, int pose) {
        if (pose == CombatPosePayload.NONE) {
            POSES.remove(entityId);
        } else {
            POSES.put(entityId, pose);
        }
    }

    public static int pose(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player && ClientCombat.isBlocking() && !POSES.containsKey(entity.getId())) {
            // Your own block shows at once, before the server's word comes back.
            return CombatPosePayload.BLOCK;
        }
        return POSES.getOrDefault(entity.getId(), CombatPosePayload.NONE);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        POSES.clear();
    }
}
