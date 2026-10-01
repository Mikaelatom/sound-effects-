package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.client.ClientShikigamiState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server tells the client the Shikigami Control state for the HUD: Substitution on/off, its cooldown, the selected spell, rainbow spell, fire spell, paper beast and energy spell. */
public record SyncShikigamiPayload(boolean enabled, int cooldownTicks, int spell, int rainbowSpell, int fireSpell, int beast, int energySpell) implements CustomPacketPayload {
    public static final Type<SyncShikigamiPayload> TYPE = new Type<>(TensuraFragments.id("sync_shikigami"));
    // Written by hand: more fields than StreamCodec.composite takes.
    public static final StreamCodec<ByteBuf, SyncShikigamiPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.BOOL.encode(buf, p.enabled());
                ByteBufCodecs.VAR_INT.encode(buf, p.cooldownTicks());
                ByteBufCodecs.VAR_INT.encode(buf, p.spell());
                ByteBufCodecs.VAR_INT.encode(buf, p.rainbowSpell());
                ByteBufCodecs.VAR_INT.encode(buf, p.fireSpell());
                ByteBufCodecs.VAR_INT.encode(buf, p.beast());
                ByteBufCodecs.VAR_INT.encode(buf, p.energySpell());
            },
            buf -> new SyncShikigamiPayload(ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncShikigamiPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientShikigamiState.update(payload));
    }
}
