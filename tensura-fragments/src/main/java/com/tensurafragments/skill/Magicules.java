package com.tensurafragments.skill;

import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.server.level.ServerPlayer;

/** Bridge to Tensura's magicule pool, which is the cost gauge for every skill in this addon. */
public final class Magicules {
    private Magicules() {
    }

    /** Spends {@code amount} magicules if the player has them. Players without Tensura data cast for free. */
    public static boolean trySpend(ServerPlayer player, double amount) {
        if (amount <= 0 || player.isCreative()) {
            return true;
        }
        IExistence existence = TensuraStorages.getExistenceFrom(player);
        if (existence == null) {
            return true;
        }
        double current = existence.getMagicule();
        if (current < amount) {
            return false;
        }
        existence.setMagicule(current - amount);
        return true;
    }
}
