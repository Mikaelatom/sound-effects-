package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.combat.CombatClientHooks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Whether Combat Mode is on, and the combo count after the last hit. */
public record SyncCombatPayload(boolean on, int combo) implements CustomPacketPayload {
    public static final Type<SyncCombatPayload> TYPE = new Type<>(TensuraFragments.id("sync_combat"));
    public static final StreamCodec<ByteBuf, SyncCombatPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SyncCombatPayload::on, ByteBufCodecs.VAR_INT, SyncCombatPayload::combo, SyncCombatPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncCombatPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> CombatClientHooks.sync(payload.on(), payload.combo()));
    }
}
