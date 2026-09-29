package com.tensurafragments.shikigami;

import com.tensurafragments.ModRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** A thrown spell talisman. Its {@link Spell} goes off the moment it touches the ground or a creature. */
public class TalismanEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Integer> SPELL =
            SynchedEntityData.defineId(TalismanEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> LEAF =
            SynchedEntityData.defineId(TalismanEntity.class, EntityDataSerializers.BOOLEAN);

    private float potency = 1.0F;

    public TalismanEntity(EntityType<? extends TalismanEntity> type, Level level) {
        super(type, level);
    }

    public static TalismanEntity create(ServerPlayer owner, Paper.Talisman material, Spell spell) {
        TalismanEntity talisman = new TalismanEntity(ModRegistries.TALISMAN.get(), owner.level());
        talisman.setOwner(owner);
        talisman.setPos(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
        talisman.setItem(material.item());
        talisman.potency = material.potency();
        talisman.entityData.set(SPELL, spell.ordinal());
        talisman.entityData.set(LEAF, material.isLeaf());
        return talisman;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SPELL, 0);
        builder.define(LEAF, false);
    }

    public Spell getSpell() {
        return Spell.byIndex(entityData.get(SPELL));
    }

    public boolean isLeaf() {
        return entityData.get(LEAF);
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
            getSpell().cast(serverLevel, this, getOwner(), result.getLocation(), potency);
            discard();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Potency", potency);
        tag.putInt("Spell", entityData.get(SPELL));
        tag.putBoolean("Leaf", isLeaf());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        potency = tag.contains("Potency") ? tag.getFloat("Potency") : 1.0F;
        entityData.set(SPELL, tag.getInt("Spell"));
        entityData.set(LEAF, tag.getBoolean("Leaf"));
    }
}
