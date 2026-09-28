package com.tensurafragments.client;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.network.CardActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

@EventBusSubscriber(modid = TensuraFragments.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ClientDeckState.tick();

        while (ClientSetup.THROW_CARD.consumeClick()) {
            send(CardActionPayload.THROW, player);
        }
        while (ClientSetup.TELEPORT_CARD.consumeClick()) {
            send(CardActionPayload.TELEPORT, player);
        }
        while (ClientSetup.DETONATE_CARDS.consumeClick()) {
            send(player.isShiftKeyDown() ? CardActionPayload.DETONATE_AIMED : CardActionPayload.DETONATE_ALL, player);
        }
    }

    private static void send(int action, LocalPlayer player) {
        Vec3 velocity = player.getDeltaMovement();
        PacketDistributor.sendToServer(new CardActionPayload(action,
                new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z)));
    }
}
