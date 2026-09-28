package com.mikaelatom.tensuragacha.gacha;

import com.mikaelatom.tensuragacha.GachaConfig;
import com.mikaelatom.tensuragacha.registry.ModAttachments;
import com.mikaelatom.tensuragacha.registry.ModSounds;
import io.github.manasmods.manascore.race.api.ManasRace;
import io.github.manasmods.manascore.race.api.RaceAPI;
import io.github.manasmods.manascore.race.api.Races;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Race Gacha: reincarnates the player as a random race from the whole race registry,
 * so Tensura's races, this add-on's races and other add-ons' races are all in the pool.
 * Rarity comes from each race's difficulty: Easy = common ... Extreme = legendary.
 */
public final class RaceGachaRoller {
    private RaceGachaRoller() {
    }

    /** @return true if the roll happened and the ticket should be consumed. */
    public static boolean roll(ServerPlayer player) {
        Races races = RaceAPI.getRaceFrom(player);
        ManasRace current = races.getRace().map(instance -> instance.getRace()).orElse(null);

        List<ManasRace> pool = buildPool(player, current);
        if (pool.isEmpty()) {
            player.displayClientMessage(Component.translatable("tensuragacha.race_gacha.pool_empty").withStyle(ChatFormatting.GRAY), true);
            return false;
        }

        int pity = player.getData(ModAttachments.RACE_PITY) + 1;
        int pityThreshold = GachaConfig.RACE_PITY_THRESHOLD.get();
        if (pityThreshold > 0 && pity >= pityThreshold) {
            List<ManasRace> rare = pool.stream().filter(RaceGachaRoller::isRare).toList();
            if (!rare.isEmpty()) pool = rare;
        }

        ManasRace rolled = pickWeighted(player, pool);
        if (rolled == null || !races.setRace(rolled, false)) {
            player.displayClientMessage(Component.translatable("tensuragacha.race_gacha.failed").withStyle(ChatFormatting.RED), true);
            return false;
        }

        player.setData(ModAttachments.RACE_PITY, isRare(rolled) ? 0 : pity);
        announce(player, rolled);
        return true;
    }

    private static List<ManasRace> buildPool(ServerPlayer player, ManasRace current) {
        List<? extends String> blacklist = GachaConfig.RACE_BLACKLIST.get();
        boolean startingOnly = GachaConfig.RACE_STARTING_ONLY.get();
        List<ManasRace> pool = new ArrayList<>();
        for (ManasRace race : RaceAPI.getRaceRegistry()) {
            if (race == current || weight(race) <= 0) continue;
            ResourceLocation id = race.getRegistryName();
            if (id == null || blacklist.contains(id.toString())) continue;
            if (startingOnly && !race.getPreviousEvolutions(race.createDefaultInstance(), player).isEmpty()) continue;
            pool.add(race);
        }
        return pool;
    }

    private static ManasRace pickWeighted(ServerPlayer player, List<ManasRace> pool) {
        int total = 0;
        for (ManasRace race : pool) total += weight(race);
        if (total <= 0) return pool.get(player.getRandom().nextInt(pool.size()));

        int roll = player.getRandom().nextInt(total);
        for (ManasRace race : pool) {
            roll -= weight(race);
            if (roll < 0) return race;
        }
        return null;
    }

    private static int weight(ManasRace race) {
        return switch (race.getDifficulty()) {
            case EASY -> GachaConfig.RACE_WEIGHT_EASY.get();
            case INTERMEDIATE -> GachaConfig.RACE_WEIGHT_INTERMEDIATE.get();
            case HARD -> GachaConfig.RACE_WEIGHT_HARD.get();
            case EXTREME -> GachaConfig.RACE_WEIGHT_EXTREME.get();
        };
    }

    private static boolean isRare(ManasRace race) {
        return race.getDifficulty() == ManasRace.Difficulty.HARD || race.getDifficulty() == ManasRace.Difficulty.EXTREME;
    }

    private static void announce(ServerPlayer player, ManasRace race) {
        ManasRace.Difficulty difficulty = race.getDifficulty();
        String tier = switch (difficulty) {
            case EASY -> "common";
            case INTERMEDIATE -> "rare";
            case HARD -> "epic";
            case EXTREME -> "legendary";
        };
        MutableComponent name = race.getName() != null ? race.getName() : Component.literal(String.valueOf(race.getRegistryName()));
        player.sendSystemMessage(Component.translatable("tensuragacha.race_gacha.reborn",
                Component.translatable("tensuragacha.race_gacha.tier." + tier).withStyle(tierColor(difficulty)),
                name.copy().withStyle(tierColor(difficulty), ChatFormatting.BOLD)));

        float pitch = switch (difficulty) {
            case EASY -> 1.2F;
            case INTERMEDIATE -> 1.0F;
            case HARD -> 0.8F;
            case EXTREME -> 0.6F;
        };
        player.level().playSound(null, player.blockPosition(), ModSounds.SQUISH_POP.get(), SoundSource.PLAYERS, 1.0F, pitch);

        if (difficulty == ManasRace.Difficulty.EXTREME) {
            player.server.getPlayerList().broadcastSystemMessage(Component.translatable("tensuragacha.race_gacha.legendary",
                    player.getDisplayName(), name.copy()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
            player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    private static ChatFormatting tierColor(ManasRace.Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> ChatFormatting.WHITE;
            case INTERMEDIATE -> ChatFormatting.AQUA;
            case HARD -> ChatFormatting.LIGHT_PURPLE;
            case EXTREME -> ChatFormatting.GOLD;
        };
    }
}
