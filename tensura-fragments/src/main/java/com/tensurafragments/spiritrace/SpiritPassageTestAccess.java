package com.tensurafragments.spiritrace;

import net.minecraft.server.level.ServerPlayer;

/** Lets the GameTests drive the spirit's clock directly. */
public final class SpiritPassageTestAccess {
    private SpiritPassageTestAccess() {
    }

    public static void tick(ServerPlayer player) {
        SpiritPassage.tick(player);
    }
}
