package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(TensuraFragments.MODID).versioned("7");
        registrar.playToClient(SyncDeckPayload.TYPE, SyncDeckPayload.STREAM_CODEC, SyncDeckPayload::handle);
        registrar.playToClient(SyncShikigamiPayload.TYPE, SyncShikigamiPayload.STREAM_CODEC, SyncShikigamiPayload::handle);
    }
}
