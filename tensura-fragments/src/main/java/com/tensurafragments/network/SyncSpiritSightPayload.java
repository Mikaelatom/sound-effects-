package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.client.ClientSpiritSight;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server tells the client whether Spirit Sight is on (to show wild spirits) and the selected jutsu element. */
public record SyncSpiritSightPayload(boolean sight, int jutsu) implements CustomPacketPayload {
    public static final Type<SyncSpiritSightPayload> TYPE = new Type<>(TensuraFragments.id("sync_spirit_sight"));
    public static final StreamCodec<ByteBuf, SyncSpiritSightPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SyncSpiritSightPayload::sight,
            ByteBufCodecs.VAR_INT, SyncSpiritSightPayload::jutsu,
            SyncSpiritSightPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncSpiritSightPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientSpiritSight.update(payload.sight(), payload.jutsu()));
    }
}
