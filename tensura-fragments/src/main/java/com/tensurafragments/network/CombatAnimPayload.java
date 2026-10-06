package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.combat.CombatClientHooks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A player does something with its own one-off animation (a special move, a block hit...), for everyone watching. */
public record CombatAnimPayload(int entityId, String animation) implements CustomPacketPayload {
    public static final String UPPERCUT = "punch_uppercut_left";
    public static final Type<CombatAnimPayload> TYPE = new Type<>(TensuraFragments.id("combat_anim"));
    public static final StreamCodec<ByteBuf, CombatAnimPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CombatAnimPayload::entityId, ByteBufCodecs.STRING_UTF8, CombatAnimPayload::animation,
            CombatAnimPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CombatAnimPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> CombatClientHooks.animation(payload.entityId(), payload.animation()));
    }
}
