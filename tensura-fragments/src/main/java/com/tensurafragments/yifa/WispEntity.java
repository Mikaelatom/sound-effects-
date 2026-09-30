package com.tensurafragments.yifa;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * An elemental spirit. Wild ones drift near their element and are only visible with Spirit Sight; bound ones orbit
 * the player who called them until they're spent on Spirit Magic or their time runs out.
 */
public class WispEntity extends Entity {
    private static final EntityDataAccessor<Integer> ELEMENT =
            SynchedEntityData.defineId(WispEntity.class, EntityDataSerializers.INT);
    /** Entity id of the player it's bound to, or -1 while wild. */
    private static final EntityDataAccessor<Integer> OWNER =
            SynchedEntityData.defineId(WispEntity.class, EntityDataSerializers.INT);
    /** Ticks left while bound, for the HUD. */
    private static final EntityDataAccessor<Integer> TIME_LEFT =
            SynchedEntityData.defineId(WispEntity.class, EntityDataSerializers.INT);

    /** Wild spirits wander off after this long. */
    public static final int WILD_LIFETIME = 1200;

    @Nullable
    private UUID ownerUuid;
    /** A player whose Magisteel Spirit Bell or Lantern is pulling this wild spirit in. */
    @Nullable
    private UUID luredBy;
    /** Bell: bind as soon as it arrives. Lantern: just come close and wait. */
    private boolean bindOnArrival;
    private int lureTicks;
    private Vec3 home = Vec3.ZERO;

    public WispEntity(EntityType<? extends WispEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static WispEntity wild(Level level, SpiritElement element, Vec3 at) {
        WispEntity wisp = new WispEntity(ModRegistries.WISP.get(), level);
        wisp.entityData.set(ELEMENT, element.ordinal());
        wisp.setPos(at.x, at.y, at.z);
        wisp.home = at;
        return wisp;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ELEMENT, 0);
        builder.define(OWNER, -1);
        builder.define(TIME_LEFT, 0);
    }

    public SpiritElement getElement() {
        return SpiritElement.byIndex(entityData.get(ELEMENT));
    }

    public boolean isWild() {
        return entityData.get(OWNER) < 0;
    }

    public int getOwnerId() {
        return entityData.get(OWNER);
    }

    public boolean isBoundTo(Entity player) {
        return entityData.get(OWNER) == player.getId();
    }

    /** Ticks left while bound, 0 when wild. */
    public int getTimeLeft() {
        return entityData.get(TIME_LEFT);
    }

    void bind(ServerPlayer player) {
        entityData.set(OWNER, player.getId());
        entityData.set(TIME_LEFT, Config.YIFA_BOUND_SECONDS.get() * 20);
        ownerUuid = player.getUUID();
        luredBy = null;
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.6F);
    }

    /** A bell or lantern calls this wild spirit over. */
    void lure(ServerPlayer player, boolean bind) {
        if (!isWild()) {
            return;
        }
        luredBy = player.getUUID();
        bindOnArrival |= bind;
        lureTicks = 200;
    }

    /** Used up: a little burst of its element. */
    void spend() {
        if (level() instanceof ServerLevel level) {
            level.sendParticles(getElement().dust(1.4F), getX(), getY(), getZ(), 10, 0.2, 0.2, 0.2, 0.05);
        }
        discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (isWild()) {
            tickWild(level);
        } else {
            tickBound(level);
        }
    }

    private void tickWild(ServerLevel level) {
        if (tickCount > WILD_LIFETIME) {
            discard();
            return;
        }
        ServerPlayer lurer = luredBy == null ? null : (ServerPlayer) level.getPlayerByUUID(luredBy);
        if (lurer != null && lureTicks-- > 0 && lurer.isAlive()) {
            Vec3 target = lurer.getEyePosition().add(0, 0.3, 0);
            double distance = position().distanceTo(target);
            if (bindOnArrival && distance < 1.8 && SpiritCommunion.hasRoom(lurer)
                    && SpiritCommunion.hasSkill(lurer)) {
                bind(lurer);
                return;
            }
            // Lanterns draw spirits in to wait close by; bells pull them all the way.
            double stopAt = bindOnArrival ? 0 : 2.5;
            if (distance > stopAt) {
                moveToward(target, 0.45);
            }
            home = position();
            return;
        }
        luredBy = null;
        bindOnArrival = false;
        // Drift and bob around where it appeared.
        double t = (tickCount + getId() * 13) * 0.05;
        Vec3 target = home.add(Math.sin(t) * 0.6, Math.sin(t * 1.7) * 0.3, Math.cos(t * 0.8) * 0.6);
        moveToward(target, 0.1);
    }

    private void tickBound(ServerLevel level) {
        ServerPlayer owner = ownerUuid == null ? null : (ServerPlayer) level.getPlayerByUUID(ownerUuid);
        int left = entityData.get(TIME_LEFT) - 1;
        if (owner == null || !owner.isAlive() || owner.getId() != entityData.get(OWNER) || left <= 0) {
            level.sendParticles(ParticleTypes.POOF, getX(), getY(), getZ(), 3, 0.1, 0.1, 0.1, 0.01);
            discard();
            return;
        }
        entityData.set(TIME_LEFT, left);
        // Orbit around the owner's shoulders, evenly spaced.
        List<WispEntity> bound = SpiritCommunion.bound(owner);
        int index = Math.max(0, bound.indexOf(this));
        double angle = owner.tickCount * 0.07 + index * Mth.TWO_PI / Math.max(1, bound.size());
        Vec3 target = owner.position().add(Math.cos(angle) * 1.1, owner.getBbHeight() * 0.85 + Math.sin(angle * 2) * 0.1,
                Math.sin(angle) * 1.1);
        moveToward(target, 0.5);
    }

    private void moveToward(Vec3 target, double speed) {
        Vec3 to = target.subtract(position());
        double distance = to.length();
        Vec3 step = distance <= speed ? to : to.scale(speed / distance);
        setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 64 * 64;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // Spirits don't outlast a reload.
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
