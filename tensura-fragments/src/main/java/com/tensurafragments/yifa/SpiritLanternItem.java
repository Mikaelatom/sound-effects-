package com.tensurafragments.yifa;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Magisteel Spirit Lantern: carried anywhere in your inventory, it makes twice as many spirits gather around you and
 * draws the wild ones within 16 blocks in to wait at your side. The work is done in {@link SpiritCommunion#tick}.
 */
public class SpiritLanternItem extends Item {
    public SpiritLanternItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.tensurafragments.spirit_lantern.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
