package com.mikaelatom.tensuragacha.shop;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hand-set prices (in Soul Coins) for raw materials that aren't crafted from anything.
 * Everything else is priced from its recipes in {@link ItemValues}.
 * Entries starting with '#' are item tags. Later entries win over earlier ones.
 */
final class BaseValues {
    private BaseValues() {
    }

    static Map<String, Long> defaults() {
        Map<String, Long> v = new LinkedHashMap<>();

        // Common blocks
        v.put("#minecraft:dirt", 1L);
        v.put("minecraft:cobblestone", 1L);
        v.put("minecraft:cobbled_deepslate", 1L);
        v.put("minecraft:netherrack", 1L);
        v.put("minecraft:end_stone", 2L);
        v.put("minecraft:stone", 2L);
        v.put("minecraft:deepslate", 2L);
        v.put("minecraft:granite", 1L);
        v.put("minecraft:diorite", 1L);
        v.put("minecraft:andesite", 1L);
        v.put("minecraft:tuff", 1L);
        v.put("minecraft:calcite", 2L);
        v.put("minecraft:blackstone", 1L);
        v.put("minecraft:basalt", 1L);
        v.put("minecraft:sand", 1L);
        v.put("minecraft:red_sand", 1L);
        v.put("minecraft:gravel", 1L);
        v.put("minecraft:clay_ball", 4L);
        v.put("minecraft:snowball", 1L);
        v.put("minecraft:ice", 2L);
        v.put("minecraft:soul_sand", 2L);
        v.put("minecraft:soul_soil", 2L);
        v.put("minecraft:magma_block", 8L);
        v.put("minecraft:obsidian", 64L);
        v.put("minecraft:crying_obsidian", 128L);
        v.put("minecraft:mud", 1L);
        v.put("minecraft:moss_block", 4L);
        v.put("minecraft:pointed_dripstone", 4L);
        v.put("minecraft:sculk", 8L);
        v.put("minecraft:mycelium", 16L);
        v.put("minecraft:podzol", 4L);
        v.put("minecraft:sponge", 512L);

        // Plants
        v.put("#minecraft:logs", 8L);
        v.put("#minecraft:leaves", 1L);
        v.put("#minecraft:saplings", 8L);
        v.put("#minecraft:flowers", 8L);
        v.put("minecraft:short_grass", 1L);
        v.put("minecraft:tall_grass", 1L);
        v.put("minecraft:fern", 1L);
        v.put("minecraft:dead_bush", 1L);
        v.put("minecraft:vine", 2L);
        v.put("minecraft:lily_pad", 4L);
        v.put("minecraft:cactus", 4L);
        v.put("minecraft:sugar_cane", 4L);
        v.put("minecraft:bamboo", 1L);
        v.put("minecraft:kelp", 2L);
        v.put("minecraft:seagrass", 1L);
        v.put("minecraft:wheat_seeds", 2L);
        v.put("minecraft:wheat", 6L);
        v.put("minecraft:carrot", 6L);
        v.put("minecraft:potato", 6L);
        v.put("minecraft:beetroot", 6L);
        v.put("minecraft:beetroot_seeds", 2L);
        v.put("minecraft:melon_slice", 2L);
        v.put("minecraft:melon_seeds", 2L);
        v.put("minecraft:pumpkin", 12L);
        v.put("minecraft:pumpkin_seeds", 2L);
        v.put("minecraft:cocoa_beans", 8L);
        v.put("minecraft:sweet_berries", 4L);
        v.put("minecraft:glow_berries", 8L);
        v.put("minecraft:apple", 8L);
        v.put("minecraft:red_mushroom", 8L);
        v.put("minecraft:brown_mushroom", 8L);
        v.put("minecraft:crimson_fungus", 8L);
        v.put("minecraft:warped_fungus", 8L);
        v.put("minecraft:nether_wart", 16L);
        v.put("minecraft:chorus_fruit", 16L);
        v.put("minecraft:torchflower_seeds", 64L);
        v.put("minecraft:pitcher_pod", 64L);
        v.put("minecraft:spore_blossom", 64L);

        // Mob drops
        v.put("minecraft:rotten_flesh", 2L);
        v.put("minecraft:bone", 8L);
        v.put("minecraft:string", 8L);
        v.put("minecraft:spider_eye", 8L);
        v.put("minecraft:feather", 8L);
        v.put("minecraft:leather", 16L);
        v.put("minecraft:rabbit_hide", 4L);
        v.put("minecraft:rabbit_foot", 64L);
        v.put("minecraft:gunpowder", 32L);
        v.put("minecraft:slime_ball", 32L);
        v.put("minecraft:ink_sac", 8L);
        v.put("minecraft:glow_ink_sac", 32L);
        v.put("minecraft:egg", 4L);
        v.put("minecraft:honeycomb", 16L);
        v.put("minecraft:scute", 64L);
        v.put("minecraft:turtle_scute", 64L);
        v.put("minecraft:armadillo_scute", 32L);
        v.put("minecraft:phantom_membrane", 64L);
        v.put("minecraft:ender_pearl", 128L);
        v.put("minecraft:blaze_rod", 128L);
        v.put("minecraft:ghast_tear", 256L);
        v.put("minecraft:magma_cream", 64L);
        v.put("minecraft:prismarine_shard", 16L);
        v.put("minecraft:prismarine_crystals", 32L);
        v.put("minecraft:nautilus_shell", 256L);
        v.put("minecraft:shulker_shell", 1024L);
        v.put("minecraft:wither_skeleton_skull", 2048L);
        v.put("minecraft:breeze_rod", 256L);
        v.put("minecraft:beef", 8L);
        v.put("minecraft:porkchop", 8L);
        v.put("minecraft:chicken", 8L);
        v.put("minecraft:mutton", 8L);
        v.put("minecraft:rabbit", 8L);
        v.put("minecraft:cod", 8L);
        v.put("minecraft:salmon", 8L);
        v.put("minecraft:tropical_fish", 16L);
        v.put("minecraft:pufferfish", 16L);
        v.put("#minecraft:wool", 12L);

        // Ores and minerals
        v.put("minecraft:coal", 16L);
        v.put("minecraft:charcoal", 12L);
        v.put("minecraft:raw_copper", 12L);
        v.put("minecraft:copper_ingot", 16L);
        v.put("minecraft:raw_iron", 48L);
        v.put("minecraft:iron_ingot", 64L);
        v.put("minecraft:raw_gold", 96L);
        v.put("minecraft:gold_ingot", 128L);
        v.put("minecraft:gold_nugget", 14L);
        v.put("minecraft:iron_nugget", 7L);
        v.put("minecraft:redstone", 16L);
        v.put("minecraft:lapis_lazuli", 32L);
        v.put("minecraft:quartz", 32L);
        v.put("minecraft:glowstone_dust", 32L);
        v.put("minecraft:amethyst_shard", 32L);
        v.put("minecraft:flint", 4L);
        v.put("minecraft:emerald", 512L);
        v.put("minecraft:diamond", 1024L);
        v.put("minecraft:ancient_debris", 4096L);
        v.put("minecraft:netherite_scrap", 4096L);
        v.put("minecraft:echo_shard", 1024L);

        // Rare loot
        v.put("minecraft:saddle", 512L);
        v.put("minecraft:name_tag", 512L);
        v.put("minecraft:heart_of_the_sea", 8192L);
        v.put("minecraft:totem_of_undying", 8192L);
        v.put("minecraft:trident", 8192L);
        v.put("minecraft:elytra", 32768L);
        v.put("minecraft:nether_star", 32768L);
        v.put("minecraft:dragon_egg", 131072L);
        v.put("minecraft:dragon_head", 16384L);
        v.put("minecraft:dragon_breath", 256L);
        v.put("minecraft:enchanted_golden_apple", 16384L);
        v.put("minecraft:heavy_core", 16384L);
        v.put("minecraft:sniffer_egg", 2048L);
        v.put("minecraft:netherite_upgrade_smithing_template", 8192L);
        v.put("#minecraft:trim_templates", 2048L);
        v.put("#minecraft:decorated_pot_sherds", 256L);
        v.put("#minecraft:creeper_drop_music_discs", 1024L);
        v.put("minecraft:music_disc_pigstep", 4096L);
        v.put("minecraft:music_disc_otherside", 4096L);
        v.put("minecraft:music_disc_5", 4096L);
        v.put("minecraft:disc_fragment_5", 512L);
        v.put("minecraft:goat_horn", 512L);
        v.put("minecraft:experience_bottle", 64L);
        v.put("minecraft:enchanted_book", 512L);
        v.put("minecraft:potion", 32L);
        v.put("minecraft:water_bucket", 193L);
        v.put("minecraft:lava_bucket", 200L);
        v.put("minecraft:milk_bucket", 196L);

        return v;
    }
}
