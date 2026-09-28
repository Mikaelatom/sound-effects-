package com.tensurafragments.card;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.particle.TensuraParticleUtils;
import net.minecraft.core.Direction;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown card. It flies until it hits something, then sticks to the block or entity.
 * It charges up while it exists and can be teleported to or detonated by its owner.
 */
public class CardEntity extends Projectile {
    private static final EntityDataAccessor<Boolean> STUCK =
            SynchedEntityData.defineId(CardEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Direction> FACE =
            SynchedEntityData.defineId(CardEntity.class, EntityDataSerializers.DIRECTION);
    private static final EntityDataAccessor<Integer> STUCK_ENTITY =
            SynchedEntityData.defineId(CardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFETIME =
            SynchedEntityData.defineId(CardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FULL_CHARGE =
            SynchedEntityData.defineId(CardEntity.class, EntityDataSerializers.INT);

    private Vec3 stuckOffset = Vec3.ZERO;
    private int fuse = -1;
    private boolean exploded;

    public CardEntity(EntityType<? extends CardEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public static CardEntity create(ServerPlayer owner) {
        CardEntity card = new CardEntity(ModRegistries.CARD.get(), owner.level());
        card.setOwner(owner);
        card.setPos(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
        card.entityData.set(LIFETIME, Config.CARD_LIFETIME_TICKS.get());
        card.entityData.set(FULL_CHARGE, Config.FULL_CHARGE_TICKS.get());
        return card;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STUCK, false);
        builder.define(FACE, Direction.UP);
        builder.define(STUCK_ENTITY, -1);
        builder.define(LIFETIME, 400);
        builder.define(FULL_CHARGE, 100);
    }

    public boolean isStuck() {
        return entityData.get(STUCK);
    }

    public Direction getFace() {
        return entityData.get(FACE);
    }

    public boolean isStuckToEntity() {
        return entityData.get(STUCK_ENTITY) >= 0;
    }

    public int getLifetime() {
        return entityData.get(LIFETIME);
    }

    public int getTicksLeft() {
        return Math.max(0, getLifetime() - tickCount);
    }

    /** 0.5 when thrown, rising to 1.5 at full charge. */
    public float getCharge() {
        return 0.5F + Mth.clamp(tickCount / (float) entityData.get(FULL_CHARGE), 0.0F, 1.0F);
    }

    public boolean isFullyCharged() {
        return tickCount >= entityData.get(FULL_CHARGE);
    }

    @Override
    public void tick() {
        super.tick();

        if (isStuck()) {
            if (isStuckToEntity()) {
                followStuckEntity();
            }
        } else {
            fly();
        }

        if (level().isClientSide) {
            if (!isStuck()) {
                level().addParticle(ParticleTypes.ENCHANT, getX(), getY(), getZ(), 0, 0, 0);
            } else if (isFullyCharged() && random.nextInt(6) == 0) {
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 0, 0.05, 0);
            }
            return;
        }

        Entity owner = getOwner();
        if (owner == null || !owner.isAlive() || owner.level() != level()) {
            fizzle();
            return;
        }
        if (fuse > 0 && --fuse == 0) {
            detonate();
            return;
        }
        if (tickCount >= getLifetime()) {
            fizzle();
        }
    }

    private void fly() {
        Vec3 motion = getDeltaMovement();
        if (!level().isClientSide) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                onHit(hit);
                return;
            }
        }
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        setDeltaMovement(motion.scale(0.99).add(0, -0.02, 0));

        double horizontal = motion.horizontalDistance();
        setYRot((float) (Mth.atan2(motion.x, motion.z) * Mth.RAD_TO_DEG));
        setXRot((float) (Mth.atan2(motion.y, horizontal) * Mth.RAD_TO_DEG));
    }

    private void followStuckEntity() {
        Entity target = level().getEntity(entityData.get(STUCK_ENTITY));
        if (target == null || !target.isAlive()) {
            if (!level().isClientSide) {
                // The thing we were stuck to is gone; hang in the air where it was.
                entityData.set(STUCK_ENTITY, -1);
            }
            return;
        }
        setPos(target.position().add(stuckOffset));
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return !(target instanceof CardEntity) && super.canHitEntity(target);
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        Direction face = hit.getDirection();
        Vec3 pos = hit.getLocation().add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.05));
        setPos(pos);
        stick(face);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        Entity target = hit.getEntity();
        AABB box = target.getBoundingBox();
        Vec3 clamped = new Vec3(
                Mth.clamp(getX(), box.minX, box.maxX),
                Mth.clamp(getY(), box.minY, box.maxY),
                Mth.clamp(getZ(), box.minZ, box.maxZ));
        stuckOffset = clamped.subtract(target.position());
        setPos(clamped);
        entityData.set(STUCK_ENTITY, target.getId());
        stick(Direction.UP);
    }

    private void stick(Direction face) {
        setDeltaMovement(Vec3.ZERO);
        entityData.set(FACE, face);
        entityData.set(STUCK, true);
        level().playSound(null, getX(), getY(), getZ(), ModRegistries.CARD_PLACE.get(), SoundSource.PLAYERS,
                0.8F, 0.9F + random.nextFloat() * 0.2F);
    }

    /** Arms the card so it detonates after {@code ticks} ticks. Used for chain reactions. */
    public void prime(int ticks) {
        if (!exploded && (fuse < 0 || ticks < fuse)) {
            fuse = Math.max(1, ticks);
        }
    }

    public void detonate() {
        if (exploded || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        exploded = true;

        Entity owner = getOwner();
        Vec3 centre = position();
        double radius = Config.BLAST_RADIUS.get();
        float damage = (float) (Config.BLAST_DAMAGE.get() * getCharge());
        DamageSource source = damageSources().explosion(this, owner);
        DamageSource selfSource = damageSources().explosion(this, null);

        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(radius))) {
            Vec3 body = victim.position().add(0, victim.getBbHeight() / 2.0, 0);
            double distance = body.distanceTo(centre);
            if (distance > radius) {
                continue;
            }
            float falloff = (float) (1.0 - distance / radius);
            float amount = damage * falloff;
            if (victim == owner) {
                // Too close to your own card and you eat the blast too.
                amount *= Config.SELF_DAMAGE_MULTIPLIER.get().floatValue();
            }
            if (amount > 0) {
                // Self damage has no attacker, so it still applies on servers with PvP turned off.
                victim.hurt(victim == owner ? selfSource : source, amount);
            }

            Vec3 push = body.subtract(centre);
            push = push.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : push.normalize();
            push = push.scale(Config.BLAST_KNOCKBACK.get() * falloff * getCharge());
            victim.push(push.x, push.y + 0.2 * falloff, push.z);
            if (victim instanceof ServerPlayer player) {
                player.hurtMarked = true;
            }
        }

        // Other cards in the blast go off a few ticks later.
        for (CardEntity other : level().getEntitiesOfClass(CardEntity.class, new AABB(centre, centre).inflate(radius))) {
            if (other != this && other.distanceTo(this) <= radius) {
                other.prime(4);
            }
        }

        serverLevel.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 1 + Math.round(getCharge() * 2), 0.5, 0.5, 0.5, 0);
        // Tensura's shockwave ring, sized to the blast.
        TensuraParticleHelper.spawnServerParticles(serverLevel,
                TensuraParticleUtils.getColorlessReversedWave(0.9F, (float) radius), getX(), getY(), getZ());
        serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY(), getZ(), 30, 0.3, 0.3, 0.3, 0.6);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                1.0F + getCharge() * 0.5F, 1.2F);
        discard();
    }

    public void fizzle() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 8, 0.1, 0.1, 0.1, 0.01);
        }
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Stuck", isStuck());
        tag.putInt("Face", getFace().get3DDataValue());
        tag.putInt("Lifetime", getLifetime());
        tag.putInt("FullCharge", entityData.get(FULL_CHARGE));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(STUCK, tag.getBoolean("Stuck"));
        entityData.set(FACE, Direction.from3DDataValue(tag.getInt("Face")));
        if (tag.contains("Lifetime")) {
            entityData.set(LIFETIME, tag.getInt("Lifetime"));
        }
        if (tag.contains("FullCharge")) {
            entityData.set(FULL_CHARGE, tag.getInt("FullCharge"));
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }
}
