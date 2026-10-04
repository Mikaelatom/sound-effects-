package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.skill.StartingSkillClientHooks;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server asks the client to show the starting skill choice (the skill ids to pick from). */
public record OpenSkillPickPayload(List<String> skills) implements CustomPacketPayload {
    public static final Type<OpenSkillPickPayload> TYPE = new Type<>(TensuraFragments.id("open_skill_pick"));
    public static final StreamCodec<ByteBuf, OpenSkillPickPayload> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)).map(OpenSkillPickPayload::new, OpenSkillPickPayload::skills);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenSkillPickPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> StartingSkillClientHooks.open(payload.skills()));
    }
}
