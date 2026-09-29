package com.tensurafragments.flame;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.shikigami.ShikigamiControl;
import com.tensurafragments.skill.Magicules;
import com.tensurafragments.skill.ModSkills;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.entity.projectile.TensuraFlyingProjectile;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * Flame Emperor: every kind of Tensura fire magic, from Fire Bolt up to Hell Flare, plus the ultimate, Draconic
 * Hell Storm. Triggered through {@link FlameEmperorSkill}.
 */
public final class FlameEmperor {
    private FlameEmperor() {
    }

    public static FireSpell selectedSpell(ServerPlayer player) {
        return FireSpell.byIndex(player.getData(ModRegistries.FIRE_SPELL));
    }

    public static double magiculeCost(FireSpell spell) {
        return spell.magiculeCost() * Config.FIRE_MAGIC_COST_MULTIPLIER.get();
    }

    public static boolean castFire(ServerPlayer player) {
        FireSpell spell = selectedSpell(player);
        if (!Magicules.trySpend(player, magiculeCost(spell))) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.no_magicules"), true);
            return false;
        }
        TensuraFlyingProjectile projectile = spell.create(player);
        player.level().addFreshEntity(projectile);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), TensuraSoundEvents.CAST_FIRE.get(),
                SoundSource.PLAYERS, 0.8F, 1.0F);
        return true;
    }

    public static void cycleSpell(ServerPlayer player) {
        FireSpell spell = selectedSpell(player).next();
        player.setData(ModRegistries.FIRE_SPELL, spell.ordinal());
        player.displayClientMessage(Component.translatable("tensurafragments.flame.selected",
                Component.translatable("tensurafragments.flame.spell." + spell.id())).withColor(0xFF7A30), true);
        ShikigamiControl.sync(player);
    }

    /** Opens the magic circle on the caster's hand; the storm follows once it's charged. */
    public static boolean hellStorm(ServerPlayer player) {
        if (!Magicules.trySpend(player, Config.HELL_STORM_MAGICULE_COST.get())) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.no_magicules"), true);
            return false;
        }
        player.level().addFreshEntity(HellCircleEntity.create(player));
        return true;
    }

    public static boolean hasSkill(ServerPlayer player) {
        return SkillAPI.getSkillsFrom(player).getSkill(ModSkills.FLAME_EMPEROR.getId()).isPresent();
    }

    public static void grantSkill(ServerPlayer player) {
        if (Config.GRANT_FLAME_EMPEROR.get() && !hasSkill(player)) {
            SkillHelper.learnSkill(player, ModSkills.FLAME_EMPEROR.get());
        }
    }
}
