package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.client.ClientSouls;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Tells a client that an entity is a summoned soul or soul-possessed, so it's drawn that way. */
public record SoulEntityPayload(int entityId, int kind) implements CustomPacketPayload {
    public static final Type<SoulEntityPayload> TYPE = new Type<>(TensuraFragments.id("soul_entity"));
    public static final StreamCodec<ByteBuf, SoulEntityPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SoulEntityPayload::entityId,
            ByteBufCodecs.VAR_INT, SoulEntityPayload::kind,
            SoulEntityPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SoulEntityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientSouls.add(payload.entityId(), payload.kind()));
    }
}
