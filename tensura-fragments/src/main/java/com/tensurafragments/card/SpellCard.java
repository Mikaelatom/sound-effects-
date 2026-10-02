package com.tensurafragments.card;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * The spells Gambit Cards can inscribe into Spell Cards, with what each costs to make (magicules) and the colour the
 * card glows. What they do is in {@link SpellCards}.
 */
public enum SpellCard {
    FLAME(120, 0xFF6A2A),
    FROST(120, 0x8FDFFF),
    THUNDER(200, 0xFFE45C),
    GALE(250, 0xD8FFE8),
    QUAKE(250, 0xC08A4A),
    METEOR(600, 0xB04AFF);

    private final int cost;
    private final int colour;

    SpellCard(int cost, int colour) {
        this.cost = cost;
        this.colour = colour;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Magicules to inscribe one. */
    public int cost() {
        return cost;
    }

    public int colour() {
        return colour;
    }

    public SpellCard next() {
        return values()[(ordinal() + 1) % values().length];
    }

    @Nullable
    public static SpellCard byIndex(int index) {
        return index >= 0 && index < values().length ? values()[index] : null;
    }
}
