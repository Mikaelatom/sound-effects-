package com.tensurafragments.spiritrace;

import com.mojang.datafixers.util.Pair;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.skill.ModSkills;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.race.api.ManasRace;
import io.github.manasmods.manascore.race.api.ManasRaceInstance;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.TensuraSkill;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.config.race.DaemonConfig;
import io.github.manasmods.tensura.config.race.RaceConfig;
import io.github.manasmods.tensura.race.template.DefaultRace;
import io.github.manasmods.tensura.race.template.EvolutionRequirement;
import io.github.manasmods.tensura.registry.skill.IntrinsicSkills;
import io.github.manasmods.tensura.registry.skill.ResistanceSkills;
import io.github.manasmods.tensura.storage.Alignment;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Spirit race line: Lesser Spirit, Greater Spirit, an element spirit of your choice, then Heroic Spirit (awakened
 * as a True Hero) or Demonic Spirit (awakened as a True Demon Lord). Spirits live in the Spirit Realm, carry Tensura's
 * Possession skill, and cross into the material world as bodiless spirits that have to take a body to stay (see
 * {@link SpiritPassage}).
 */
public class SpiritRace extends DefaultRace {
    public enum Stage {
        LESSER, GREATER, ELEMENT, HEROIC, DEMONIC
    }

    /** The elements a Greater Spirit can become, with the Transform and resistance skill each brings. */
    public enum SpiritElement {
        FLAME(Element.FLAME, IntrinsicSkills.FLAME_TRANSFORM, ResistanceSkills.FLAME_ATTACK_RESISTANCE),
        WATER(Element.WATER, IntrinsicSkills.WATER_TRANSFORM, ResistanceSkills.WATER_ATTACK_RESISTANCE),
        WIND(Element.WIND, IntrinsicSkills.WIND_TRANSFORM, ResistanceSkills.WIND_ATTACK_RESISTANCE),
        EARTH(Element.EARTH, IntrinsicSkills.EARTH_TRANSFORM, ResistanceSkills.EARTH_ATTACK_RESISTANCE),
        LIGHT(Element.LIGHT, IntrinsicSkills.LIGHT_TRANSFORM, ResistanceSkills.LIGHT_ATTACK_RESISTANCE),
        DARKNESS(Element.DARKNESS, IntrinsicSkills.DARKNESS_TRANSFORM, ResistanceSkills.DARKNESS_ATTACK_RESISTANCE),
        SPACE(Element.SPACE, IntrinsicSkills.SPACE_TRANSFORM, ResistanceSkills.SPATIAL_ATTACK_RESISTANCE);

        private final Element element;
        private final RegistrySupplier<? extends ManasSkill> transform;
        private final RegistrySupplier<? extends ManasSkill> resistance;

        SpiritElement(Element element, RegistrySupplier<? extends ManasSkill> transform,
                      RegistrySupplier<? extends ManasSkill> resistance) {
            this.element = element;
            this.transform = transform;
            this.resistance = resistance;
        }

        public Element element() {
            return element;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        @Nullable
        public static SpiritElement byId(String id) {
            for (SpiritElement element : values()) {
                if (element.id().equals(id)) {
                    return element;
                }
            }
            return null;
        }
    }

    private final Stage stage;
    @Nullable
    private final SpiritElement element;
    private final SpiritRaceConfig config;

    public SpiritRace(Stage stage, @Nullable SpiritElement element) {
        super(switch (stage) {
            case LESSER -> Difficulty.INTERMEDIATE;
            case GREATER, ELEMENT -> Difficulty.HARD;
            case HEROIC, DEMONIC -> Difficulty.EXTREME;
        });
        this.stage = stage;
        this.element = element;
        // Base stats follow Tensura's Daemon line at the same stage.
        this.config = switch (stage) {
            case LESSER -> new SpiritRaceConfig(2000, 3000, 5000, 6000, 20, 90, 0.4, -0.5, 0.1, 0);
            case GREATER -> new SpiritRaceConfig(10000, 20000, 10000, 30000, 60, 200, 1.0, 0, 0.2, 0);
            case ELEMENT -> new SpiritRaceConfig(40000, 40000, 100000, 100000, 100, 606, 2.0, 0.2, 0.4, 0.01);
            case HEROIC, DEMONIC -> new SpiritRaceConfig(100000, 300000, 200000, 500000, 646, 3606, 3.0, 0.5, 0.6, 0.04);
        };
        applyDefaultAttributeModifiers();
    }

    public Stage stage() {
        return stage;
    }

    @Nullable
    public SpiritElement element() {
        return element;
    }

    @Override
    public RaceConfig.Default getDefaultConfig() {
        return config;
    }

    @Override
    public Alignment getAlignment() {
        // Neither good nor evil until awakened: Chaos can become either a Hero or a Demon Lord.
        return switch (stage) {
            case HEROIC -> Alignment.HOLY;
            case DEMONIC -> Alignment.MAJIN;
            default -> Alignment.CHAOS;
        };
    }

    @Override
    public Pair<ResourceKey<Level>, BlockState> getRespawnDimension(ManasRaceInstance instance, LivingEntity entity) {
        return Pair.of(SpiritRealm.KEY, Blocks.CALCITE.defaultBlockState());
    }

    // ---- Evolution ----

    @Override
    public List<ManasRace> getNextEvolutions(ManasRaceInstance instance, LivingEntity entity) {
        return switch (stage) {
            case LESSER -> List.of(SpiritRaces.GREATER.get());
            case GREATER -> SpiritRaces.ELEMENTS.values().stream().map(r -> (ManasRace) r.get()).toList();
            case ELEMENT -> List.of(SpiritRaces.HEROIC.get(), SpiritRaces.DEMONIC.get());
            case HEROIC, DEMONIC -> List.of();
        };
    }

    @Override
    public List<ManasRace> getPreviousEvolutions(ManasRaceInstance instance, LivingEntity entity) {
        return switch (stage) {
            case LESSER -> List.of();
            case GREATER -> List.of(SpiritRaces.LESSER.get());
            case ELEMENT -> List.of(SpiritRaces.GREATER.get());
            case HEROIC, DEMONIC -> SpiritRaces.ELEMENTS.values().stream().map(r -> (ManasRace) r.get()).toList();
        };
    }

    @Override
    @Nullable
    public ManasRace getDefaultEvolution(ManasRaceInstance instance, LivingEntity entity) {
        return stage == Stage.LESSER ? SpiritRaces.GREATER.get() : null;
    }

    /** Awakening as a True Hero. */
    @Override
    @Nullable
    public ManasRace getAwakeningEvolution(ManasRaceInstance instance, LivingEntity entity) {
        return stage == Stage.ELEMENT ? SpiritRaces.HEROIC.get() : null;
    }

    /** Awakening as a True Demon Lord. */
    @Override
    @Nullable
    public ManasRace getHarvestFestivalEvolution(ManasRaceInstance instance, LivingEntity entity) {
        return stage == Stage.ELEMENT ? SpiritRaces.DEMONIC.get() : null;
    }

    /** What it takes to become this race. */
    @Override
    public Map<EvolutionRequirement, Float> getEvolutionRequirements(ManasRaceInstance instance, LivingEntity entity) {
        return switch (stage) {
            case LESSER -> Map.of();
            case GREATER -> Map.of(new EvolutionRequirement.EPRequirement(20000), 100F);
            case ELEMENT -> Map.of(new EvolutionRequirement.EPRequirement(140000), 100F);
            case HEROIC, DEMONIC -> Map.of(new EvolutionRequirement.AwakenRequirement(), 100F);
        };
    }

    /** Heroic Spirit only for a True Hero, Demonic Spirit only for a True Demon Lord. */
    @Override
    public float getEvolutionProgress(ManasRaceInstance instance, LivingEntity entity, ManasRace target) {
        IExistence existence = TensuraStorages.getExistenceFrom(entity);
        if (target == SpiritRaces.HEROIC.get() && (existence == null || !existence.isTrueHero())) {
            return 0;
        }
        if (target == SpiritRaces.DEMONIC.get() && (existence == null || !existence.isTrueDemonLord())) {
            return 0;
        }
        return super.getEvolutionProgress(instance, entity, target);
    }

    // ---- Skills ----

    @Override
    public List<ManasSkill> getIntrinsicSkills(ManasRaceInstance instance, LivingEntity entity) {
        List<ManasSkill> skills = new ArrayList<>();
        skills.add(IntrinsicSkills.POSSESSION.get());
        skills.add(ResistanceSkills.MAGIC_RESISTANCE.get());
        skills.add(ModSkills.SPIRIT_RELEASE.get());
        SpiritElement own = element != null ? element : rememberedElement(entity);
        if (own != null && stage != Stage.LESSER && stage != Stage.GREATER) {
            skills.add(own.transform.get());
            skills.add(own.resistance.get());
        }
        switch (stage) {
            case HEROIC -> {
                skills.add(ResistanceSkills.SPIRITUAL_ATTACK_RESISTANCE.get());
                skills.add(ResistanceSkills.HOLY_ATTACK_NULLIFICATION.get());
                skills.add(IntrinsicSkills.DIVINE_KI_RELEASE.get());
            }
            case DEMONIC -> {
                skills.add(ResistanceSkills.SPIRITUAL_ATTACK_RESISTANCE.get());
                skills.add(ResistanceSkills.DARKNESS_ATTACK_NULLIFICATION.get());
                skills.add(IntrinsicSkills.DRAIN.get());
            }
            default -> {
            }
        }
        return skills;
    }

    /** Spirits learn magic as they grow, the same spells as Tensura's daemons at each stage. */
    @Override
    public List<TensuraSkill> getIntrinsicLearnable(ManasRaceInstance instance, LivingEntity entity) {
        DaemonConfig daemons = ConfigRegistry.getConfig(DaemonConfig.class);
        List<String> ids = switch (stage) {
            case LESSER -> daemons.LesserDaemon.learnableMagics;
            case GREATER -> daemons.GreaterDaemon.learnableMagics;
            case ELEMENT -> daemons.ArchDaemon.learnableMagics;
            case HEROIC, DEMONIC -> daemons.DaemonLord.learnableMagics;
        };
        return ids.stream().map(id -> SkillAPI.getSkillRegistry().get(ResourceLocation.parse(id)))
                .filter(Objects::nonNull).filter(TensuraSkill.class::isInstance).map(TensuraSkill.class::cast).toList();
    }

    @Override
    public void onRaceSet(ManasRaceInstance instance, LivingEntity entity) {
        super.onRaceSet(instance, entity);
        if (element != null) {
            // Remembered for the Heroic or Demonic Spirit it becomes.
            entity.setData(ModRegistries.SPIRIT_ELEMENT, element.id());
        }
        if (entity instanceof Player player && !player.level().isClientSide) {
            SpiritPassage.giveBook(player);
        }
    }

    @Nullable
    private static SpiritElement rememberedElement(LivingEntity entity) {
        return SpiritElement.byId(entity.getData(ModRegistries.SPIRIT_ELEMENT));
    }

    // ---- Race ability (R): toggle flight, in any form ----

    @Override
    public void onActivateAbility(ManasRaceInstance instance, LivingEntity entity) {
        if (!(entity instanceof Player player) || player.isSpectator() || player.isCreative()) {
            return;
        }
        boolean fly = !player.getAbilities().mayfly;
        player.getAbilities().mayfly = fly;
        player.getAbilities().flying = fly;
        player.onUpdateAbilities();
    }
}
