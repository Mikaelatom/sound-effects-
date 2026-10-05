package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.combat.CombatClientHooks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Whether Combat Mode is on, the combo count after the last hit, the fighting style, and the down power of what was hit. */
public record SyncCombatPayload(boolean on, int combo, int style, int down) implements CustomPacketPayload {
    public static final Type<SyncCombatPayload> TYPE = new Type<>(TensuraFragments.id("sync_combat"));
    public static final StreamCodec<ByteBuf, SyncCombatPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SyncCombatPayload::on, ByteBufCodecs.VAR_INT, SyncCombatPayload::combo,
            ByteBufCodecs.VAR_INT, SyncCombatPayload::style, ByteBufCodecs.VAR_INT, SyncCombatPayload::down, SyncCombatPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncCombatPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> CombatClientHooks.sync(payload.on(), payload.combo(), payload.style(), payload.down()));
    }
}
