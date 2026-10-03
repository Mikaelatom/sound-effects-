package com.tensurafragments.mixin;

import com.tensurafragments.ally.Companions;
import io.github.manasmods.tensura.entity.template.subclass.ISubordinate;
import io.github.manasmods.tensura.network.c2s.RequestNamingMenuPacket;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A summon, or any creature that isn't one of Tensura's own monsters, given a name with Tensura's Naming becomes a named
 * companion of its namer, the same as with a name tag.
 */
@Mixin(value = RequestNamingMenuPacket.class, remap = false)
public abstract class TensuraNamedMixin {
    @Inject(method = "name", at = @At("TAIL"))
    private static void tensurafragments$companion(LivingEntity target, ServerPlayer player,
                                                   RequestNamingMenuPacket.NamingType type, String name, CallbackInfo ci) {
        IExistence existence = TensuraStorages.getExistenceFrom(target);
        // Summons, and any other creature (Tensura's own monsters keep Tensura's subordinate system).
        if (existence != null && existence.getName() != null
                && (Companions.ownerOf(target) != null || (target instanceof Mob && !(target instanceof ISubordinate)))) {
            Companions.markNamed(target);
        }
    }
}
