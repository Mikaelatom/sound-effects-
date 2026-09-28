package com.mikaelatom.tensuragacha.registry;

import com.mikaelatom.tensuragacha.TensuraGacha;
import com.mikaelatom.tensuragacha.skill.GamblerSkill;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSkills {
    public static final DeferredRegister<ManasSkill> SKILLS =
            DeferredRegister.create(SkillAPI.getSkillRegistryKey(), TensuraGacha.MODID);

    public static final DeferredHolder<ManasSkill, GamblerSkill> GAMBLER =
            SKILLS.register("gambler", GamblerSkill::new);
}
