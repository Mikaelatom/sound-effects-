package com.tensurafragments.mixin;

import com.tensurafragments.client.ClientEnergy;
import com.tensurafragments.client.ClientSouls;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Energy spells glow with a green outline on your screen, Soul Reaper's souls and possessed creatures with a soul-blue
 * or violet one (only drawn, nothing changes in the world).
 */
@Mixin(Entity.class)
public abstract class EntityGlowMixin {
    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void tensurafragments$energyGlow(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide && (ClientEnergy.isEnergy(self) || ClientSouls.kind(self) >= 0)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void tensurafragments$energyGlowColour(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide && ClientEnergy.isEnergy(self)) {
            cir.setReturnValue(ClientEnergy.GLOW_COLOUR);
        } else if (self.level().isClientSide && ClientSouls.kind(self) >= 0) {
            cir.setReturnValue(ClientSouls.glowColour(self));
        }
    }
}
