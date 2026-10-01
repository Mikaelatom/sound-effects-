package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.client.ClientSouls;
import com.tensurafragments.soul.CapturedSoul;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Soul Reaper's HUD: soul points, the captured souls and which one is selected. */
public record SyncSoulsPayload(int points, List<CapturedSoul> souls, int selected) implements CustomPacketPayload {
    public static final Type<SyncSoulsPayload> TYPE = new Type<>(TensuraFragments.id("sync_souls"));
    public static final StreamCodec<ByteBuf, SyncSoulsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SyncSoulsPayload::points,
            CapturedSoul.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncSoulsPayload::souls,
            ByteBufCodecs.VAR_INT, SyncSoulsPayload::selected,
            SyncSoulsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncSoulsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientSouls.sync(payload.points(), payload.souls(), payload.selected()));
    }
}
