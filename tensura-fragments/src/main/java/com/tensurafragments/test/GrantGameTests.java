package com.tensurafragments.test;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.skill.ModSkills;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Every skill this addon grants has to actually end up on the player. */
@GameTestHolder(TensuraFragments.MODID)
@PrefixGameTestTemplate(false)
public final class GrantGameTests {
    private GrantGameTests() {
    }

    @GameTest(template = "platform")
    public static void everySkillCanBeLearned(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.spawn(helper, 4.5, 4.5);
        List<RegistrySupplier<? extends ManasSkill>> skills = List.of(ModSkills.GAMBIT_CARDS, ModSkills.SHIKIGAMI_CONTROL,
                ModSkills.SEALING_GRIMOIRE, ModSkills.RAINBOW_MAGIC, ModSkills.FLAME_EMPEROR, ModSkills.SPIRIT_CONTROL, ModSkills.SPIRIT_COMMUNION);
        StringBuilder missing = new StringBuilder();
        for (RegistrySupplier<? extends ManasSkill> skill : skills) {
            boolean learned = SkillHelper.learnSkill(player, skill.get());
            boolean has = SkillAPI.getSkillsFrom(player).getSkill(skill.getId()).isPresent();
            if (!has) {
                missing.append(skill.getId()).append(" (learnSkill returned ").append(learned).append(") ");
            }
        }
        helper.assertTrue(missing.isEmpty(), "not learned: " + missing);
        helper.succeed();
    }
}
