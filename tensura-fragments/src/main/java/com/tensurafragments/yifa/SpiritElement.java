package com.tensurafragments.yifa;

import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;

/** The elemental spirits Yifa can see and command. Fire and wind are her affinity: they hit hardest. */
public enum SpiritElement {
    FIRE("fire", 0xFF7A2A),
    WIND("wind", 0xB8F5D8),
    WATER("water", 0x4FA8FF),
    EARTH("earth", 0xB08850);

    private final String id;
    private final int colour;

    SpiritElement(String id, int colour) {
        this.id = id;
        this.colour = colour;
    }

    public String id() {
        return id;
    }

    /** RGB. */
    public int colour() {
        return colour;
    }

    public DustParticleOptions dust(float size) {
        return new DustParticleOptions(new Vector3f(((colour >> 16) & 0xFF) / 255F, ((colour >> 8) & 0xFF) / 255F,
                (colour & 0xFF) / 255F), size);
    }

    public static SpiritElement byIndex(int index) {
        return values()[Math.floorMod(index, values().length)];
    }
}
