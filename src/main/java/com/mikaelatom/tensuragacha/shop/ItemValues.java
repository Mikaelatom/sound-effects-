package com.mikaelatom.tensuragacha.shop;

import com.mikaelatom.tensuragacha.GachaConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Works out a Soul Coin value for every item in the game.
 * <ol>
 *   <li>Raw materials get a hand-set base value ({@link BaseValues}, plus config overrides).</li>
 *   <li>Every other item is priced from its cheapest recipe (crafting, smelting, stonecutting, modded
 *       recipes that list their ingredients...): the sum of its ingredients divided by how many it makes.
 *       This repeats until nothing changes, so long crafting chains are priced too.</li>
 *   <li>Anything still unpriced falls back to a value from its item rarity.</li>
 * </ol>
 * Because prices come from the cheapest way to make something, crafting and then selling can't earn more
 * than selling the ingredients, and selling pays less than buying, so the shop can't be farmed.
 */
public final class ItemValues {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_PASSES = 40;

    /** Creative-only or unobtainable items that the shop never sells or buys. */
    private static final Set<String> BUILT_IN_BLACKLIST = Set.of(
            "minecraft:air", "minecraft:bedrock", "minecraft:barrier", "minecraft:light",
            "minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block",
            "minecraft:command_block_minecart", "minecraft:structure_block", "minecraft:structure_void",
            "minecraft:jigsaw", "minecraft:debug_stick", "minecraft:knowledge_book", "minecraft:spawner",
            "minecraft:trial_spawner", "minecraft:vault", "minecraft:end_portal_frame",
            "minecraft:reinforced_deepslate", "minecraft:budding_amethyst", "minecraft:petrified_oak_slab",
            "minecraft:frogspawn", "minecraft:farmland", "minecraft:dirt_path", "minecraft:bundle",
            "minecraft:trial_key", "minecraft:ominous_trial_key", "minecraft:ominous_bottle");

    private static Map<Item, Long> values = new HashMap<>();
    private static List<Item> shopItems = List.of();

    private ItemValues() {
    }

    /** Buy price of one item, or 0 if the shop doesn't trade it. */
    public static long buyPrice(Item item) {
        if (!isTradeable(item)) return 0;
        long value = values.getOrDefault(item, 0L);
        return Math.max(1, Math.round(value * GachaConfig.SHOP_PRICE_MULTIPLIER.get()));
    }

    /** Coins paid for selling this stack, taking durability into account. */
    public static long sellPrice(ItemStack stack) {
        long each = buyPrice(stack.getItem());
        if (each <= 0 || stack.isEmpty()) return 0;
        double condition = 1.0;
        if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
            condition = 1.0 - (double) stack.getDamageValue() / stack.getMaxDamage();
        }
        return (long) Math.floor(each * GachaConfig.SHOP_SELL_MULTIPLIER.get() * condition * stack.getCount());
    }

    public static boolean isTradeable(Item item) {
        if (item == Items.AIR || !values.containsKey(item)) return false;
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        if (BUILT_IN_BLACKLIST.contains(id) || GachaConfig.SHOP_BLACKLIST.get().contains(id)) return false;
        return GachaConfig.SHOP_SPAWN_EGGS.get() || !(item instanceof SpawnEggItem);
    }

    /** Every item the shop sells, sorted by mod and then by name. */
    public static List<Item> shopItems() {
        return shopItems;
    }

    public static void recompute(MinecraftServer server) {
        long start = System.currentTimeMillis();
        Map<Item, Long> result = new HashMap<>();
        Set<Item> fixed = new HashSet<>();

        Map<String, Long> base = BaseValues.defaults();
        base.putAll(TensuraBaseValues.defaults());
        for (String entry : GachaConfig.SHOP_VALUE_OVERRIDES.get()) {
            int split = entry.lastIndexOf('=');
            try {
                base.put(entry.substring(0, split).trim(), Long.parseLong(entry.substring(split + 1).trim()));
            } catch (RuntimeException e) {
                LOGGER.warn("Ignoring bad shop value override '{}'", entry);
            }
        }
        for (Map.Entry<String, Long> entry : base.entrySet()) {
            for (Item item : resolve(entry.getKey())) {
                result.put(item, entry.getValue());
                fixed.add(item);
            }
        }

        record SimpleRecipe(Item output, int count, List<Item[]> inputs) {
        }
        List<SimpleRecipe> recipes = new ArrayList<>();
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            try {
                Recipe<?> recipe = holder.value();
                ItemStack out = recipe.getResultItem(server.registryAccess());
                if (out.isEmpty() || fixed.contains(out.getItem())) continue;
                List<Item[]> inputs = new ArrayList<>();
                for (Ingredient ingredient : recipe.getIngredients()) {
                    if (ingredient.isEmpty()) continue;
                    ItemStack[] options = ingredient.getItems();
                    Item[] items = new Item[options.length];
                    for (int i = 0; i < options.length; i++) items[i] = options[i].getItem();
                    if (items.length > 0) inputs.add(items);
                }
                if (!inputs.isEmpty()) recipes.add(new SimpleRecipe(out.getItem(), out.getCount(), inputs));
            } catch (RuntimeException e) {
                // Some modded recipes can't report results outside a real crafting grid; skip them.
            }
        }

        for (int pass = 0; pass < MAX_PASSES; pass++) {
            boolean changed = false;
            for (SimpleRecipe recipe : recipes) {
                long cost = 0;
                boolean complete = true;
                for (Item[] options : recipe.inputs()) {
                    long cheapest = Long.MAX_VALUE;
                    for (Item option : options) {
                        Long value = result.get(option);
                        if (value != null && value < cheapest) cheapest = value;
                    }
                    if (cheapest == Long.MAX_VALUE) {
                        complete = false;
                        break;
                    }
                    cost += cheapest;
                }
                if (!complete) continue;
                long each = Math.max(1, (cost + recipe.count() - 1) / recipe.count());
                Long current = result.get(recipe.output());
                if (current == null || each < current) {
                    result.put(recipe.output(), each);
                    changed = true;
                }
            }
            if (!changed) break;
        }

        // Netherite gear is made at a smithing table, which doesn't list its ingredients: diamond version + ingot.
        Item netheriteIngot = Items.NETHERITE_INGOT;
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (result.containsKey(item) || !id.getPath().startsWith("netherite_")) continue;
            Item diamondVersion = BuiltInRegistries.ITEM.get(id.withPath(id.getPath().replaceFirst("netherite_", "diamond_")));
            if (diamondVersion != Items.AIR && result.containsKey(diamondVersion) && result.containsKey(netheriteIngot)) {
                result.put(item, result.get(diamondVersion) + result.get(netheriteIngot));
            }
        }

        // Anything left gets a value from its rarity.
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR || result.containsKey(item)) continue;
            long fallback = switch (item.getDefaultInstance().getRarity()) {
                case UNCOMMON -> 512;
                case RARE -> 4096;
                case EPIC -> 32768;
                default -> 32; // COMMON, and any rarity added by other mods
            };
            result.put(item, fallback);
        }

        values = result;
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (isTradeable(item)) items.add(item);
        }
        items.sort(Comparator.comparing((Item item) -> BuiltInRegistries.ITEM.getKey(item).getNamespace())
                .thenComparing(item -> BuiltInRegistries.ITEM.getKey(item).getPath()));
        shopItems = List.copyOf(items);

        LOGGER.info("Soul Market priced {} items from {} recipes in {} ms", items.size(), recipes.size(),
                System.currentTimeMillis() - start);
    }

    private static List<Item> resolve(String key) {
        List<Item> items = new ArrayList<>();
        if (key.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(key.substring(1));
            if (tagId == null) return items;
            for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, tagId))) {
                items.add(holder.value());
            }
        } else {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) items.add(BuiltInRegistries.ITEM.get(id));
        }
        return items;
    }
}
