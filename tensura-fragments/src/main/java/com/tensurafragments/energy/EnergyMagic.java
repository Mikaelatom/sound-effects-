package com.tensurafragments.energy;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.network.EnergyEntityPayload;
import com.tensurafragments.shikigami.ShikigamiControl;
import com.tensurafragments.skill.ModSkills;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.entity.projectile.TensuraFlyingProjectile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Energy Magic: Tensura's spells, cast glowing green at twice their power, paid for with experience levels (the
 * vanilla green bar) instead of magicules. Triggered through {@link EnergyMagicSkill}.
 */
public final class EnergyMagic {
    /** Entity tag marking an energy spell. */
    public static final String TAG = "tensurafragments.energy";

    private EnergyMagic() {
    }

    public static EnergySpell selectedSpell(ServerPlayer player) {
        return EnergySpell.byIndex(player.getData(ModRegistries.ENERGY_SPELL));
    }

    /** Experience levels the spell costs. */
    public static int levelCost(EnergySpell spell) {
        return Math.max(0, (int) Math.round(spell.baseLevels() * Config.ENERGY_LEVEL_COST_MULTIPLIER.get()));
    }

    public static float power() {
        return Config.ENERGY_POWER.get().floatValue();
    }

    public static boolean cast(ServerPlayer player) {
        EnergySpell spell = selectedSpell(player);
        int cost = levelCost(spell);
        if (!player.getAbilities().instabuild && player.experienceLevel < cost) {
            player.displayClientMessage(Component.translatable("tensurafragments.energy.no_levels", cost), true);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH,
                    SoundSource.PLAYERS, 0.4F, 1.8F);
            return false;
        }
        if (!player.getAbilities().instabuild) {
            player.giveExperienceLevels(-cost);
        }
        TensuraFlyingProjectile projectile = spell.create(player, power());
        mark(projectile);
        player.level().addFreshEntity(projectile);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(projectile, new EnergyEntityPayload(projectile.getId()));
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.9F, 0.6F);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT,
                SoundSource.PLAYERS, 0.5F, 1.8F);
        return true;
    }

    public static void mark(Entity entity) {
        entity.addTag(TAG);
    }

    public static boolean isEnergy(Entity entity) {
        return entity != null && entity.getTags().contains(TAG);
    }

    public static void cycleSpell(ServerPlayer player) {
        EnergySpell spell = selectedSpell(player).next();
        player.setData(ModRegistries.ENERGY_SPELL, spell.ordinal());
        player.displayClientMessage(Component.translatable("tensurafragments.energy.selected",
                Component.translatable("tensurafragments.energy.spell." + spell.id()), levelCost(spell)).withColor(0x7CFF6A), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.5F, 1.6F);
        ShikigamiControl.sync(player);
    }

    public static boolean hasSkill(ServerPlayer player) {
        return SkillAPI.getSkillsFrom(player).getSkill(ModSkills.ENERGY_MAGIC.getId()).isPresent();
    }

    public static void grantSkill(ServerPlayer player) {
        if (Config.GRANT_ENERGY_MAGIC.get() && !hasSkill(player)) {
            SkillHelper.learnSkill(player, ModSkills.ENERGY_MAGIC.get());
        }
    }
}
