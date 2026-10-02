package com.tensurafragments.client;

import com.tensurafragments.network.SyncDeckPayload;

/** Last deck state the server sent. Regen progress is advanced locally between syncs for a smooth bar. */
public final class ClientDeckState {
    private static int deck;
    private static int deckSize = 5;
    private static int regenTicks = 60;
    private static int regenProgress;
    private static int spellCard;

    private ClientDeckState() {
    }

    public static void update(SyncDeckPayload payload) {
        deck = payload.deck();
        deckSize = payload.deckSize();
        regenTicks = Math.max(1, payload.regenTicks());
        regenProgress = payload.regenProgress();
        spellCard = payload.spellCard();
    }

    static void tick() {
        if (deck < deckSize && regenProgress < regenTicks) {
            regenProgress++;
        }
    }

    /** The spell Gambit Cards' Inscribe mode makes. */
    public static com.tensurafragments.card.SpellCard spellCard() {
        var spell = com.tensurafragments.card.SpellCard.byIndex(spellCard);
        return spell == null ? com.tensurafragments.card.SpellCard.FLAME : spell;
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
