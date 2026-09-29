package com.tensurafragments.rainbow;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.shikigami.ShikigamiControl;
import com.tensurafragments.shikigami.Spell;
import com.tensurafragments.skill.ModSkills;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Rainbow Talismans: throw the rainbow version of any talisman spell. Same shape and effect as the normal spell, drawn
 * in rainbow colours, and it strikes with every element at once. Uses paper like Shikigami Control but works on its
 * own; it has its own spell selection. Triggered through {@link RainbowTalismansSkill}.
 */
public final class RainbowTalismans {
    private RainbowTalismans() {
    }

    public static Spell selectedSpell(ServerPlayer player) {
        return Spell.byIndex(player.getData(ModRegistries.RAINBOW_SPELL));
    }

    public static boolean throwTalisman(ServerPlayer player, Vec3 momentum) {
        return ShikigamiControl.throwTalisman(player, momentum, selectedSpell(player), true);
    }

    public static void cycleSpell(ServerPlayer player) {
        Spell spell = selectedSpell(player).next();
        player.setData(ModRegistries.RAINBOW_SPELL, spell.ordinal());
        int colour = Mth.hsvToRgb(spell.ordinal() / (float) Spell.values().length, 0.6F, 1.0F);
        player.displayClientMessage(Component.translatable("tensurafragments.shikigami.spell_selected",
                Component.translatable("tensurafragments.spell.rainbow",
                        Component.translatable("tensurafragments.spell." + spell.id()))).withColor(colour), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.8F, 1.4F);
        ShikigamiControl.sync(player);
    }

    public static boolean hasSkill(ServerPlayer player) {
        return SkillAPI.getSkillsFrom(player).getSkill(ModSkills.RAINBOW_TALISMANS.getId()).isPresent();
    }

    public static void grantSkill(ServerPlayer player) {
        if (Config.GRANT_RAINBOW_TALISMANS.get() && !hasSkill(player)) {
            SkillHelper.learnSkill(player, ModSkills.RAINBOW_TALISMANS.get());
        }
    }
}
