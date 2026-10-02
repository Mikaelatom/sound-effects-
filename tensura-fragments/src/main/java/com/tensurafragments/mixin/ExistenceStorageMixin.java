package com.tensurafragments.mixin;

import com.tensurafragments.spiritrace.SpiritRaces;
import io.github.manasmods.tensura.storage.ep.ExistenceStorage;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Tensura drains magicules from anything in spiritual form outside its own spiritual dimensions (115 every half
 * second). Spirits are at home without a body for a while (the Book of Passage gives them a set time instead), so
 * their magicules regenerate normally.
 */
@Mixin(value = ExistenceStorage.class, remap = false)
public abstract class ExistenceStorageMixin {
    @Redirect(method = "handleMagiculeRegen", at = @At(value = "INVOKE",
            target = "Lio/github/manasmods/tensura/storage/ep/IExistence;isSpiritualForm()Z"))
    private static boolean tensurafragments$spiritsDontFade(IExistence existence, IExistence sameExistence, LivingEntity entity,
                                                            double magicule, double maxMagicule) {
        return existence.isSpiritualForm() && !SpiritRaces.isSpirit(entity);
    }
}
