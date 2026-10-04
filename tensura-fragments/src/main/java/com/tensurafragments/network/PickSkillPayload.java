package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.skill.StartingSkill;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The skill a player picked to start with. */
public record PickSkillPayload(String skill) implements CustomPacketPayload {
    public static final Type<PickSkillPayload> TYPE = new Type<>(TensuraFragments.id("pick_skill"));
    public static final StreamCodec<ByteBuf, PickSkillPayload> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(PickSkillPayload::new, PickSkillPayload::skill);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PickSkillPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                StartingSkill.pick(player, payload.skill());
            }
        });
    }
}
