package com.mikaelatom.tensuragacha.block;

import com.mikaelatom.tensuragacha.shop.ShopMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Right-click to open the Soul Market shop. */
public class SoulMarketBlock extends Block {
    public SoulMarketBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            ShopMenu.open(serverPlayer, null);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
