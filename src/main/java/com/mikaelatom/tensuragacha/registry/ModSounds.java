package com.mikaelatom.tensuragacha.registry;

import com.mikaelatom.tensuragacha.TensuraGacha;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, TensuraGacha.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SQUISH_POP =
            SOUNDS.register("squish_pop", () -> SoundEvent.createVariableRangeEvent(TensuraGacha.id("squish_pop")));
}
