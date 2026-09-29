package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.shikigami.PaperBeasts;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The client steering the paper beast it sees through: movement keys, aim and attack, once a tick. */
public record BeastInputPayload(float forward, float strafe, byte keys, float yaw, float pitch) implements CustomPacketPayload {
    public static final byte JUMP = 1;
    public static final byte DOWN = 2;
    public static final byte ATTACK = 4;

    public static final Type<BeastInputPayload> TYPE = new Type<>(TensuraFragments.id("beast_input"));
    public static final StreamCodec<ByteBuf, BeastInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, BeastInputPayload::forward,
            ByteBufCodecs.FLOAT, BeastInputPayload::strafe,
            ByteBufCodecs.BYTE, BeastInputPayload::keys,
            ByteBufCodecs.FLOAT, BeastInputPayload::yaw,
            ByteBufCodecs.FLOAT, BeastInputPayload::pitch,
            BeastInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BeastInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && Float.isFinite(payload.forward())
                    && Float.isFinite(payload.strafe()) && Float.isFinite(payload.yaw()) && Float.isFinite(payload.pitch())) {
                PaperBeasts.steer(player, payload.forward(), payload.strafe(), (payload.keys() & JUMP) != 0,
                        (payload.keys() & DOWN) != 0, payload.yaw(), payload.pitch(), (payload.keys() & ATTACK) != 0);
            }
        });
    }
}
