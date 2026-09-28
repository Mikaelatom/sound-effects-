package com.mikaelatom.tensuragacha.race;

import com.mikaelatom.tensuragacha.TensuraGacha;
import com.mikaelatom.tensuragacha.registry.ModRaces;
import io.github.manasmods.manascore.race.api.ManasRace;
import io.github.manasmods.manascore.race.api.ManasRaceInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * A slime born from a lucky soul. Bouncy (no fall damage), quick, and very lucky,
 * which raises Soul Gacha jackpot odds. Evolves into a Celestial Slime at XP level 30.
 */
public class AstralSlimeRace extends ManasRace {
    public static final int EVOLUTION_LEVEL = 30;

    public AstralSlimeRace() {
        super(Difficulty.INTERMEDIATE);
        addAttributeModifier(Attributes.FALL_DAMAGE_MULTIPLIER, TensuraGacha.id("astral_slime_fall"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.LUCK, TensuraGacha.id("astral_slime_luck"), 3.0, AttributeModifier.Operation.ADD_VALUE);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, TensuraGacha.id("astral_slime_speed"), 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        addAttributeModifier(Attributes.JUMP_STRENGTH, TensuraGacha.id("astral_slime_jump"), 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        addAttributeModifier(Attributes.MAX_HEALTH, TensuraGacha.id("astral_slime_health"), -4.0, AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public List<ManasRace> getNextEvolutions(ManasRaceInstance instance, LivingEntity entity) {
        return List.of(ModRaces.CELESTIAL_SLIME.get());
    }

    @Override
    public ManasRace getDefaultEvolution(ManasRaceInstance instance, LivingEntity entity) {
        return ModRaces.CELESTIAL_SLIME.get();
    }

    @Override
    public float getEvolutionProgress(ManasRaceInstance instance, LivingEntity entity, ManasRace evolution) {
        if (!(entity instanceof Player player)) return 0;
        return Math.min(1.0F, player.experienceLevel / (float) EVOLUTION_LEVEL);
    }
}
