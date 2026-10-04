package com.tensurafragments.rune;

import com.tensurafragments.ModRegistries;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** A Rune Tome, found in chests around the world. Read it (right-click) to learn its rune for good. */
public class RuneTomeItem extends Item {
    public RuneTomeItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Nullable
    public static Rune runeOf(ItemStack stack) {
        String id = stack.get(ModRegistries.RUNE.get());
        return id == null ? null : Rune.byId(id);
    }

    public static ItemStack of(Rune rune) {
        ItemStack stack = new ItemStack(ModRegistries.RUNE_TOME.get());
        stack.set(ModRegistries.RUNE.get(), rune.id());
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        Rune rune = runeOf(stack);
        return rune == null ? super.getName(stack)
                : Component.translatable("item.tensurafragments.rune_tome.named", Component.translatable(rune.translationKey()))
                        .withColor(rune.colour());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.tensurafragments.rune_tome.tooltip").withColor(0xC9B47A));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Rune rune = runeOf(stack);
        if (rune == null) {
            return InteractionResultHolder.pass(stack);
        }
        if (player instanceof ServerPlayer serverPlayer && RuneMagic.learn(serverPlayer, rune)) {
            stack.consume(1, player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
