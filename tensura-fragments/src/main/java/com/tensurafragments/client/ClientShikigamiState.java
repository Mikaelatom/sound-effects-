package com.tensurafragments.client;

import com.tensurafragments.energy.EnergySpell;
import com.tensurafragments.flame.FireSpell;
import com.tensurafragments.network.SyncShikigamiPayload;
import com.tensurafragments.rainbow.RainbowSpell;
import com.tensurafragments.shikigami.BeastKind;
import com.tensurafragments.shikigami.Spell;

/** Substitution on/off and cooldown as last sent by the server, counted down locally. */
public final class ClientShikigamiState {
    private static boolean enabled = true;
    private static int cooldownTicks;
    private static int cooldownTotal = 1;
    private static Spell spell = Spell.EXPLOSIVE;
    private static RainbowSpell rainbowSpell = RainbowSpell.FIRE_BALL;
    private static FireSpell fireSpell = FireSpell.FIRE_BOLT;
    private static BeastKind beast = BeastKind.OWL;
    private static EnergySpell energySpell = EnergySpell.FIRE_BOLT;
    /** Raw Rainbow Magic choice: a spell index, or the Rainbow Spirit slot after them. */
    private static int rainbowChoice;

    private ClientShikigamiState() {
    }

    public static void update(SyncShikigamiPayload payload) {
        enabled = payload.enabled();
        cooldownTicks = payload.cooldownTicks();
        cooldownTotal = Math.max(1, payload.cooldownTicks());
        spell = Spell.byIndex(payload.spell());
        rainbowSpell = RainbowSpell.byIndex(payload.rainbowSpell());
        rainbowChoice = payload.rainbowSpell();
        fireSpell = FireSpell.byIndex(payload.fireSpell());
        beast = BeastKind.byIndex(payload.beast());
        energySpell = EnergySpell.byIndex(payload.energySpell());
    }

    static void tick() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }
    }

    public static Spell spell() {
        return spell;
    }

    public static FireSpell fireSpell() {
        return fireSpell;
    }

    public static RainbowSpell rainbowSpell() {
        return rainbowSpell;
    }

    public static int rainbowChoice() {
        return rainbowChoice;
    }

    public static EnergySpell energySpell() {
        return energySpell;
    }

    public static BeastKind beast() {
        return beast;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** 1 right after a doll was used, 0 when ready. */
    public static float cooldownFraction() {
        return cooldownTicks <= 0 ? 0 : cooldownTicks / (float) cooldownTotal;
    }
}
