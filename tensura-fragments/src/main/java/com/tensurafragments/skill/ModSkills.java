package com.tensurafragments.skill;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.grimoire.SealingGrimoireSkill;
import com.tensurafragments.shikigami.ShikigamiControlSkill;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.impl.SkillRegistry;
import java.util.function.Supplier;

/** Skills registered into Tensura's (ManasCore's) skill registry, so they show up in Tensura's skill menu and keys. */
public final class ModSkills {
    public static final RegistrySupplier<GambitCardsSkill> GAMBIT_CARDS = register("gambit_cards", GambitCardsSkill::new);
    public static final RegistrySupplier<ShikigamiControlSkill> SHIKIGAMI_CONTROL =
            register("shikigami_control", ShikigamiControlSkill::new);
    public static final RegistrySupplier<SealingGrimoireSkill> SEALING_GRIMOIRE =
            register("sealing_grimoire", SealingGrimoireSkill::new);

    private ModSkills() {
    }

    private static <E extends ManasSkill> RegistrySupplier<E> register(String name, Supplier<E> supplier) {
        return SkillRegistry.SKILLS.register(TensuraFragments.id(name), supplier);
    }

    /** Loads this class so the skills above are registered. Call from the mod constructor. */
    public static void init() {
    }
}
