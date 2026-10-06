package com.tensurafragments.combatanim;

import com.tensurafragments.combatanim.client.CombatAnimClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Syncs animations between players. Channel ids live under your mod id, so several mods can each bundle the kit. */
public final class CombatAnimPayloads {
    private CombatAnimPayloads() {}

    static void createTypes(String modId) {
        PlayC2S.TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(modId, "combat_anim"));
        PlayS2C.TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(modId, "combat_anim_seen"));
    }

    static void register(RegisterPayloadHandlersEvent event) {
        // Optional, so clients with your mod can still join servers without it (they just won't see other players animate).
        PayloadRegistrar registrar = event.registrar("1").optional();

        // A client started (or stopped) an animation on itself; show it to everyone who can see that player.
        registrar.playToServer(PlayC2S.TYPE, PlayC2S.STREAM_CODEC, (payload, context) -> {
            if (!CombatAnim.isValidName(payload.name())) return;
            if (context.player() instanceof ServerPlayer sender) {
                PacketDistributor.sendToPlayersTrackingEntity(sender, new PlayS2C(sender.getId(), payload.name()));
            }
        });

        registrar.playToClient(PlayS2C.TYPE, PlayS2C.STREAM_CODEC,
                (payload, context) -> CombatAnimClient.onPlaySeen(payload.entityId(), payload.name()));
    }

    /** Client -> server: "I started animation {@code name}" (empty = stopped). */
    public record PlayC2S(String name) implements CustomPacketPayload {
        public static Type<PlayC2S> TYPE;
        public static final StreamCodec<RegistryFriendlyByteBuf, PlayC2S> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.stringUtf8(64), PlayC2S::name, PlayC2S::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: "player {@code entityId} started animation {@code name}" (empty = stopped). */
    public record PlayS2C(int entityId, String name) implements CustomPacketPayload {
        public static Type<PlayS2C> TYPE;
        public static final StreamCodec<RegistryFriendlyByteBuf, PlayS2C> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, PlayS2C::entityId, ByteBufCodecs.stringUtf8(64), PlayS2C::name, PlayS2C::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
