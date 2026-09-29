package com.tensurafragments.shikigami;

import com.tensurafragments.Config;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Talisman material for Shikigami Control. Paper is the real thing; any leaves work as a weaker stand-in
 * ({@code leafPotency} in the config). Paper is always used first.
 */
public final class Paper {
    /** What a talisman was made from and how strong that makes it. */
    public record Talisman(ItemStack item, float potency) {
        public boolean isLeaf() {
            return !item.is(Items.PAPER);
        }
    }

    private Paper() {
    }

    public static int count(Player player) {
        return player.getInventory().countItem(Items.PAPER);
    }

    public static int countLeaves(Player player) {
        int total = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(ItemTags.LEAVES)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static boolean has(Player player) {
        return player.getAbilities().instabuild || count(player) > 0 || countLeaves(player) > 0;
    }

    /**
     * Uses one paper, or one leaf block if there's no paper. Returns what was used, or null if there was nothing.
     * Creative players use nothing and always get full-strength paper.
     */
    public static Talisman consume(Player player) {
        if (player.getAbilities().instabuild) {
            return new Talisman(new ItemStack(Items.PAPER), 1.0F);
        }
        Talisman leaf = null;
        var inventory = player.getInventory();
        int leafSlot = -1;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(Items.PAPER)) {
                stack.shrink(1);
                return new Talisman(new ItemStack(Items.PAPER), 1.0F);
            }
            if (leafSlot < 0 && stack.is(ItemTags.LEAVES)) {
                leafSlot = i;
            }
        }
        if (leafSlot >= 0) {
            ItemStack stack = inventory.getItem(leafSlot);
            leaf = new Talisman(stack.copyWithCount(1), Config.LEAF_POTENCY.get().floatValue());
            stack.shrink(1);
        }
        return leaf;
    }
}
