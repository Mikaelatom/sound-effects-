package com.tensurafragments.network;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.combat.CombatClientHooks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A solid white arc for the client to draw (Combat Mode's swishes, guards and slam rings): the points along it, how
 * thick it is, how many ticks it lasts, and whether it sweeps along from its first point to its last.
 */
public record ArcPayload(List<Vec3> points, float thickness, int life, boolean sweep) implements CustomPacketPayload {
    public static final Type<ArcPayload> TYPE = new Type<>(TensuraFragments.id("arc"));
    public static final StreamCodec<FriendlyByteBuf, ArcPayload> STREAM_CODEC = StreamCodec.ofMember(ArcPayload::write,
            ArcPayload::read);

    private void write(FriendlyByteBuf buf) {
        Vec3 origin = points.get(0);
        buf.writeDouble(origin.x);
        buf.writeDouble(origin.y);
        buf.writeDouble(origin.z);
        buf.writeVarInt(points.size() - 1);
        for (int i = 1; i < points.size(); i++) {
            Vec3 p = points.get(i).subtract(origin);
            buf.writeFloat((float) p.x);
            buf.writeFloat((float) p.y);
            buf.writeFloat((float) p.z);
        }
        buf.writeFloat(thickness);
        buf.writeVarInt(life);
        buf.writeBoolean(sweep);
    }

    private static ArcPayload read(FriendlyByteBuf buf) {
        Vec3 origin = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        int count = Math.min(buf.readVarInt(), 256);
        List<Vec3> points = new ArrayList<>(count + 1);
        points.add(origin);
        for (int i = 0; i < count; i++) {
            points.add(origin.add(buf.readFloat(), buf.readFloat(), buf.readFloat()));
        }
        return new ArcPayload(points, buf.readFloat(), buf.readVarInt(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ArcPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> CombatClientHooks.arc(payload));
    }
}
