package com.tensurafragments.spiritrace;

import com.tensurafragments.Config;
import com.tensurafragments.TensuraFragments;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.race.api.ManasRaceInstance;
import io.github.manasmods.manascore.race.api.RaceAPI;
import io.github.manasmods.manascore.race.impl.RaceRegistry;
import io.github.manasmods.tensura.config.ReincarnationConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/** The Spirit races, registered into Tensura's (ManasCore's) race registry. */
public final class SpiritRaces {
    public static final RegistrySupplier<SpiritRace> LESSER =
            register("lesser_spirit", () -> new SpiritRace(SpiritRace.Stage.LESSER, null));
    public static final RegistrySupplier<SpiritRace> GREATER =
            register("greater_spirit", () -> new SpiritRace(SpiritRace.Stage.GREATER, null));
    public static final Map<SpiritRace.SpiritElement, RegistrySupplier<SpiritRace>> ELEMENTS = elements();
    public static final RegistrySupplier<SpiritRace> HEROIC =
            register("heroic_spirit", () -> new SpiritRace(SpiritRace.Stage.HEROIC, null));
    public static final RegistrySupplier<SpiritRace> DEMONIC =
            register("demonic_spirit", () -> new SpiritRace(SpiritRace.Stage.DEMONIC, null));

    private SpiritRaces() {
    }

    private static Map<SpiritRace.SpiritElement, RegistrySupplier<SpiritRace>> elements() {
        Map<SpiritRace.SpiritElement, RegistrySupplier<SpiritRace>> map = new EnumMap<>(SpiritRace.SpiritElement.class);
        for (SpiritRace.SpiritElement element : SpiritRace.SpiritElement.values()) {
            map.put(element, register(element.id() + "_spirit", () -> new SpiritRace(SpiritRace.Stage.ELEMENT, element)));
        }
        return Collections.unmodifiableMap(map);
    }

    private static RegistrySupplier<SpiritRace> register(String name, Supplier<SpiritRace> supplier) {
        return RaceRegistry.RACES.register(TensuraFragments.id(name), supplier);
    }

    /** Loads this class so the races above are registered. Call from the mod constructor. */
    public static void init() {
    }

    /** The spirit race this entity is, if any. */
    @Nullable
    public static SpiritRace raceOf(LivingEntity entity) {
        return RaceAPI.getRaceFrom(entity).getRace().map(ManasRaceInstance::getRace)
                .filter(SpiritRace.class::isInstance).map(SpiritRace.class::cast).orElse(null);
    }

    public static boolean isSpirit(LivingEntity entity) {
        return raceOf(entity) != null;
    }

    /** Puts Lesser Spirit on Tensura's race selection menu (Tensura keeps that list in its own config). */
    public static void addToRaceMenu() {
        if (!Config.SPIRIT_RACE_IN_MENU.get()) {
            return;
        }
        ReincarnationConfig config = ConfigRegistry.getConfig(ReincarnationConfig.class);
        String id = LESSER.getId().toString();
        if (config != null && config.Races != null && config.Races.startingRaces != null
                && !config.Races.startingRaces.contains(id)) {
            List<String> races = new ArrayList<>(config.Races.startingRaces);
            races.add(id);
            config.Races.startingRaces = races;
        }
    }
}
