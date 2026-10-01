package com.tensurafragments.skill;

import com.tensurafragments.Config;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.api.Skills;
import io.github.manasmods.tensura.ability.skill.Skill;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Removes Tensura's own top-tier skills (Unique and Ultimate by default) from players: this addon makes the new ones,
 * and they'll evolve into its own Ultimates. Everything else Tensura has (magic, resistances, common, extra and
 * intrinsic skills) is kept and learned the normal Tensura way.
 */
public final class OriginalSkillStripper {
    private OriginalSkillStripper() {
    }

    /** Whether this addon removes {@code skill} (Tensura's, of a stripped type). */
    public static boolean isStripped(io.github.manasmods.manascore.skill.api.ManasSkill skill, ResourceLocation id) {
        if (!Config.STRIP_ORIGINAL_SKILLS.get() || id == null || !Config.STRIPPED_NAMESPACES.get().contains(id.getNamespace())) {
            return false;
        }
        return skill instanceof Skill tensuraSkill && Config.STRIPPED_SKILL_TYPES.get().contains(tensuraSkill.getType().name());
    }

    public static void strip(ServerPlayer player) {
        if (!Config.STRIP_ORIGINAL_SKILLS.get()) {
            return;
        }
        Skills skills = SkillAPI.getSkillsFrom(player);
        List<ResourceLocation> toRemove = new ArrayList<>();
        for (ManasSkillInstance instance : skills.getLearnedSkills()) {
            if (instance != null && isStripped(instance.getSkill(), instance.getSkillId())) {
                toRemove.add(instance.getSkillId());
            }
        }
        for (ResourceLocation id : toRemove) {
            skills.forgetSkill(id, Component.translatable("tensurafragments.skill.stripped"));
        }
    }
}
