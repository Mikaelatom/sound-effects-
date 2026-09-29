package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.client.ClientShikigamiState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server tells the client whether Substitution is on and how long its cooldown has left, for the HUD. */
public record SyncSubstitutionPayload(boolean enabled, int cooldownTicks) implements CustomPacketPayload {
    public static final Type<SyncSubstitutionPayload> TYPE = new Type<>(TensuraFragments.id("sync_substitution"));
    public static final StreamCodec<ByteBuf, SyncSubstitutionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SyncSubstitutionPayload::enabled,
            ByteBufCodecs.VAR_INT, SyncSubstitutionPayload::cooldownTicks,
            SyncSubstitutionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncSubstitutionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientShikigamiState.update(payload));
    }
}
