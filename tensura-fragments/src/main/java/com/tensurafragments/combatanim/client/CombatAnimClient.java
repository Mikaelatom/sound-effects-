package com.tensurafragments.combatanim.client;

import com.tensurafragments.combatanim.CombatAnim;
import com.tensurafragments.combatanim.CombatAnimPayloads;
import com.tensurafragments.combatanim.CombatAnimations;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client side of {@link CombatAnim}. Only loaded on the client. */
public final class CombatAnimClient {
    private CombatAnimClient() {}

    public static void init(IEventBus modBus) {
        SwishTrails.load();
        CombatFx.load();
        modBus.addListener(CombatAnimClient::addLayers);
        NeoForge.EVENT_BUS.addListener(CombatAnimClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(CombatFx::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(CombatAnimClient::registerCommands);
    }

    private static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new SwishTrails.Layer(renderer));
                renderer.addLayer(new CombatFx.Layer(renderer));
            }
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        CombatAnimator.tick(Minecraft.getInstance());
        CombatFx.tick(Minecraft.getInstance());
    }

    /** Play an effect clip on an entity on this client only (see CombatAnim.effect). */
    public static void effectLocal(Entity entity, String name, float yaw, int durationTicks) {
        CombatFx.trigger(entity, name, yaw, durationTicks);
    }

    public static void onEffectSeen(int entityId, String name, float yaw, int durationTicks) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Entity entity = level.getEntity(entityId);
        if (entity != null) CombatFx.trigger(entity, name, yaw, durationTicks);
    }

    /** Play (or with an empty name, stop) an animation on this player. {@code relay} also tells the server, for the local player. */
    public static void playLocal(Player player, String name, boolean relay) {
        if (name.isEmpty()) {
            CombatAnimator.stop(player);
        } else if (!CombatAnimator.play(player, name)) {
            return;
        }
        if (relay && player == Minecraft.getInstance().player) {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            if (connection != null && connection.hasChannel(CombatAnimPayloads.PlayC2S.TYPE)) {
                PacketDistributor.sendToServer(new CombatAnimPayloads.PlayC2S(name));
            }
        }
    }

    public static void onPlaySeen(int entityId, String name) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (level.getEntity(entityId) instanceof Player player) {
            playLocal(player, name, false);
        }
    }

    /** /&lt;modid&gt;_anim &lt;name&gt; previews an animation on yourself; /&lt;modid&gt;_anim stop ends it. Press F5 to watch. */
    private static void registerCommands(RegisterClientCommandsEvent event) {
        // /<modid>_fx <clip> plays an effect clip on the mob you're looking at (facing you), or on yourself.
        event.getDispatcher().register(Commands.literal(CombatAnim.modId() + "_fx")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(CombatFx.clipNames(), builder))
                        .executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            LocalPlayer player = mc.player;
                            if (player == null) return 0;
                            Entity target = mc.hitResult instanceof EntityHitResult hit ? hit.getEntity() : player;
                            CombatAnim.effect(target, StringArgumentType.getString(ctx, "name"), target == player ? null : player);
                            return 1;
                        })));
        event.getDispatcher().register(Commands.literal(CombatAnim.modId() + "_anim")
                .then(Commands.literal("stop").executes(ctx -> {
                    LocalPlayer player = Minecraft.getInstance().player;
                    if (player != null) CombatAnim.stop(player);
                    return 1;
                }))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(CombatAnimations.names(), builder))
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            LocalPlayer player = Minecraft.getInstance().player;
                            if (player == null) return 0;
                            if (CombatAnimations.get(name) == null) {
                                ctx.getSource().sendFailure(Component.literal("Unknown animation: " + name));
                                return 0;
                            }
                            CombatAnim.play(player, name);
                            return 1;
                        })));
    }
}
