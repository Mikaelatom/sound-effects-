package com.tensurafragments.mixin;

import com.tensurafragments.ally.Companions;
import io.github.manasmods.tensura.network.c2s.RequestNamingMenuPacket;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A summon given a name with Tensura's Naming becomes a named companion, the same as with a name tag. */
@Mixin(value = RequestNamingMenuPacket.class, remap = false)
public abstract class TensuraNamedMixin {
    @Inject(method = "name", at = @At("TAIL"))
    private static void tensurafragments$companion(LivingEntity target, ServerPlayer player,
                                                   RequestNamingMenuPacket.NamingType type, String name, CallbackInfo ci) {
        IExistence existence = TensuraStorages.getExistenceFrom(target);
        if (existence != null && existence.getName() != null && Companions.ownerOf(target) != null) {
            Companions.markNamed(target);
        }
    }
}
