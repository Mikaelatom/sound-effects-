package com.mikaelatom.tensuragacha.shop;

import com.mikaelatom.tensuragacha.GachaConfig;
import com.mikaelatom.tensuragacha.registry.ModAttachments;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;

/**
 * /shop                  open the Soul Market (if allowShopCommand is on)
 * /shop search <text>    open it showing only matching items
 * /shop sell             sell the stack in your main hand
 * /shop price            show the price of the item in your main hand
 * /shop balance          show your Soul Coins
 * /shop coins add|set <players> <amount>   (operators)
 * /shop reload           re-calculate all prices (operators)
 */
public final class ShopCommands {
    private ShopCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("shop")
                .executes(ctx -> open(ctx, null))
                .then(Commands.literal("search")
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> open(ctx, StringArgumentType.getString(ctx, "text")))))
                .then(Commands.literal("sell").executes(ShopCommands::sellHand))
                .then(Commands.literal("price").executes(ShopCommands::priceHand))
                .then(Commands.literal("balance").executes(ShopCommands::balance))
                .then(Commands.literal("coins").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("add")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .then(Commands.argument("amount", LongArgumentType.longArg())
                                                .executes(ctx -> changeCoins(ctx, false)))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                                .executes(ctx -> changeCoins(ctx, true))))))
                .then(Commands.literal("reload").requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            ItemValues.recompute(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.translatable("tensuragacha.shop.reloaded",
                                    ItemValues.shopItems().size()), true);
                            return 1;
                        })));
    }

    private static boolean commandAllowed(CommandSourceStack source) {
        if (GachaConfig.SHOP_COMMAND.get() || source.hasPermission(2)) return true;
        source.sendFailure(Component.translatable("tensuragacha.shop.command_disabled"));
        return false;
    }

    private static int open(CommandContext<CommandSourceStack> ctx, String search) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!commandAllowed(ctx.getSource())) return 0;
        ShopMenu.open(player, search);
        return 1;
    }

    private static int sellHand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!commandAllowed(ctx.getSource())) return 0;
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        long earned = ItemValues.sellPrice(stack);
        if (stack.isEmpty() || earned <= 0) {
            ctx.getSource().sendFailure(Component.translatable("tensuragacha.shop.cant_sell"));
            return 0;
        }
        Component name = stack.getHoverName();
        int count = stack.getCount();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setData(ModAttachments.SOUL_COINS, player.getData(ModAttachments.SOUL_COINS) + earned);
        ctx.getSource().sendSuccess(() -> Component.translatable("tensuragacha.shop.sold", count, name,
                ShopMenu.coins(earned)).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int priceHand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (stack.isEmpty() || !ItemValues.isTradeable(stack.getItem())) {
            ctx.getSource().sendFailure(Component.translatable("tensuragacha.shop.cant_sell"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("tensuragacha.shop.price_info", stack.getHoverName(),
                ShopMenu.coins(ItemValues.buyPrice(stack.getItem())), ShopMenu.coins(ItemValues.sellPrice(stack)),
                stack.getCount()).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int balance(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ctx.getSource().sendSuccess(() -> Component.translatable("tensuragacha.shop.balance",
                ShopMenu.coins(player.getData(ModAttachments.SOUL_COINS))).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int changeCoins(CommandContext<CommandSourceStack> ctx, boolean set) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
        long amount = LongArgumentType.getLong(ctx, "amount");
        for (ServerPlayer player : players) {
            long current = player.getData(ModAttachments.SOUL_COINS);
            player.setData(ModAttachments.SOUL_COINS, Math.max(0, set ? amount : current + amount));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("tensuragacha.shop.coins_changed", players.size()), true);
        return players.size();
    }
}
