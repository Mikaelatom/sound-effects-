package com.tensurafragments.rune;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/** Opens rune screens on the client, without the common code touching client classes. */
public final class RuneClientHooks {
    private RuneClientHooks() {
    }

    public static void openCodex() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.tensurafragments.client.RuneScreens.openCodex();
        }
    }

    /** The runes the codex shows. */
    public static void setKnown(java.util.List<String> known, boolean all) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.tensurafragments.client.RuneScreens.setKnown(known, all);
        }
    }

    public static void openCanvas() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.tensurafragments.client.RuneScreens.openCanvas();
        }
    }
}
