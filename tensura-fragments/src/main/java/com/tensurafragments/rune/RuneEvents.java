package com.tensurafragments.rune;

import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Rune weapons striking, and the codex for new Rune Magic users. */
@EventBusSubscriber(modid = TensuraFragments.MODID)
public final class RuneEvents {
    private RuneEvents() {
    }

    /** A melee hit with a rune weapon. */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (event.getSource().getDirectEntity() instanceof LivingEntity attacker && attacker == event.getSource().getEntity()
                && !attacker.level().isClientSide && attacker != event.getEntity()) {
            ItemStack weapon = attacker.getMainHandItem();
            if (weapon.has(ModRegistries.WEAPON_RUNE.get())) {
                RuneMagic.onWeaponHit(attacker, event.getEntity(), weapon, event.getNewDamage());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 40 == 0) {
            RuneMagic.giveCodex(player);
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        WeaponRune inscribed = event.getItemStack().get(ModRegistries.WEAPON_RUNE.get());
        Rune rune = inscribed == null ? null : inscribed.rune();
        if (rune != null) {
            event.getToolTip().add(1, Component.translatable("tensurafragments.rune.inscribed",
                    Component.translatable(rune.translationKey()), inscribed.charges()).withColor(rune.colour()));
        }
    }
}
