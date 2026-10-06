package com.tensurafragments.combatanim;

import com.tensurafragments.combatanim.client.CombatAnimClient;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.regex.Pattern;

/**
 * Combat animation kit: 41 baked player animations you can play on any player.
 *
 * <p>Setup: call {@link #init} once from your mod's constructor. Then from anywhere:
 * <pre>{@code
 * CombatAnim.play(player, "ki_blast");
 * CombatAnim.stop(player);
 * }</pre>
 *
 * <ul>
 *   <li>Server side: plays on that player for everyone who can see them, including the player.</li>
 *   <li>Client side: plays right away on that client. For the local player it also tells the server,
 *       so other players see it.</li>
 * </ul>
 *
 * <p>The punch animations also show the swish trails modelled in Blockbench (third person).
 *
 * <p>Each animation has a built-in ending ({@link CombatAnimations.Mode}). "once" blends back to normal
 * by itself, "hold" stays on its last frame and "loop" repeats. Hold and loop keep going until you call
 * {@link #stop} or play another animation. Playing an animation replaces whatever the player was playing.
 */
public final class CombatAnim {
    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_]{1,64}");

    static String modId;

    private CombatAnim() {}

    /** Call once from your @Mod constructor. {@code modId} is your mod id: animations load from assets/&lt;modId&gt;/combat_animations/. */
    public static void init(IEventBus modBus, String modId) {
        CombatAnim.modId = modId;
        CombatAnimPayloads.createTypes(modId);
        modBus.addListener(CombatAnimPayloads::register);
        if (FMLEnvironment.dist.isClient()) {
            CombatAnimClient.init(modBus);
        }
    }

    /** Your mod id, as passed to {@link #init}. */
    public static String modId() {
        if (modId == null) throw new IllegalStateException("CombatAnim.init(modBus, modId) was never called");
        return modId;
    }

    /** Play an animation on this player. Unknown names are ignored on the client. */
    public static void play(Player player, String name) {
        if (!VALID_NAME.matcher(name).matches()) throw new IllegalArgumentException("Bad animation name: " + name);
        send(player, name);
    }

    /** Blend this player back to the normal pose. */
    public static void stop(Player player) {
        send(player, "");
    }

    private static void send(Player player, String name) {
        if (player.level().isClientSide()) {
            CombatAnimClient.playLocal(player, name, true);
        } else if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, new CombatAnimPayloads.PlayS2C(serverPlayer.getId(), name));
        }
    }

    /** True for a well-formed animation name, or the empty string (which means "stop"). */
    public static boolean isValidName(String name) {
        return name.isEmpty() || VALID_NAME.matcher(name).matches();
    }
}
