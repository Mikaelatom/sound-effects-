package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.rune.RuneClientHooks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server tells the client to open the rune drawing sheet. */
public record OpenRuneCanvasPayload() implements CustomPacketPayload {
    public static final Type<OpenRuneCanvasPayload> TYPE = new Type<>(TensuraFragments.id("open_rune_canvas"));
    public static final StreamCodec<ByteBuf, OpenRuneCanvasPayload> STREAM_CODEC = StreamCodec.unit(new OpenRuneCanvasPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenRuneCanvasPayload payload, IPayloadContext context) {
        context.enqueueWork(RuneClientHooks::openCanvas);
    }
}
