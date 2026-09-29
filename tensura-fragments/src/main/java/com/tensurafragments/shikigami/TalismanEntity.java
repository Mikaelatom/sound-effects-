package com.tensurafragments.shikigami;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.magic.Blast;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** An explosive paper talisman. Unlike a card it goes off the moment it hits something. */
public class TalismanEntity extends ThrowableItemProjectile {
    private float potency = 1.0F;

    public TalismanEntity(EntityType<? extends TalismanEntity> type, Level level) {
        super(type, level);
    }

    /** The thrown item shows what it was made of (paper or the leaf used). */
    public static TalismanEntity create(ServerPlayer owner, Paper.Talisman material) {
        TalismanEntity talisman = new TalismanEntity(ModRegistries.TALISMAN.get(), owner.level());
        talisman.setOwner(owner);
        talisman.setPos(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
        talisman.setItem(material.item());
        talisman.potency = material.potency();
        return talisman;
    }

    @Override
    protected Item getDefaultItem() {
        return Items.PAPER;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.01;
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Potency", potency);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        potency = tag.contains("Potency") ? tag.getFloat("Potency") : 1.0F;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            level().addParticle(ParticleTypes.ENCHANT, getX(), getY(), getZ(), 0, 0, 0);
        } else if (tickCount > 200) {
            discard();
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel serverLevel && !isRemoved()) {
            // A weaker talisman makes a smaller blast as well as a weaker one.
            double radius = Config.TALISMAN_BLAST_RADIUS.get() * (0.5 + 0.5 * potency);
            Blast.explode(serverLevel, this, getOwner(), position(), radius,
                    Config.TALISMAN_BLAST_DAMAGE.get().floatValue(), Config.BLAST_KNOCKBACK.get(),
                    Config.SELF_DAMAGE_MULTIPLIER.get().floatValue(), potency);
            discard();
        }
    }
}
