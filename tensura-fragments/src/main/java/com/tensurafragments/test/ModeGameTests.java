package com.tensurafragments.test;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.rainbow.RainbowMagic;
import com.tensurafragments.rainbow.RainbowSpell;
import com.tensurafragments.skill.ModSkills;
import com.tensurafragments.spirit.SpiritEntity;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.network.c2s.RequestAbilityModeChangePacket;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ability.IAbility;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Tensura's "next mode" key has to reach every mode of every multi-mode skill. */
@GameTestHolder(TensuraFragments.MODID)
@PrefixGameTestTemplate(false)
public final class ModeGameTests {
    private ModeGameTests() {
    }

    @GameTest(template = "platform")
    public static void nextModeKeyReachesEveryMode(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.spawn(helper, 4.5, 4.5);
        IAbility ability = TensuraStorages.getAbilityFrom(player);
        helper.assertTrue(ability != null, "player has Tensura ability data");
        List<RegistrySupplier<? extends ManasSkill>> skills = List.of(ModSkills.SHIKIGAMI_CONTROL, ModSkills.RAINBOW_MAGIC,
                ModSkills.FLAME_EMPEROR, ModSkills.SPIRIT_COMMUNION, ModSkills.SOUL_REAPER);
        StringBuilder problems = new StringBuilder();
        // Each skill in turn goes in the first slot (a preset only has three).
        int slot = 0;
        for (RegistrySupplier<? extends ManasSkill> entry : skills) {
            ManasSkill skill = entry.get();
            SkillHelper.learnSkill(player, skill);
            ability.getAbilitySlot(slot).setSkillAndMode(skill, 0);
            var instance = io.github.manasmods.manascore.skill.api.SkillAPI.getSkillsFrom(player).getSkill(skill).orElseThrow();
            int modes = instance.getModes();
            StringBuilder seen = new StringBuilder("0");
            for (int press = 1; press < modes; press++) {
                RequestAbilityModeChangePacket.changeModePacket(slot, false).changeMode(player);
                int mode = ability.getAbilitySlot(slot).getMode();
                seen.append(",").append(mode);
                if (mode != press) {
                    problems.append(entry.getId()).append(" went ").append(seen).append("; ");
                    break;
                }
            }
        }
        helper.assertTrue(problems.isEmpty(), "mode switching: " + problems);
        helper.succeed();
    }

    /** Rainbow Magic's switch (sneak + key) goes through every rainbow spell and then Rainbow Spirit. */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void rainbowSwitchReachesRainbowSpirit(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.spawn(helper, 4.5, 0.5);
        TestPlayers.giveMagicules(player, 100_000);
        SkillHelper.learnSkill(player, ModSkills.RAINBOW_MAGIC.get());
        for (int i = 0; i < RainbowSpell.values().length; i++) {
            helper.assertFalse(RainbowMagic.spiritSelected(player), "spell " + i + " first");
            RainbowMagic.cycleSpell(player);
        }
        helper.assertTrue(RainbowMagic.spiritSelected(player), "Rainbow Spirit comes after the spells");
        helper.assertTrue(RainbowMagic.cast(player), "casting it summons a rainbow spirit");
        List<SpiritEntity> spirits = helper.getLevel().getEntitiesOfClass(SpiritEntity.class,
                new AABB(player.blockPosition()).inflate(4, 3, 8));
        helper.assertTrue(spirits.size() == 1 && RainbowMagic.isRainbow(spirits.get(0)), "a rainbow spirit appeared");
        RainbowMagic.cycleSpell(player);
        helper.assertTrue(RainbowMagic.selectedSpell(player) == RainbowSpell.FIRE_BALL, "and round again to Fire Ball");
        helper.succeed();
    }
}
