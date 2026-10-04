package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.rune.RuneClientHooks;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The runes this player knows (or all of them, with Rune Magic), for the codex. */
public record SyncKnownRunesPayload(List<String> known, boolean all) implements CustomPacketPayload {
    public static final Type<SyncKnownRunesPayload> TYPE = new Type<>(TensuraFragments.id("sync_known_runes"));
    public static final StreamCodec<ByteBuf, SyncKnownRunesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)), SyncKnownRunesPayload::known,
            ByteBufCodecs.BOOL, SyncKnownRunesPayload::all, SyncKnownRunesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncKnownRunesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> RuneClientHooks.setKnown(payload.known(), payload.all()));
    }
}
