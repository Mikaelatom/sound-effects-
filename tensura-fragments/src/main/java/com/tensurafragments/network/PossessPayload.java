package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.client.ClientPossession;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server tells the client which paper beast it now sees through (-1: back in its own body). */
public record PossessPayload(int entityId) implements CustomPacketPayload {
    public static final Type<PossessPayload> TYPE = new Type<>(TensuraFragments.id("possess"));
    public static final StreamCodec<ByteBuf, PossessPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(PossessPayload::new, PossessPayload::entityId);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PossessPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPossession.possess(payload.entityId()));
    }
}
