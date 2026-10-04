package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.combat.CombatMode;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** From the client: toggle Combat Mode, or a down slam. */
public record CombatInputPayload(int action) implements CustomPacketPayload {
    public static final int TOGGLE = 0;
    public static final int SLAM = 1;
    public static final Type<CombatInputPayload> TYPE = new Type<>(TensuraFragments.id("combat_input"));
    public static final StreamCodec<ByteBuf, CombatInputPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(CombatInputPayload::new, CombatInputPayload::action);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CombatInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (payload.action() == TOGGLE) {
                    CombatMode.toggle(player);
                } else if (payload.action() == SLAM) {
                    CombatMode.slam(player);
                }
            }
        });
    }
}
