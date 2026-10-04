package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.rune.RuneMagic;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The lines a player drew on the rune sheet (as segment numbers, see {@code Rune.segment}). */
public record DrawRunePayload(List<Integer> segments) implements CustomPacketPayload {
    public static final Type<DrawRunePayload> TYPE = new Type<>(TensuraFragments.id("draw_rune"));
    public static final StreamCodec<ByteBuf, DrawRunePayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64)).map(DrawRunePayload::new, DrawRunePayload::segments);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DrawRunePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                RuneMagic.finishDrawing(player, payload.segments());
            }
        });
    }
}
