package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.client.ClientEnergy;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Tells a client that an entity is an energy spell, so it gets drawn glowing green. */
public record EnergyEntityPayload(int entityId) implements CustomPacketPayload {
    public static final Type<EnergyEntityPayload> TYPE = new Type<>(TensuraFragments.id("energy_entity"));
    public static final StreamCodec<ByteBuf, EnergyEntityPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(EnergyEntityPayload::new, EnergyEntityPayload::entityId);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EnergyEntityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientEnergy.add(payload.entityId()));
    }
}
