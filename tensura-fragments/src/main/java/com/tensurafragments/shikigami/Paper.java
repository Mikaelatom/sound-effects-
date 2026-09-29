package com.tensurafragments.shikigami;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Paper is the ammo for Shikigami Control: talismans, barrier anchors and substitution dolls. */
public final class Paper {
    private Paper() {
    }

    public static int count(Player player) {
        return player.getInventory().countItem(Items.PAPER);
    }

    public static boolean has(Player player) {
        return player.getAbilities().instabuild || count(player) > 0;
    }

    /** Uses one paper. Creative players don't use any. */
    public static boolean consume(Player player) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(Items.PAPER)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }
}
