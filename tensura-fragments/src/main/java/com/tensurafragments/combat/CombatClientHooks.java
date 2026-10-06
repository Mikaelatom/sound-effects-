package com.tensurafragments.combat;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/** Passes Combat Mode state to the client, without the common code touching client classes. */
public final class CombatClientHooks {
    private CombatClientHooks() {
    }

    public static void sync(boolean on, int combo, int style, int down) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.tensurafragments.client.ClientCombat.sync(on, combo, style, down);
        }
    }

    public static void arc(com.tensurafragments.network.ArcPayload payload) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.tensurafragments.client.ArcRenderer.add(payload);
        }
    }

    public static void animation(int entityId, String animation) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.tensurafragments.client.BoxingAnimator.play(entityId, animation);
        }
    }

    public static void pose(int entityId, int pose) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.tensurafragments.client.CombatPoses.set(entityId, pose);
        }
    }
}
