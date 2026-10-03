package com.tensurafragments.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tensurafragments.shikigami.PaperBeasts;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * While a player sees through a paper beast, the world is loaded and sent to them around the beast instead of around
 * their body, so a possessed beast can go any distance away.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @WrapOperation(method = {"move", "updatePlayerPos"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/world/level/entity/EntityAccess;)Lnet/minecraft/core/SectionPos;"))
    private SectionPos tensurafragments$viewSection(EntityAccess entity, Operation<SectionPos> original) {
        return original.call(entity instanceof ServerPlayer player ? PaperBeasts.viewpoint(player) : entity);
    }

    @WrapOperation(method = "updateChunkTracking", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;chunkPosition()Lnet/minecraft/world/level/ChunkPos;"))
    private ChunkPos tensurafragments$viewChunk(ServerPlayer player, Operation<ChunkPos> original) {
        Entity view = PaperBeasts.viewpoint(player);
        return view == player ? original.call(player) : view.chunkPosition();
    }
}
