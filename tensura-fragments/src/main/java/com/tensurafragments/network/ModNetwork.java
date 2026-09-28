package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(TensuraFragments.MODID).versioned("1");
        registrar.playToServer(CardActionPayload.TYPE, CardActionPayload.STREAM_CODEC, CardActionPayload::handle);
        registrar.playToClient(SyncDeckPayload.TYPE, SyncDeckPayload.STREAM_CODEC, SyncDeckPayload::handle);
    }
}
