package com.tensurafragments.mixin;

import io.github.manasmods.tensura.network.c2s.RequestNamingMenuPacket;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Naming spends the magicules you have, but never takes anything off your maximum. */
@Mixin(value = RequestNamingMenuPacket.class, remap = false)
public abstract class NamingCostMixin {
    @Inject(method = "shouldConsumeMax", at = @At("HEAD"), cancellable = true)
    private static void tensurafragments$keepMax(RequestNamingMenuPacket.NamingType type, Player player,
                                                 CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
