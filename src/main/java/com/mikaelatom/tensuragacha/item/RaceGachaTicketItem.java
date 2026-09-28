package com.mikaelatom.tensuragacha.item;

import com.mikaelatom.tensuragacha.gacha.RaceGachaRoller;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Rolls a new race. Sneak + use, because it replaces your current race. */
public class RaceGachaTicketItem extends Item {
    public RaceGachaTicketItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.success(stack);

        if (!player.isShiftKeyDown()) {
            player.displayClientMessage(Component.translatable("item.tensuragacha.race_gacha_ticket.sneak").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResultHolder.fail(stack);
        }

        if (RaceGachaRoller.roll(serverPlayer)) {
            stack.consume(1, player);
            player.getCooldowns().addCooldown(this, 40);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.tensuragacha.race_gacha_ticket.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.tensuragacha.race_gacha_ticket.warning").withStyle(ChatFormatting.RED));
    }
}
