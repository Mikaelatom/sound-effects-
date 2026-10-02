package com.tensurafragments.mixin;

import com.tensurafragments.spiritrace.SpiritRaces;
import io.github.manasmods.manascore.race.api.ManasRace;
import io.github.manasmods.tensura.menu.ReincarnationMenu;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Puts Lesser Spirit on Tensura's race selection menu. The menu reads its list from Tensura's config on whichever side
 * is asking (the screen asks on the client), so the race is added to the list it returns rather than to the config.
 */
@Mixin(value = ReincarnationMenu.class, remap = false)
public abstract class ReincarnationMenuMixin {
    @Shadow
    private int racePool;

    @Inject(method = "getRacePool", at = @At("RETURN"), cancellable = true)
    private void tensurafragments$addSpirit(CallbackInfoReturnable<List<ManasRace>> cir) {
        // Pool 0 is the first-spawn menu (1 and 2 are after the Reincarnation spell).
        if (racePool != 0 || !SpiritRaces.inRaceMenu()) {
            return;
        }
        ManasRace spirit = SpiritRaces.LESSER.get();
        if (!cir.getReturnValue().contains(spirit)) {
            List<ManasRace> races = new ArrayList<>(cir.getReturnValue());
            races.add(spirit);
            cir.setReturnValue(races);
        }
    }
}
