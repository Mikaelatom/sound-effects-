package com.tensurafragments.skill;

import com.tensurafragments.Config;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.api.Skills;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Removes the original Tensura skills from players. This addon only uses Tensura for its races and assets;
 * every ability comes from this addon instead.
 */
public final class OriginalSkillStripper {
    private OriginalSkillStripper() {
    }

    public static void strip(ServerPlayer player) {
        if (!Config.STRIP_ORIGINAL_SKILLS.get()) {
            return;
        }
        List<? extends String> namespaces = Config.STRIPPED_NAMESPACES.get();
        Skills skills = SkillAPI.getSkillsFrom(player);

        List<ResourceLocation> toRemove = new ArrayList<>();
        for (ManasSkillInstance instance : skills.getLearnedSkills()) {
            ResourceLocation id = instance == null ? null : instance.getSkillId();
            if (id != null && namespaces.contains(id.getNamespace())) {
                toRemove.add(id);
            }
        }
        for (ResourceLocation id : toRemove) {
            skills.forgetSkill(id, Component.translatable("tensurafragments.skill.stripped"));
        }
    }
}
