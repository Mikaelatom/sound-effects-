package com.mikaelatom.tensuragacha.item;

import com.mikaelatom.tensuragacha.registry.ModRaces;
import com.mikaelatom.tensuragacha.registry.ModSounds;
import io.github.manasmods.manascore.race.api.RaceAPI;
import io.github.manasmods.manascore.race.api.Races;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Consuming it reincarnates the player as an Astral Slime. Sneak + use so it isn't eaten by accident. */
public class AstralCoreItem extends Item {
    public AstralCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.success(stack);

        if (!player.isShiftKeyDown()) {
            player.displayClientMessage(Component.translatable("item.tensuragacha.astral_core.sneak").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResultHolder.fail(stack);
        }

        Races races = RaceAPI.getRaceFrom(serverPlayer);
        boolean alreadyAstral = races.getRace()
                .map(instance -> instance.getRace() == ModRaces.ASTRAL_SLIME.get() || instance.getRace() == ModRaces.CELESTIAL_SLIME.get())
                .orElse(false);
        if (alreadyAstral) {
            player.displayClientMessage(Component.translatable("item.tensuragacha.astral_core.already").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }

        if (races.setRace(ModRaces.ASTRAL_SLIME.get(), false)) {
            player.sendSystemMessage(Component.translatable("item.tensuragacha.astral_core.reborn").withStyle(ChatFormatting.LIGHT_PURPLE));
            level.playSound(null, player.blockPosition(), ModSounds.SQUISH_POP.get(), SoundSource.PLAYERS, 1.0F, 0.6F);
            stack.consume(1, player);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.tensuragacha.astral_core.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
