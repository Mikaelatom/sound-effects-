package com.tensurafragments.spiritrace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.skill.ModSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.intrinsic.PossessionSkill;
import io.github.manasmods.tensura.registry.skill.IntrinsicSkills;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

/**
 * Spirit Release: a spirit possessing a body unlocks its own power instead of being held to the body's. 10% and 50%
 * last 5 minutes and cost half your health when they end; 100% lasts 2 minutes and then the spirit tears free of the
 * body (back to spiritual form, and the clock on the material world starts again). Ending it early costs the same.
 */
public final class SpiritRelease {
    /** An active release: how much (10, 50 or 100 percent) and until when (game time). */
    public record State(int percent, long until) {
        public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("percent").forGetter(State::percent),
                Codec.LONG.fieldOf("until").forGetter(State::until)
        ).apply(i, State::new));
    }

    public static final int[] LEVELS = {10, 50, 100};
    private static final ResourceLocation MODIFIER = TensuraFragments.id("spirit_release");
    /** Every stat the body holds back, raised by the released share. */
    private static final List<Holder<Attribute>> STATS = List.of(Attributes.ATTACK_DAMAGE, Attributes.MAX_HEALTH,
            Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, Attributes.MOVEMENT_SPEED, Attributes.ATTACK_SPEED);

    private SpiritRelease() {
    }

    @Nullable
    public static State state(ServerPlayer player) {
        return player.hasData(ModRegistries.SPIRIT_RELEASE) ? player.getData(ModRegistries.SPIRIT_RELEASE) : null;
    }

    public static int durationTicks(int percent) {
        return (percent >= 100 ? Config.SPIRIT_RELEASE_FULL_SECONDS.get() : Config.SPIRIT_RELEASE_SECONDS.get()) * 20;
    }

    /** The skill key: release at this level, or (if already released) end it. */
    public static boolean press(ServerPlayer player, ManasSkillInstance instance, int mode) {
        if (state(player) != null) {
            end(player, false);
            return true;
        }
        int percent = LEVELS[Math.floorMod(mode, LEVELS.length)];
        if (!SpiritRaces.isSpirit(player)) {
            player.displayClientMessage(Component.translatable("tensurafragments.spirit_release.not_spirit"), true);
            return false;
        }
        if (SpiritPassage.isSpiritual(player) || SpiritPassage.inSpiritRealm(player)) {
            player.displayClientMessage(Component.translatable("tensurafragments.spirit_release.no_body"), true);
            return false;
        }
        if (instance.getCoolDown(mode) > 0) {
            player.displayClientMessage(Component.translatable("tensurafragments.spirit_release.cooldown",
                    instance.getCoolDown(mode)), true);
            return false;
        }
        start(player, percent);
        return true;
    }

    public static void start(ServerPlayer player, int percent) {
        float healthShare = player.getHealth() / player.getMaxHealth();
        player.setData(ModRegistries.SPIRIT_RELEASE, new State(percent, player.level().getGameTime() + durationTicks(percent)));
        applyModifiers(player, percent);
        // The extra health comes filled in.
        player.setHealth(player.getMaxHealth() * healthShare);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1, player.getZ(),
                20 + percent / 2, 0.5, 0.9, 0.5, 0.08);
        level.sendParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT,
                SoundSource.PLAYERS, 1.2F, 0.6F + percent / 200F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.5F, 0.7F);
        player.displayClientMessage(Component.translatable("tensurafragments.spirit_release.started", percent,
                durationTicks(percent) / 20 / 60).withColor(0x7FD8FF), true);
        SpiritPassage.sync(player);
    }

    private static void applyModifiers(ServerPlayer player, int percent) {
        for (Holder<Attribute> stat : STATS) {
            AttributeInstance instance = player.getAttribute(stat);
            if (instance != null) {
                instance.addOrReplacePermanentModifier(new AttributeModifier(MODIFIER, percent / 100.0,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }

    private static void removeModifiers(ServerPlayer player) {
        for (Holder<Attribute> stat : STATS) {
            AttributeInstance instance = player.getAttribute(stat);
            if (instance != null) {
                instance.removeModifier(MODIFIER);
            }
        }
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }

    /**
     * The release ends: at 100% the spirit tears free of its body; otherwise the body pays half its health. With
     * {@code bodyLost} it ended because the body was already gone, so there's nothing more to pay.
     */
    public static void end(ServerPlayer player, boolean bodyLost) {
        State state = state(player);
        if (state == null) {
            return;
        }
        player.removeData(ModRegistries.SPIRIT_RELEASE);
        removeModifiers(player);
        ServerLevel level = player.serverLevel();
        if (!bodyLost) {
            if (state.percent() >= 100) {
                tearFree(player);
                player.displayClientMessage(Component.translatable("tensurafragments.spirit_release.torn_free")
                        .withColor(0xFF9090), false);
            } else {
                player.setHealth(player.getHealth() / 2);
                player.displayClientMessage(Component.translatable("tensurafragments.spirit_release.ended")
                        .withColor(0x7FD8FF), true);
            }
        }
        level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 25, 0.4, 0.8, 0.4, 0.04);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE,
                SoundSource.PLAYERS, 1F, 0.8F);
        SkillAPI.getSkillsFrom(player).getSkill(ModSkills.SPIRIT_RELEASE.get()).ifPresent(instance -> {
            for (int mode = 0; mode < LEVELS.length; mode++) {
                instance.setCoolDown(Config.SPIRIT_RELEASE_COOLDOWN_SECONDS.get(), mode);
            }
        });
        SpiritPassage.sync(player);
    }

    /** Back to spiritual form, leaving the body behind, the way Tensura's own Possession does it. */
    private static void tearFree(ServerPlayer player) {
        IExistence existence = TensuraStorages.getExistenceFrom(player);
        if (existence == null || existence.isSpiritualForm()) {
            return;
        }
        ManasSkillInstance possession = SkillAPI.getSkillsFrom(player).getSkill(IntrinsicSkills.POSSESSION.get()).orElse(null);
        if (possession != null) {
            PossessionSkill.turnSpiritual(possession, player, existence, PossessionSkill.CONFIG.bodyDespawnTick);
        }
        if (!existence.isSpiritualForm()) {
            existence.setSpiritualForm(true);
            existence.markDirty();
        }
    }

    /** Every tick for released spirits: the aura, and the time limit. */
    static void tick(ServerPlayer player) {
        State state = state(player);
        if (state == null) {
            return;
        }
        if (SpiritPassage.isSpiritual(player) || !SpiritRaces.isSpirit(player) || SpiritPassage.inSpiritRealm(player)) {
            // The body is already gone (or the spirit is no longer a spirit).
            end(player, true);
            return;
        }
        if (player.level().getGameTime() >= state.until()) {
            end(player, false);
            return;
        }
        if (player.tickCount % 20 == 0) {
            // Keeps the stats right if anything else touched them.
            applyModifiers(player, state.percent());
        }
        aura(player, state.percent());
    }

    private static void aura(ServerPlayer player, int percent) {
        ServerLevel level = player.serverLevel();
        int flames = percent >= 100 ? 4 : percent >= 50 ? 2 : 1;
        for (int i = 0; i < flames; i++) {
            double angle = (player.tickCount * 0.35 + i * Math.PI * 2 / flames);
            double radius = 0.7 + percent / 250.0;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX() + Math.cos(angle) * radius,
                    player.getY() + 0.1 + level.random.nextDouble() * player.getBbHeight(),
                    player.getZ() + Math.sin(angle) * radius, 1, 0, 0.04, 0, 0.01);
        }
        if (percent >= 50 && player.tickCount % 3 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + player.getBbHeight() / 2, player.getZ(),
                    1, 0.4, 0.6, 0.4, 0.02);
        }
    }
}
