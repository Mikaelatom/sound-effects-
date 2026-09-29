package com.tensurafragments.client;

import com.tensurafragments.network.SyncSubstitutionPayload;

/** Substitution window / cooldown as last sent by the server, counted down locally. */
public final class ClientShikigamiState {
    private static int windowTicks;
    private static int cooldownTicks;
    private static int cooldownTotal = 1;

    private ClientShikigamiState() {
    }

    public static void update(SyncSubstitutionPayload payload) {
        windowTicks = payload.windowTicks();
        cooldownTicks = payload.cooldownTicks();
        cooldownTotal = Math.max(1, payload.cooldownTicks());
    }

    static void tick() {
        if (windowTicks > 0) {
            windowTicks--;
        }
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }
    }

    public static boolean isWindowOpen() {
        return windowTicks > 0;
    }

    /** 1 right after going on cooldown, 0 when ready. */
    public static float cooldownFraction() {
        return cooldownTicks <= 0 ? 0 : cooldownTicks / (float) cooldownTotal;
    }
}
