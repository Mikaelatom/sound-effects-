package com.tensurafragments.client;

import com.tensurafragments.network.SyncDeckPayload;

/** Last deck state the server sent. Regen progress is advanced locally between syncs for a smooth bar. */
public final class ClientDeckState {
    private static int deck;
    private static int deckSize = 5;
    private static int regenTicks = 60;
    private static int regenProgress;

    private ClientDeckState() {
    }

    public static void update(SyncDeckPayload payload) {
        deck = payload.deck();
        deckSize = payload.deckSize();
        regenTicks = Math.max(1, payload.regenTicks());
        regenProgress = payload.regenProgress();
    }

    static void tick() {
        if (deck < deckSize && regenProgress < regenTicks) {
            regenProgress++;
        }
    }

    public static int deck() {
        return deck;
    }

    public static int deckSize() {
        return deckSize;
    }

    public static float regenFraction() {
        return deck >= deckSize ? 0 : Math.min(1, regenProgress / (float) regenTicks);
    }
}
