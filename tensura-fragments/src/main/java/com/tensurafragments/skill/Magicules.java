package com.tensurafragments.skill;

import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.server.level.ServerPlayer;

/** Direct access to Tensura's magicule pool, for costs that depend on what the skill actually does. */
public final class Magicules {
    private Magicules() {
    }

    public static boolean has(ServerPlayer player, double amount) {
        IExistence existence = TensuraStorages.getExistenceFrom(player);
        return amount <= 0 || player.isCreative() || existence == null || existence.getMagicule() >= amount;
    }

    /** Spends {@code amount} if the player has it. Creative players and players without Tensura data pay nothing. */
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
