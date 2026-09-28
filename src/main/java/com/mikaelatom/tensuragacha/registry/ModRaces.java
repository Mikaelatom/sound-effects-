package com.mikaelatom.tensuragacha.registry;

import com.mikaelatom.tensuragacha.TensuraGacha;
import com.mikaelatom.tensuragacha.race.AstralSlimeRace;
import com.mikaelatom.tensuragacha.race.CelestialSlimeRace;
import io.github.manasmods.manascore.race.api.ManasRace;
import io.github.manasmods.manascore.race.api.RaceAPI;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModRaces {
    public static final DeferredRegister<ManasRace> RACES =
            DeferredRegister.create(RaceAPI.getRaceRegistryKey(), TensuraGacha.MODID);

    public static final DeferredHolder<ManasRace, AstralSlimeRace> ASTRAL_SLIME =
            RACES.register("astral_slime", AstralSlimeRace::new);

    public static final DeferredHolder<ManasRace, CelestialSlimeRace> CELESTIAL_SLIME =
            RACES.register("celestial_slime", CelestialSlimeRace::new);
}
