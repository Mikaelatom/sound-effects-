package com.tensurafragments.skill;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.energy.EnergyMagic;
import com.tensurafragments.flame.FlameEmperor;
import com.tensurafragments.grimoire.SealingGrimoire;
import com.tensurafragments.network.OpenSkillPickPayload;
import com.tensurafragments.rainbow.RainbowMagic;
import com.tensurafragments.rune.RuneMagic;
import com.tensurafragments.shikigami.ShikigamiControl;
import com.tensurafragments.soul.SoulReaper;
import com.tensurafragments.spirit.SpiritControl;
import com.tensurafragments.yifa.SpiritCommunion;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.manasmods.manascore.race.api.RaceAPI;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The starting skill: once a new player has picked their race, they pick one of this addon's skills (a screen opens).
 * Players who already have any of them (from before this) keep everything and aren't asked. With the option off,
 * everyone gets every skill whose grant option is on, as before.
 */
public final class StartingSkill {
    /** The skills to choose from, in the order they're shown. Spirit Release is the Spirit race's own. */
    public static final List<RegistrySupplier<? extends ManasSkill>> CHOICES = List.of(
            ModSkills.GAMBIT_CARDS, ModSkills.SHIKIGAMI_CONTROL, ModSkills.SEALING_GRIMOIRE, ModSkills.RAINBOW_MAGIC,
            ModSkills.FLAME_EMPEROR, ModSkills.SPIRIT_CONTROL, ModSkills.SPIRIT_COMMUNION, ModSkills.ENERGY_MAGIC,
            ModSkills.SOUL_REAPER, ModSkills.RUNE_MAGIC, ModSkills.EXPLOSION);
    /** How often the choice is shown again while it hasn't been made (the screen may have been covered). */
    private static final int ASK_AGAIN_TICKS = 200;
    private static final Map<UUID, Integer> LAST_ASKED = new HashMap<>();

    private StartingSkill() {
    }

    public static boolean enabled() {
        return Config.STARTING_SKILL_PICK.get();
    }

    public static boolean hasPicked(ServerPlayer player) {
        return player.getData(ModRegistries.STARTING_SKILL_PICKED);
    }

    public static boolean hasAnyChoice(ServerPlayer player) {
        var skills = SkillAPI.getSkillsFrom(player);
        return CHOICES.stream().anyMatch(choice -> skills.getSkill(choice.getId()).isPresent());
    }

    /** Called on login and every 2 seconds. */
    public static void grantOrOffer(ServerPlayer player) {
        if (!enabled()) {
            grantAll(player);
            return;
        }
        if (hasPicked(player)) {
            return;
        }
        if (hasAnyChoice(player)) {
            // From before the choice existed: keeps everything.
            player.setData(ModRegistries.STARTING_SKILL_PICKED, true);
            return;
        }
        if (RaceAPI.getRaceFrom(player).getRace().isEmpty()) {
            // Race first.
            return;
        }
        Integer last = LAST_ASKED.get(player.getUUID());
        if (last != null && player.tickCount - last < ASK_AGAIN_TICKS && player.tickCount >= last) {
            return;
        }
        LAST_ASKED.put(player.getUUID(), player.tickCount);
        PacketDistributor.sendToPlayer(player, new OpenSkillPickPayload(
                CHOICES.stream().map(choice -> choice.getId().toString()).toList()));
    }

    @Nullable
    private static RegistrySupplier<? extends ManasSkill> choice(String id) {
        return CHOICES.stream().filter(choice -> choice.getId().toString().equals(id)).findFirst().orElse(null);
    }

    /** The player's pick. Returns whether it was taken. */
    public static boolean pick(ServerPlayer player, String id) {
        RegistrySupplier<? extends ManasSkill> choice = choice(id);
        if (!enabled() || hasPicked(player) || choice == null) {
            return false;
        }
        player.setData(ModRegistries.STARTING_SKILL_PICKED, true);
        LAST_ASKED.remove(player.getUUID());
        if (choice == ModSkills.RUNE_MAGIC) {
            RuneMagic.grantSkill(player);
        } else {
            SkillHelper.learnSkill(player, choice.get());
        }
        player.sendSystemMessage(Component.translatable("tensurafragments.starting_skill.picked",
                Component.translatable(skillKey(choice.getId().getPath()))).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 1F, 1.2F);
        return true;
    }

    /** A short line on what the skill does, for the choice screen. */
    public static String descriptionKey(String path) {
        return "tensurafragments.starting_skill.desc." + path;
    }

    public static String skillKey(String path) {
        return "tensurafragments.skill." + path;
    }

    /** The old way: every skill whose grant option is on. */
    private static void grantAll(ServerPlayer player) {
        GambitCards.grantSkill(player);
        ShikigamiControl.grantSkill(player);
        SealingGrimoire.grantSkill(player);
        RainbowMagic.grantSkill(player);
        FlameEmperor.grantSkill(player);
        SpiritControl.grantSkill(player);
        SpiritCommunion.grantSkill(player);
        EnergyMagic.grantSkill(player);
        SoulReaper.grantSkill(player);
        if (Config.GRANT_RUNE_MAGIC.get()) {
            RuneMagic.grantSkill(player);
        }
        com.tensurafragments.explosion.ExplosionSkill.grantSkill(player);
    }

    public static void forget(ServerPlayer player) {
        LAST_ASKED.remove(player.getUUID());
    }
}
