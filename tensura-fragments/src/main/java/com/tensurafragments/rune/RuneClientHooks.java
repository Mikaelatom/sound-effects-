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

    public static void openCanvas() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.tensurafragments.client.RuneScreens.openCanvas();
        }
    }
}
