package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.client.ClientDeckState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server tells the client how many cards are in its deck, for the HUD. */
public record SyncDeckPayload(int deck, int deckSize, int regenTicks, int regenProgress) implements CustomPacketPayload {
    public static final Type<SyncDeckPayload> TYPE = new Type<>(TensuraFragments.id("sync_deck"));
    public static final StreamCodec<ByteBuf, SyncDeckPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SyncDeckPayload::deck,
            ByteBufCodecs.VAR_INT, SyncDeckPayload::deckSize,
            ByteBufCodecs.VAR_INT, SyncDeckPayload::regenTicks,
            ByteBufCodecs.VAR_INT, SyncDeckPayload::regenProgress,
            SyncDeckPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncDeckPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientDeckState.update(payload));
    }
}
