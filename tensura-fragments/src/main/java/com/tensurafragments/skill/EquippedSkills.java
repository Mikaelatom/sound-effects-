package com.tensurafragments.skill;

import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ability.IAbility;
import net.minecraft.world.entity.LivingEntity;

/** Whether a skill is on the player's active Tensura skill preset (the slots bound to their skill keys). */
public final class EquippedSkills {
    private EquippedSkills() {
    }

    public static boolean isEquipped(LivingEntity entity, ManasSkill skill) {
        IAbility ability = TensuraStorages.getAbilityFrom(entity);
        return ability != null && ability.isAbilityInActivePreset(skill);
    }
}
