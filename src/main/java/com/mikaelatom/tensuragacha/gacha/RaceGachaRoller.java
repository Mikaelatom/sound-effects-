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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Race Gacha: reincarnates the player as a random race from the whole race registry,
 * so Tensura's races, this add-on's races and other add-ons' races are all in the pool.
 * Rarity is the race's evolution stage (Human = Common ... Divine Human = Legendary),
 * which works for any add-on race that declares its previous evolutions.
 */
public final class RaceGachaRoller {
    public enum Tier {
        COMMON(ChatFormatting.WHITE, 1.2F),
        RARE(ChatFormatting.AQUA, 1.0F),
        EPIC(ChatFormatting.LIGHT_PURPLE, 0.8F),
        LEGENDARY(ChatFormatting.GOLD, 0.6F);

        final ChatFormatting color;
        final float pitch;

        Tier(ChatFormatting color, float pitch) {
            this.color = color;
            this.pitch = pitch;
        }

        int weight() {
            return switch (this) {
                case COMMON -> GachaConfig.RACE_WEIGHT_COMMON.get();
                case RARE -> GachaConfig.RACE_WEIGHT_RARE.get();
                case EPIC -> GachaConfig.RACE_WEIGHT_EPIC.get();
                case LEGENDARY -> GachaConfig.RACE_WEIGHT_LEGENDARY.get();
            };
        }

        boolean isHigh() {
            return this == EPIC || this == LEGENDARY;
        }
    }

    private RaceGachaRoller() {
    }

    /** @return true if the roll happened and the ticket should be consumed. */
    public static boolean roll(ServerPlayer player) {
        Races races = RaceAPI.getRaceFrom(player);
        ManasRace current = races.getRace().map(instance -> instance.getRace()).orElse(null);

        Map<Tier, List<ManasRace>> pool = buildPool(player, current);
        if (pool.isEmpty()) {
            player.displayClientMessage(Component.translatable("tensuragacha.race_gacha.pool_empty").withStyle(ChatFormatting.GRAY), true);
            return false;
        }

        int pity = player.getData(ModAttachments.RACE_PITY) + 1;
        int pityThreshold = GachaConfig.RACE_PITY_THRESHOLD.get();
        boolean pityActive = pityThreshold > 0 && pity >= pityThreshold
                && (pool.containsKey(Tier.EPIC) || pool.containsKey(Tier.LEGENDARY));

        Tier tier = pickTier(player, pool, pityActive);
        List<ManasRace> candidates = pool.get(tier);
        ManasRace rolled = candidates.get(player.getRandom().nextInt(candidates.size()));

        if (!races.setRace(rolled, false)) {
            player.displayClientMessage(Component.translatable("tensuragacha.race_gacha.failed").withStyle(ChatFormatting.RED), true);
            return false;
        }

        player.setData(ModAttachments.RACE_PITY, tier.isHigh() ? 0 : pity);
        announce(player, rolled, tier);
        return true;
    }

    private static Map<Tier, List<ManasRace>> buildPool(ServerPlayer player, ManasRace current) {
        List<? extends String> blacklist = GachaConfig.RACE_BLACKLIST.get();
        Map<ManasRace, Integer> stageCache = new HashMap<>();
        Map<Tier, List<ManasRace>> pool = new EnumMap<>(Tier.class);
        for (ManasRace race : RaceAPI.getRaceRegistry()) {
            if (race == current) continue;
            ResourceLocation id = race.getRegistryName();
            if (id == null || blacklist.contains(id.toString())) continue;
            Tier tier = tierOf(race, id, player, stageCache);
            if (tier.weight() <= 0) continue;
            pool.computeIfAbsent(tier, t -> new ArrayList<>()).add(race);
        }
        return pool;
    }

    private static Tier pickTier(ServerPlayer player, Map<Tier, List<ManasRace>> pool, boolean pityActive) {
        List<Tier> tiers = new ArrayList<>();
        int total = 0;
        for (Tier tier : pool.keySet()) {
            if (pityActive && !tier.isHigh()) continue;
            tiers.add(tier);
            total += tier.weight();
        }
        int roll = player.getRandom().nextInt(Math.max(1, total));
        for (Tier tier : tiers) {
            roll -= tier.weight();
            if (roll < 0) return tier;
        }
        return tiers.get(tiers.size() - 1);
    }

    static Tier tierOf(ManasRace race, ResourceLocation id, ServerPlayer player, Map<ManasRace, Integer> stageCache) {
        String key = id.toString();
        if (GachaConfig.RACE_LEGENDARY_OVERRIDES.get().contains(key)) return Tier.LEGENDARY;
        if (GachaConfig.RACE_EPIC_OVERRIDES.get().contains(key)) return Tier.EPIC;
        if (GachaConfig.RACE_RARE_OVERRIDES.get().contains(key)) return Tier.RARE;
        if (GachaConfig.RACE_COMMON_OVERRIDES.get().contains(key)) return Tier.COMMON;

        return switch (evolutionStage(race, player, stageCache, new HashSet<>())) {
            case 0 -> Tier.COMMON;
            case 1 -> Tier.RARE;
            case 2 -> Tier.EPIC;
            default -> Tier.LEGENDARY;
        };
    }

    /** 0 for a base race, otherwise 1 + the highest stage among the races it evolves from. */
    private static int evolutionStage(ManasRace race, ServerPlayer player, Map<ManasRace, Integer> cache, Set<ManasRace> visiting) {
        Integer cached = cache.get(race);
        if (cached != null) return cached;
        if (!visiting.add(race)) return 0; // evolution loop, stop here

        int stage = 0;
        for (ManasRace previous : race.getPreviousEvolutions(race.createDefaultInstance(), player)) {
            if (previous == null || previous == race) continue;
            stage = Math.max(stage, evolutionStage(previous, player, cache, visiting) + 1);
        }

        visiting.remove(race);
        cache.put(race, stage);
        return stage;
    }

    private static void announce(ServerPlayer player, ManasRace race, Tier tier) {
        MutableComponent name = race.getName() != null ? race.getName() : Component.literal(String.valueOf(race.getRegistryName()));
        String tierKey = "tensuragacha.race_gacha.tier." + tier.name().toLowerCase();
        player.sendSystemMessage(Component.translatable("tensuragacha.race_gacha.reborn",
                Component.translatable(tierKey).withStyle(tier.color),
                name.copy().withStyle(tier.color, ChatFormatting.BOLD)));

        player.level().playSound(null, player.blockPosition(), ModSounds.SQUISH_POP.get(), SoundSource.PLAYERS, 1.0F, tier.pitch);

        if (tier == Tier.LEGENDARY) {
            player.server.getPlayerList().broadcastSystemMessage(Component.translatable("tensuragacha.race_gacha.legendary",
                    player.getDisplayName(), name.copy()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
            player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }
}
