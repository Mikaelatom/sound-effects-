package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(TensuraFragments.MODID).versioned("18");
        registrar.playToClient(SyncDeckPayload.TYPE, SyncDeckPayload.STREAM_CODEC, SyncDeckPayload::handle);
        registrar.playToClient(EnergyEntityPayload.TYPE, EnergyEntityPayload.STREAM_CODEC, EnergyEntityPayload::handle);
        registrar.playToClient(RainbowEntityPayload.TYPE, RainbowEntityPayload.STREAM_CODEC, RainbowEntityPayload::handle);
        registrar.playToClient(SyncShikigamiPayload.TYPE, SyncShikigamiPayload.STREAM_CODEC, SyncShikigamiPayload::handle);
        registrar.playToClient(SyncSpiritSightPayload.TYPE, SyncSpiritSightPayload.STREAM_CODEC, SyncSpiritSightPayload::handle);
        registrar.playToClient(PossessPayload.TYPE, PossessPayload.STREAM_CODEC, PossessPayload::handle);
        registrar.playToClient(SoulEntityPayload.TYPE, SoulEntityPayload.STREAM_CODEC, SoulEntityPayload::handle);
        registrar.playToClient(SyncSoulsPayload.TYPE, SyncSoulsPayload.STREAM_CODEC, SyncSoulsPayload::handle);
        registrar.playToClient(SyncSpiritPassagePayload.TYPE, SyncSpiritPassagePayload.STREAM_CODEC,
                SyncSpiritPassagePayload::handle);
        registrar.playToServer(BeastInputPayload.TYPE, BeastInputPayload.STREAM_CODEC, BeastInputPayload::handle);
        registrar.playToClient(OpenRuneCanvasPayload.TYPE, OpenRuneCanvasPayload.STREAM_CODEC, OpenRuneCanvasPayload::handle);
        registrar.playToServer(DrawRunePayload.TYPE, DrawRunePayload.STREAM_CODEC, DrawRunePayload::handle);
        registrar.playToClient(OpenSkillPickPayload.TYPE, OpenSkillPickPayload.STREAM_CODEC, OpenSkillPickPayload::handle);
        registrar.playToClient(SyncKnownRunesPayload.TYPE, SyncKnownRunesPayload.STREAM_CODEC, SyncKnownRunesPayload::handle);
        registrar.playToServer(PickSkillPayload.TYPE, PickSkillPayload.STREAM_CODEC, PickSkillPayload::handle);
    }
}
