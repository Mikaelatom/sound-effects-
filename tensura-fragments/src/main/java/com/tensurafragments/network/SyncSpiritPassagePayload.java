package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.client.SpiritPassageHud;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** How many ticks a spirit has left in the material world before it fades (0: no clock running). */
public record SyncSpiritPassagePayload(int ticksLeft) implements CustomPacketPayload {
    public static final Type<SyncSpiritPassagePayload> TYPE = new Type<>(TensuraFragments.id("sync_spirit_passage"));
    public static final StreamCodec<ByteBuf, SyncSpiritPassagePayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(SyncSpiritPassagePayload::new, SyncSpiritPassagePayload::ticksLeft);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncSpiritPassagePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SpiritPassageHud.sync(payload.ticksLeft()));
    }
}
