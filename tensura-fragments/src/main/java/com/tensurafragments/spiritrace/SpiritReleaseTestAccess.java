package com.tensurafragments.spiritrace;

import net.minecraft.server.level.ServerPlayer;

/** Lets the GameTests drive Spirit Release's clock directly. */
public final class SpiritReleaseTestAccess {
    private SpiritReleaseTestAccess() {
    }

    public static void tick(ServerPlayer player) {
        SpiritRelease.tick(player);
    }
}
