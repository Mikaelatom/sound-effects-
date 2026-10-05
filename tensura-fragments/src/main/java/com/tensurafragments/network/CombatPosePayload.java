package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.combat.CombatClientHooks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A player's Combat Mode stance (none, guard, blocking or grabbing), for everyone who can see them to draw it. */
public record CombatPosePayload(int entityId, int pose) implements CustomPacketPayload {
    public static final int NONE = 0;
    public static final int BLOCK = 1;
    public static final int GRAB = 2;
    /** Combat Mode on, nothing else going on: the boxing guard. */
    public static final int STANCE = 3;
    public static final Type<CombatPosePayload> TYPE = new Type<>(TensuraFragments.id("combat_pose"));
    public static final StreamCodec<ByteBuf, CombatPosePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CombatPosePayload::entityId, ByteBufCodecs.VAR_INT, CombatPosePayload::pose,
            CombatPosePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CombatPosePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> CombatClientHooks.pose(payload.entityId(), payload.pose()));
    }
}
