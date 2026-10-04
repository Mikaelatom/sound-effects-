package com.tensurafragments.rune;

import com.tensurafragments.ModRegistries;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * A rune drawn on paper. Right-click a creature to use it on them (or the air, on yourself). To inscribe a weapon: in
 * your inventory, right-click the paper onto the weapon (or the weapon onto the paper), or hold the weapon and use the
 * paper from your off hand.
 */
public class RunePaperItem extends Item {
    public RunePaperItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Nullable
    public static Rune runeOf(ItemStack stack) {
        String id = stack.get(ModRegistries.RUNE.get());
        return id == null ? null : Rune.byId(id);
    }

    public static ItemStack of(Rune rune) {
        ItemStack stack = new ItemStack(ModRegistries.RUNE_PAPER.get());
        stack.set(ModRegistries.RUNE.get(), rune.id());
        return stack;
    }

    /** Seconds left before a rune drawn without Rune Magic is finished (0: ready). */
    public static long secondsLeft(ItemStack stack, @Nullable Level level) {
        Long until = stack.get(ModRegistries.INSCRIBING_UNTIL.get());
        if (until == null || level == null) {
            return 0;
        }
        return Math.max(0, (until - level.getGameTime() + 19) / 20);
    }

    public static boolean isReady(ItemStack stack, @Nullable Level level) {
        return !stack.has(ModRegistries.INSCRIBING_UNTIL.get()) || secondsLeft(stack, level) <= 0;
    }

    /** Still inscribing: say how long, and do nothing. */
    private static boolean notReady(ItemStack stack, Player player) {
        if (isReady(stack, player.level())) {
            return false;
        }
        long left = secondsLeft(stack, player.level());
        player.displayClientMessage(Component.translatable("tensurafragments.rune.inscribing", left / 60,
                String.format("%02d", left % 60)), true);
        return true;
    }

    /** Finishes inscribing once its time is up. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && stack.has(ModRegistries.INSCRIBING_UNTIL.get()) && secondsLeft(stack, level) <= 0) {
            stack.remove(ModRegistries.INSCRIBING_UNTIL.get());
            Rune rune = runeOf(stack);
            if (rune != null && entity instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("tensurafragments.rune.finished",
                        Component.translatable(rune.translationKey())).withColor(rune.colour()), true);
            }
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        Rune rune = runeOf(stack);
        return rune == null ? super.getName(stack)
                : Component.translatable("item.tensurafragments.rune_paper.named", Component.translatable(rune.translationKey()))
                        .withColor(rune.colour());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return runeOf(stack) != null && !stack.has(ModRegistries.INSCRIBING_UNTIL.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Rune rune = runeOf(stack);
        if (stack.has(ModRegistries.INSCRIBING_UNTIL.get())) {
            long left = secondsLeft(stack, context.level());
            tooltip.add(Component.translatable("tensurafragments.rune.inscribing", left / 60, String.format("%02d", left % 60))
                    .withColor(0xE0B050));
        }
        if (rune != null) {
            tooltip.add(Component.translatable(rune.translationKey() + ".creature").withColor(0xCFCFCF));
            tooltip.add(Component.translatable(rune.translationKey() + ".weapon").withColor(0xCFCFCF));
        }
        tooltip.add(Component.translatable("item.tensurafragments.rune_paper.tooltip").withColor(0x8F8F8F));
    }

    /** On a creature. */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        Rune rune = runeOf(stack);
        if (rune == null) {
            return InteractionResult.PASS;
        }
        if (notReady(stack, player)) {
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            RuneMagic.useOn(serverPlayer, target, rune);
            stack.consume(1, player);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    /** On yourself, or (from the off hand) on the weapon in your main hand. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Rune rune = runeOf(stack);
        if (rune == null) {
            return InteractionResultHolder.pass(stack);
        }
        if (notReady(stack, player)) {
            return InteractionResultHolder.fail(stack);
        }
        ItemStack weapon = player.getMainHandItem();
        if (hand == InteractionHand.OFF_HAND && RuneMagic.canInscribe(weapon)) {
            RuneMagic.inscribe(weapon, rune);
            player.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.8F, 1.3F);
            stack.consume(1, player);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            RuneMagic.useOn(serverPlayer, serverPlayer, rune);
            stack.consume(1, player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Carrying the rune paper, right-click a weapon in the inventory. */
    @Override
    public boolean overrideStackedOnOther(ItemStack paper, Slot slot, ClickAction action, Player player) {
        Rune rune = runeOf(paper);
        ItemStack weapon = slot.getItem();
        if (action != ClickAction.SECONDARY || rune == null || !isReady(paper, player.level()) || !RuneMagic.canInscribe(weapon) || !slot.allowModification(player)) {
            return false;
        }
        RuneMagic.inscribe(weapon, rune);
        slot.setChanged();
        paper.shrink(1);
        player.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.8F, 1.3F);
        return true;
    }

    /** Carrying a weapon, right-click the rune paper in the inventory. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack paper, ItemStack weapon, Slot slot, ClickAction action, Player player,
                                            SlotAccess carried) {
        Rune rune = runeOf(paper);
        if (action != ClickAction.SECONDARY || rune == null || !isReady(paper, player.level()) || !RuneMagic.canInscribe(weapon) || !slot.allowModification(player)) {
            return false;
        }
        RuneMagic.inscribe(weapon, rune);
        paper.shrink(1);
        slot.setChanged();
        player.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.8F, 1.3F);
        return true;
    }
}
