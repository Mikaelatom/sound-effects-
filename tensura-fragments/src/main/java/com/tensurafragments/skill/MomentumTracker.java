package com.tensurafragments.skill;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The server does not know a player's real velocity (the client moves itself), so it is measured here from how far
 * the player moved in the last tick.
 */
public final class MomentumTracker {
    private static final Map<ServerPlayer, Vec3> LAST_POSITION = new WeakHashMap<>();
    private static final Map<ServerPlayer, Vec3> VELOCITY = new WeakHashMap<>();

    private MomentumTracker() {
    }

    /** Call once per server tick per player. */
    static void tick(ServerPlayer player) {
        Vec3 now = player.position();
        Vec3 last = LAST_POSITION.put(player, now);
        VELOCITY.put(player, last == null ? Vec3.ZERO : now.subtract(last));
    }

    public static Vec3 velocity(ServerPlayer player) {
        return VELOCITY.getOrDefault(player, Vec3.ZERO);
    }

    /** After a teleport, so the jump itself doesn't count as speed next tick. */
    static void reset(ServerPlayer player, Vec3 velocity) {
        LAST_POSITION.put(player, player.position());
        VELOCITY.put(player, velocity);
    }
}
