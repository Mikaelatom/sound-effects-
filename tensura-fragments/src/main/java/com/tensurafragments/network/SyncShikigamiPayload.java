package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.client.ClientShikigamiState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server tells the client the Shikigami Control state for the HUD: Substitution on/off, its cooldown, the selected spell. */
public record SyncShikigamiPayload(boolean enabled, int cooldownTicks, int spell) implements CustomPacketPayload {
    public static final Type<SyncShikigamiPayload> TYPE = new Type<>(TensuraFragments.id("sync_shikigami"));
    public static final StreamCodec<ByteBuf, SyncShikigamiPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SyncShikigamiPayload::enabled,
            ByteBufCodecs.VAR_INT, SyncShikigamiPayload::cooldownTicks,
            ByteBufCodecs.VAR_INT, SyncShikigamiPayload::spell,
            SyncShikigamiPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncShikigamiPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientShikigamiState.update(payload));
    }
}
