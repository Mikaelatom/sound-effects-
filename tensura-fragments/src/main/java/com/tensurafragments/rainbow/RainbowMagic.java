package com.tensurafragments.rainbow;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.network.RainbowEntityPayload;
import com.tensurafragments.shikigami.ShikigamiControl;
import com.tensurafragments.shikigami.Spell;
import com.tensurafragments.skill.Magicules;
import com.tensurafragments.skill.ModSkills;
import com.tensurafragments.spirit.SpiritControl;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.entity.projectile.TensuraFlyingProjectile;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Rainbow Magic: cast Tensura's own spells (Fire Ball, Water Blade, Wind Blade, Lightning Lance, Stone Shot, Ice
 * Lance) recoloured in rainbow. Anything they hit takes the spell's damage plus every element at once: extra
 * damage, burning, freezing and slowness. Triggered through {@link RainbowMagicSkill}.
 */
public final class RainbowMagic {
    /** Entity tag marking a rainbow spell. Saved with the entity, so it survives reloads. */
    public static final String TAG = "tensurafragments.rainbow";

    private RainbowMagic() {
    }

    /** Choices the switch goes through: every rainbow spell, then Rainbow Spirit. */
    private static final int CHOICES = RainbowSpell.values().length + 1;

    /** The selected rainbow spell, or null when Rainbow Spirit is selected. */
    @Nullable
    public static RainbowSpell selectedSpell(ServerPlayer player) {
        return spiritSelected(player) ? null : RainbowSpell.byIndex(player.getData(ModRegistries.RAINBOW_SPELL));
    }

    /** Whether the last choice in the switch, Rainbow Spirit, is selected. */
    public static boolean spiritSelected(ServerPlayer player) {
        return isSpiritIndex(player.getData(ModRegistries.RAINBOW_SPELL));
    }

    public static boolean isSpiritIndex(int index) {
        return Math.floorMod(index, CHOICES) == RainbowSpell.values().length;
    }

    public static double magiculeCost(RainbowSpell spell) {
        return spell.baseMagiculeCost() * Config.RAINBOW_COST_MULTIPLIER.get();
    }

    public static boolean cast(ServerPlayer player) {
        RainbowSpell spell = selectedSpell(player);
        if (spell == null) {
            return SpiritControl.callRainbowAtAim(player);
        }
        if (!Magicules.trySpend(player, magiculeCost(spell))) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.no_magicules"), true);
            return false;
        }
        TensuraFlyingProjectile projectile = spell.create(player);
        mark(projectile);
        player.level().addFreshEntity(projectile);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(projectile, new RainbowEntityPayload(projectile.getId()));
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), TensuraSoundEvents.CAST_LIGHT.get(),
                SoundSource.PLAYERS, 0.8F, 1.3F);
        return true;
    }

    public static void mark(Entity entity) {
        entity.addTag(TAG);
    }

    public static boolean isRainbow(Entity entity) {
        return entity != null && entity.getTags().contains(TAG);
    }

    public static void cycleSpell(ServerPlayer player) {
        int next = Math.floorMod(player.getData(ModRegistries.RAINBOW_SPELL) + 1, CHOICES);
        player.setData(ModRegistries.RAINBOW_SPELL, next);
        int colour = Mth.hsvToRgb(next / (float) CHOICES, 0.6F, 1.0F);
        player.displayClientMessage(Component.translatable("tensurafragments.rainbow.selected",
                Component.translatable(choiceKey(next))).withColor(colour), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.8F, 1.4F);
        ShikigamiControl.sync(player);
    }

    /** Translation key for a choice in the switch (a spell's name, or Rainbow Spirit). */
    public static String choiceKey(int index) {
        return isSpiritIndex(index) ? "tensurafragments.rainbow.spell.spirit"
                : "tensurafragments.rainbow.spell." + RainbowSpell.byIndex(index).id();
    }

    /** Extra damage a rainbow spell adds to each hit: every element's damage at once. */
    public static float prismDamage() {
        return Spell.totalElementDamage() * Config.RAINBOW_DAMAGE_MULTIPLIER.get().floatValue();
    }

    /** Burns, freezes and slows at once. */
    public static void applyElements(LivingEntity target) {
        target.igniteForSeconds(3);
        if (target.canFreeze()) {
            target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + 100));
        }
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
    }

    public static boolean hasSkill(ServerPlayer player) {
        return SkillAPI.getSkillsFrom(player).getSkill(ModSkills.RAINBOW_MAGIC.getId()).isPresent();
    }

    public static void grantSkill(ServerPlayer player) {
        if (Config.GRANT_RAINBOW_MAGIC.get() && !hasSkill(player)) {
            SkillHelper.learnSkill(player, ModSkills.RAINBOW_MAGIC.get());
        }
    }
}
