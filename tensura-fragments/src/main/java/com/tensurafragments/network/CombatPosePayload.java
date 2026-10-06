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
    /** Held up by someone's grab. */
    public static final int HELD = 4;
    /** Knocked down. */
    public static final int DOWN = 5;
    /** Thrown, flying through the air. */
    public static final int THROWN = 6;
    /** Stunned in mid-air (juggled). */
    public static final int JUGGLE = 7;
    public static final int SLAM_DIVE = 8;
    public static final int METEOR_DIVE = 9;
    public static final int DIVE_KICK = 10;
    public static final int TACKLE = 11;
    public static final int IRON_BODY = 12;
    public static final int COUNTER = 13;
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
