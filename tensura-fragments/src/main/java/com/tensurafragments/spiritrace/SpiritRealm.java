package com.tensurafragments.spiritrace;

import com.tensurafragments.TensuraFragments;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** The Spirit Realm: where spirits are born and return to. Its terrain and look are data (dimension/spirit_realm.json). */
public final class SpiritRealm {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, TensuraFragments.id("spirit_realm"));

    private SpiritRealm() {
    }
}
