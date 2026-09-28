package com.mikaelatom.tensuragacha.race;

import com.mikaelatom.tensuragacha.TensuraGacha;
import com.mikaelatom.tensuragacha.registry.ModRaces;
import com.mikaelatom.tensuragacha.registry.ModSkills;
import io.github.manasmods.manascore.race.api.ManasRace;
import io.github.manasmods.manascore.race.api.ManasRaceInstance;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/** Evolved Astral Slime. Stronger, luckier, and born with the Unique Skill Gambler. */
public class CelestialSlimeRace extends ManasRace {
    public CelestialSlimeRace() {
        super(Difficulty.HARD);
        addAttributeModifier(Attributes.FALL_DAMAGE_MULTIPLIER, TensuraGacha.id("celestial_slime_fall"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.LUCK, TensuraGacha.id("celestial_slime_luck"), 7.0, AttributeModifier.Operation.ADD_VALUE);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, TensuraGacha.id("celestial_slime_speed"), 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        addAttributeModifier(Attributes.JUMP_STRENGTH, TensuraGacha.id("celestial_slime_jump"), 0.4, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        addAttributeModifier(Attributes.MAX_HEALTH, TensuraGacha.id("celestial_slime_health"), 10.0, AttributeModifier.Operation.ADD_VALUE);
        addAttributeModifier(Attributes.ATTACK_DAMAGE, TensuraGacha.id("celestial_slime_attack"), 3.0, AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public List<ManasSkill> getIntrinsicSkills(ManasRaceInstance instance, LivingEntity entity) {
        return List.of(ModSkills.GAMBLER.get());
    }

    @Override
    public List<ManasRace> getPreviousEvolutions(ManasRaceInstance instance, LivingEntity entity) {
        return List.of(ModRaces.ASTRAL_SLIME.get());
    }
}
