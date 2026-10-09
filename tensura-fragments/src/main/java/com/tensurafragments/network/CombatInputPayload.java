package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.combat.CombatMode;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * From the client: toggle Combat Mode, a down slam, that the next punch is an uppercut, blocking, a grab, or a dash
 * (with its direction).
 */
public record CombatInputPayload(int action, float x, float z) implements CustomPacketPayload {
    public static final int TOGGLE = 0;
    public static final int SLAM = 1;
    /** The punch about to be thrown is an uppercut (sent just before the attack). */
    public static final int UPPERCUT = 2;
    public static final int BLOCK_START = 3;
    public static final int BLOCK_STOP = 4;
    /** Grab what you're looking at, or throw what you're holding. */
    public static final int GRAB = 5;
    public static final int DASH = 6;
    /** Next fighting style. */
    public static final int STYLE = 7;
    /** Still hovering on blasts (sent every few ticks while it lasts). */
    public static final int HOVER = 8;
    public static final Type<CombatInputPayload> TYPE = new Type<>(TensuraFragments.id("combat_input"));
    public static final StreamCodec<ByteBuf, CombatInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CombatInputPayload::action, ByteBufCodecs.FLOAT, CombatInputPayload::x,
            ByteBufCodecs.FLOAT, CombatInputPayload::z, CombatInputPayload::new);

    public CombatInputPayload(int action) {
        this(action, 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CombatInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                switch (payload.action()) {
                    case TOGGLE -> CombatMode.toggle(player);
                    case SLAM -> CombatMode.airSpecial(player);
                    case UPPERCUT -> CombatMode.markUppercut(player);
                    case BLOCK_START -> CombatMode.setBlocking(player, true);
                    case BLOCK_STOP -> CombatMode.setBlocking(player, false);
                    case GRAB -> com.tensurafragments.combat.StyleMoves.grabKey(player);
                    case DASH -> com.tensurafragments.combat.StyleMoves.dashKey(player, new Vec3(payload.x(), 0, payload.z()));
                    case STYLE -> CombatMode.cycleStyle(player);
                    case HOVER -> com.tensurafragments.combat.ExplosionMoves.hover(player);
                    default -> {
                    }
                }
            }
        });
    }
}
