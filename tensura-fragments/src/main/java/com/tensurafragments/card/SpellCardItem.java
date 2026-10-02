package com.tensurafragments.card;

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

/** A Spell Card: right-click to throw it. It sticks like a Gambit card and casts its spell when detonated. */
public class SpellCardItem extends Item {
    private final SpellCard spell;

    public SpellCardItem(SpellCard spell, Properties properties) {
        super(properties.stacksTo(16));
        this.spell = spell;
    }

    public SpellCard spell() {
        return spell;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            SpellCards.throwCard(serverPlayer, spell);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.getCooldowns().addCooldown(this, 4);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.tensurafragments." + spell.id() + "_card.tooltip").withColor(spell.colour()));
        tooltip.add(Component.translatable("item.tensurafragments.spell_card.how").withColor(0xA0A0A0));
    }
}
