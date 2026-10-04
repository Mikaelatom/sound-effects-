package com.tensurafragments.rune;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Rune Codex: how to draw each rune you know (every rune, with Rune Magic) and what it does. Right-click to read
 * it; sneak and right-click to draw a rune.
 */
public class RuneCodexItem extends Item {
    public RuneCodexItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // Sneak to draw (with paper on you); otherwise read it.
        if (level.isClientSide) {
            if (player.isShiftKeyDown()) {
                RuneClientHooks.openCanvas();
            } else {
                RuneClientHooks.openCodex();
            }
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.tensurafragments.rune_codex.tooltip").withColor(0xC9B47A));
    }
}
