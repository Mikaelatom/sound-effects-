package com.tensurafragments.rune;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tensurafragments.Config;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Loot chests anywhere (any loot table under {@code chests/}, Tensura's and other mods' included) can hold a Rune
 * Tome, and sometimes a ready rune paper.
 */
public class RuneLootModifier extends LootModifier {
    public static final MapCodec<RuneLootModifier> CODEC =
            RecordCodecBuilder.mapCodec(instance -> codecStart(instance).apply(instance, RuneLootModifier::new));

    public RuneLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    public static boolean isChest(LootContext context) {
        return context.getQueriedLootTableId().getPath().startsWith("chests/");
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (!isChest(context)) {
            return loot;
        }
        RandomSource random = context.getRandom();
        Rune[] runes = Rune.values();
        if (random.nextDouble() < Config.RUNE_TOME_CHANCE.get()) {
            loot.add(RuneTomeItem.of(runes[random.nextInt(runes.length)]));
        }
        if (random.nextDouble() < Config.RUNE_PAPER_LOOT_CHANCE.get()) {
            loot.add(RunePaperItem.of(runes[random.nextInt(runes.length)]));
        }
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
