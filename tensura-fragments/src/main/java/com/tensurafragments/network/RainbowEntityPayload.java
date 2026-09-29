package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.client.ClientRainbow;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Tells a client that an entity is a rainbow spell, so it gets drawn in rainbow colours. */
public record RainbowEntityPayload(int entityId) implements CustomPacketPayload {
    public static final Type<RainbowEntityPayload> TYPE = new Type<>(TensuraFragments.id("rainbow_entity"));
    public static final StreamCodec<ByteBuf, RainbowEntityPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RainbowEntityPayload::entityId, RainbowEntityPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RainbowEntityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRainbow.add(payload.entityId()));
    }
}
