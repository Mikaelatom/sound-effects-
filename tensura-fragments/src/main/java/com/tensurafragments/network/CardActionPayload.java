package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.skill.GambitCards;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Vector3f;

/**
 * Client asks to use a Gambit Cards action. The client's own velocity is sent along because the server does not
 * track player momentum accurately; it is clamped server side.
 */
public record CardActionPayload(int action, Vector3f velocity) implements CustomPacketPayload {
    public static final int THROW = 0;
    public static final int TELEPORT = 1;
    public static final int DETONATE_ALL = 2;
    public static final int DETONATE_AIMED = 3;

    public static final Type<CardActionPayload> TYPE = new Type<>(TensuraFragments.id("card_action"));
    public static final StreamCodec<ByteBuf, CardActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CardActionPayload::action,
            ByteBufCodecs.VECTOR3F, CardActionPayload::velocity,
            CardActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CardActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()) {
                return;
            }
            Vec3 velocity = new Vec3(payload.velocity());
            switch (payload.action()) {
                case THROW -> GambitCards.throwCard(player, velocity);
                case TELEPORT -> GambitCards.teleport(player, velocity);
                case DETONATE_ALL -> GambitCards.detonate(player, false);
                case DETONATE_AIMED -> GambitCards.detonate(player, true);
                default -> {
                }
            }
        });
    }
}
