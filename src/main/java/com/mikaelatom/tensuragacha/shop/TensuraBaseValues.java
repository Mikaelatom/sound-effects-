package com.mikaelatom.tensuragacha.shop;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base Soul Coin values for Tensura: Reincarnated's raw materials. See {@link BaseValues}.
 * Metals are fixed here because Tensura makes them in its own machines (kiln, refining),
 * so their gear, blocks and nuggets get priced from these through recipes.
 */
final class TensuraBaseValues {
    private TensuraBaseValues() {
    }

    static Map<String, Long> defaults() {
        Map<String, Long> v = new LinkedHashMap<>();

        // Ores and metals, roughly in progression order
        v.put("tensura:raw_silver", 48L);
        v.put("tensura:silver_ingot", 64L);
        v.put("tensura:magic_ore_shard", 256L);
        v.put("tensura:magic_ore", 320L);
        v.put("tensura:deepslate_magic_ore", 320L);
        v.put("tensura:low_magisteel_ingot", 512L);
        v.put("tensura:high_magisteel_ingot", 2048L);
        v.put("tensura:pure_magisteel_ingot", 8192L);
        v.put("tensura:mithril_ingot", 8192L);
        v.put("tensura:orichalcum_ingot", 16384L);
        v.put("tensura:adamantite_ingot", 32768L);
        v.put("tensura:hihiirokane_ingot", 65536L);

        // Magic crystals and cores
        v.put("tensura:low_quality_magic_crystal", 128L);
        v.put("tensura:medium_quality_magic_crystal", 512L);
        v.put("tensura:high_quality_magic_crystal", 2048L);
        v.put("tensura:magic_stone", 512L);
        v.put("tensura:element_core_empty", 256L);
        v.put("tensura:earth_elemental_shard", 256L);
        v.put("tensura:fire_elemental_shard", 256L);
        v.put("tensura:space_elemental_shard", 512L);
        v.put("tensura:water_elemental_shard", 256L);
        v.put("tensura:wind_elemental_shard", 256L);
        v.put("tensura:elemental_essence", 512L);
        v.put("tensura:daemon_essence", 1024L);
        v.put("tensura:daemon_core", 2048L);
        v.put("tensura:dragon_essence", 8192L);
        v.put("tensura:slime_core", 256L);
        v.put("tensura:marionette_heart", 4096L);
        v.put("tensura:royal_blood", 4096L);
        v.put("tensura:zane_blood", 2048L);

        // Monster drops
        v.put("tensura:slime_chunk", 16L);
        v.put("tensura:chilled_slime", 32L);
        v.put("tensura:sticky_thread", 16L);
        v.put("tensura:steel_thread", 64L);
        v.put("tensura:spider_fang", 64L);
        v.put("tensura:giant_bat_wing", 64L);
        v.put("tensura:beast_horn", 128L);
        v.put("tensura:sissie_fin", 128L);
        v.put("tensura:sissie_tooth", 128L);
        v.put("tensura:spear_toro_fin", 128L);
        v.put("tensura:centipede_stinger", 128L);
        v.put("tensura:giant_ant_carapace", 128L);
        v.put("tensura:knight_spider_carapace", 256L);
        v.put("tensura:insectar_carapace", 256L);
        v.put("tensura:blade_tiger_tail", 256L);
        v.put("tensura:hell_moth_silk", 256L);
        v.put("tensura:armorsaurus_scale", 256L);
        v.put("tensura:armorsaurus_shell", 512L);
        v.put("tensura:serpent_scale", 512L);
        v.put("tensura:invisible_feather", 512L);
        v.put("tensura:dragon_peacock_feather", 1024L);
        v.put("tensura:gehenna_moth_silk", 1024L);
        v.put("tensura:unicorn_horn", 4096L);
        v.put("tensura:charybdis_scale", 8192L);
        v.put("tensura:monster_leather_d", 64L);
        v.put("tensura:monster_leather_c", 128L);
        v.put("tensura:monster_leather_b", 512L);
        v.put("tensura:monster_leather_a", 2048L);
        v.put("tensura:monster_leather_special_a", 8192L);

        // Food and plants
        v.put("tensura:raw_armorsaurus_meat", 16L);
        v.put("tensura:raw_blade_tiger_meat", 16L);
        v.put("tensura:raw_charybdis_meat", 64L);
        v.put("tensura:raw_giant_bat_meat", 8L);
        v.put("tensura:raw_megalodon_meat", 32L);
        v.put("tensura:raw_serpent_meat", 16L);
        v.put("tensura:raw_sissie_meat", 16L);
        v.put("tensura:raw_spear_toro_meat", 16L);
        v.put("tensura:cattledeer_beef", 12L);
        v.put("tensura:giant_ant_leg", 8L);
        v.put("tensura:knight_spider_leg", 8L);
        v.put("tensura:hipokute_seeds", 8L);
        v.put("tensura:hipokute_grass", 16L);
        v.put("tensura:hipokute_flower", 32L);
        v.put("tensura:thatch", 2L);

        // Tensura's own currency
        v.put("tensura:bronze_coin", 8L);
        v.put("tensura:silver_coin", 64L);
        v.put("tensura:gold_coin", 512L);
        v.put("tensura:stellar_gold_coin", 8192L);

        // Powerful one-off items
        v.put("tensura:full_potion", 4096L);
        v.put("tensura:revival_elixir", 16384L);
        v.put("tensura:orb_of_domination", 32768L);
        v.put("tensura:skill_reset_scroll", 32768L);
        v.put("tensura:race_reset_scroll", 65536L);
        v.put("tensura:character_reset_scroll", 131072L);

        return v;
    }
}
