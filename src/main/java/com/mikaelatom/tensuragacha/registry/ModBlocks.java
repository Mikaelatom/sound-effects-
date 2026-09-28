package com.mikaelatom.tensuragacha.registry;

import com.mikaelatom.tensuragacha.TensuraGacha;
import com.mikaelatom.tensuragacha.block.SoulMarketBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TensuraGacha.MODID);

    public static final DeferredBlock<SoulMarketBlock> SOUL_MARKET = BLOCKS.register("soul_market",
            () -> new SoulMarketBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 7)));
}
