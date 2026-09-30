package com.tensurafragments.yifa;

import com.tensurafragments.Config;
import java.util.List;
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

/** Magisteel Spirit Bell: ring it and every wild spirit nearby comes flying and binds itself to you. */
public class SpiritBellItem extends Item {
    public SpiritBellItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            SpiritCommunion.ringBell(serverPlayer);
            player.getCooldowns().addCooldown(this, Config.SPIRIT_BELL_COOLDOWN_TICKS.get());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.tensurafragments.spirit_bell.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
