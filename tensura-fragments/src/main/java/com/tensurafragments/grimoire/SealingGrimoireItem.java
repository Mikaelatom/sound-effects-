package com.tensurafragments.grimoire;

import com.tensurafragments.ModRegistries;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** The book everything is sealed in. Lose the book and you lose what's inside. */
public class SealingGrimoireItem extends Item {
    public SealingGrimoireItem(Properties properties) {
        super(properties);
    }

    public static GrimoireContents contents(ItemStack stack) {
        return stack.getOrDefault(ModRegistries.GRIMOIRE_CONTENTS.get(), GrimoireContents.EMPTY);
    }

    public static void setContents(ItemStack stack, GrimoireContents contents) {
        stack.set(ModRegistries.GRIMOIRE_CONTENTS.get(), contents);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return !contents(stack).isEmpty();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        GrimoireContents contents = contents(stack);
        if (contents.isEmpty()) {
            tooltip.add(Component.translatable("tensurafragments.grimoire.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        for (int i = 0; i < contents.pages().size(); i++) {
            GrimoireContents.Page page = contents.pages().get(i);
            ChatFormatting colour = page.kind() == GrimoireContents.Kind.CREATURE ? ChatFormatting.GREEN : ChatFormatting.AQUA;
            Component line = Component.literal((i == contents.selected() ? "▶ " : "  ") + page.name()).withStyle(colour);
            tooltip.add(line);
        }
    }
}
