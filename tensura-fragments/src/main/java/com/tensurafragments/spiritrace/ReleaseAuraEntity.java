package com.tensurafragments.spiritrace;

import com.tensurafragments.ModRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Spirit Release's aura: Tensura's Demon Lord Haki around the released spirit, blue at 10%, rainbow at 50% and purple
 * at 100%. Only a look: it follows its spirit and does nothing else (the real Haki's fear and pressure aren't part of it).
 */
public class ReleaseAuraEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Integer> OWNER =
            SynchedEntityData.defineId(ReleaseAuraEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PERCENT =
            SynchedEntityData.defineId(ReleaseAuraEntity.class, EntityDataSerializers.INT);
    private static final RawAnimation LOOP = RawAnimation.begin().thenLoop("animation.haki.loop");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ReleaseAuraEntity(EntityType<? extends ReleaseAuraEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static ReleaseAuraEntity spawn(ServerPlayer owner, int percent) {
        ReleaseAuraEntity aura = new ReleaseAuraEntity(ModRegistries.RELEASE_AURA.get(), owner.level());
        aura.entityData.set(OWNER, owner.getId());
        aura.entityData.set(PERCENT, percent);
        aura.setPos(owner.position());
        owner.level().addFreshEntity(aura);
        return aura;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, -1);
        builder.define(PERCENT, 10);
    }

    public int percent() {
        return entityData.get(PERCENT);
    }

    @Nullable
    public Entity owner() {
        return level().getEntity(entityData.get(OWNER));
    }

    @Override
    public void tick() {
        super.tick();
        Entity owner = owner();
        if (owner != null) {
            setPos(owner.position());
        }
        if (level().isClientSide) {
            return;
        }
        // Gone with its release (or its spirit).
        SpiritRelease.State state = owner instanceof ServerPlayer player ? SpiritRelease.state(player) : null;
        if (owner == null || !owner.isAlive() || state == null || state.percent() != percent()) {
            discard();
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "aura", 0, state -> state.setAndContinue(LOOP)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96 * 96;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
