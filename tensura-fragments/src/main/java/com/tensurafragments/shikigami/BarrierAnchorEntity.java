package com.tensurafragments.shikigami;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A talisman planted in the ground. Once three or more are placed they can be linked into a barrier; the anchor
 * placed first runs the barrier (see {@link Barrier}).
 */
public class BarrierAnchorEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(BarrierAnchorEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> ORDER =
            SynchedEntityData.defineId(BarrierAnchorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ACTIVE =
            SynchedEntityData.defineId(BarrierAnchorEntity.class, EntityDataSerializers.BOOLEAN);
    /** Entity id of the next anchor around the barrier, for drawing the wall. */
    private static final EntityDataAccessor<Integer> NEXT =
            SynchedEntityData.defineId(BarrierAnchorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HEIGHT =
            SynchedEntityData.defineId(BarrierAnchorEntity.class, EntityDataSerializers.INT);

    /** An anchor that's never linked into a barrier crumbles after this long. */
    private static final int UNLINKED_LIFETIME = 1200;

    int activeTicks;

    public BarrierAnchorEntity(EntityType<? extends BarrierAnchorEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static BarrierAnchorEntity create(ServerPlayer owner, Vec3 pos, int order) {
        BarrierAnchorEntity anchor = new BarrierAnchorEntity(ModRegistries.BARRIER_ANCHOR.get(), owner.level());
        anchor.setPos(pos);
        anchor.entityData.set(OWNER, Optional.of(owner.getUUID()));
        anchor.entityData.set(ORDER, order);
        anchor.entityData.set(HEIGHT, Config.BARRIER_HEIGHT.get());
        anchor.setYRot(owner.getYRot());
        return anchor;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, Optional.empty());
        builder.define(ORDER, 0);
        builder.define(ACTIVE, false);
        builder.define(NEXT, -1);
        builder.define(HEIGHT, 5);
    }

    @Nullable
    public UUID getOwnerId() {
        return entityData.get(OWNER).orElse(null);
    }

    public boolean isOwnedBy(Entity entity) {
        return entity.getUUID().equals(getOwnerId());
    }

    public int getOrder() {
        return entityData.get(ORDER);
    }

    public boolean isActive() {
        return entityData.get(ACTIVE);
    }

    public int getWallHeight() {
        return entityData.get(HEIGHT);
    }

    @Nullable
    public BarrierAnchorEntity getNext() {
        return level().getEntity(entityData.get(NEXT)) instanceof BarrierAnchorEntity next ? next : null;
    }

    void link(BarrierAnchorEntity next) {
        entityData.set(NEXT, next.getId());
        entityData.set(ACTIVE, true);
        activeTicks = 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextInt(isActive() ? 3 : 10) == 0) {
                level().addParticle(ParticleTypes.ENCHANT, getX(), getY() + 0.6, getZ(), 0, 0.3, 0);
            }
            return;
        }
        ServerPlayer owner = getOwnerId() != null && level().getPlayerByUUID(getOwnerId()) instanceof ServerPlayer player
                ? player : null;
        if (owner == null || (!isActive() && tickCount > UNLINKED_LIFETIME)) {
            crumble();
            return;
        }
        if (isActive()) {
            activeTicks++;
            if (getOrder() == Barrier.leaderOrder(owner)) {
                Barrier.tick(owner, this);
            }
        }
    }

    public void crumble() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.3, getZ(), 4, 0.1, 0.1, 0.1, 0.01);
        }
        discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // Barriers are temporary; anchors don't survive a reload.
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
