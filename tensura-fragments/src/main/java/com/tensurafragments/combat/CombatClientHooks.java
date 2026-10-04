package com.tensurafragments.combat;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/** Passes Combat Mode state to the client, without the common code touching client classes. */
public final class CombatClientHooks {
    private CombatClientHooks() {
    }

    public static void sync(boolean on, int combo) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.tensurafragments.client.ClientCombat.sync(on, combo);
        }
    }

    public static void pose(int entityId, int pose) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.tensurafragments.client.CombatPoses.set(entityId, pose);
        }
    }
}
