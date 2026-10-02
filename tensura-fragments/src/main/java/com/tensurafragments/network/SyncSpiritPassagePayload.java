package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.client.SpiritPassageHud;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A spirit's clocks: ticks left in the material world before it fades (0: no clock running), and an active Spirit
 * Release (percent 0: none) with its ticks left.
 */
public record SyncSpiritPassagePayload(int ticksLeft, int releasePercent, int releaseTicksLeft) implements CustomPacketPayload {
    public static final Type<SyncSpiritPassagePayload> TYPE = new Type<>(TensuraFragments.id("sync_spirit_passage"));
    public static final StreamCodec<ByteBuf, SyncSpiritPassagePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SyncSpiritPassagePayload::ticksLeft,
            ByteBufCodecs.VAR_INT, SyncSpiritPassagePayload::releasePercent,
            ByteBufCodecs.VAR_INT, SyncSpiritPassagePayload::releaseTicksLeft,
            SyncSpiritPassagePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncSpiritPassagePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SpiritPassageHud.sync(payload.ticksLeft(), payload.releasePercent(), payload.releaseTicksLeft()));
    }
}
