package com.tensurafragments.shikigami;

import com.tensurafragments.Config;
import com.tensurafragments.ally.Companions;
import com.tensurafragments.ally.OwnersTargetGoal;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A paper beast folded by Shikigami Control. On its own it follows you and fights for you; while you possess it
 * ({@link PaperBeasts}) you see through its eyes and steer it yourself. It's paper: fire tears through it, falls don't.
 */
public class PaperBeastEntity extends TamableAnimal implements GeoEntity {
    private static final EntityDataAccessor<Boolean> CONTROLLED =
            SynchedEntityData.defineId(PaperBeastEntity.class, EntityDataSerializers.BOOLEAN);
    /** Glide left for the winged cat, synced for the HUD. */
    private static final EntityDataAccessor<Integer> GLIDE =
            SynchedEntityData.defineId(PaperBeastEntity.class, EntityDataSerializers.INT);
    /** How long the winged cat can glide before it has to land again. */
    public static final int GLIDE_TICKS = 60;
    private static final int ATTACK_COOLDOWN = 10;

    private final BeastKind kind;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Steering from the possessing player (server side).
    private float inForward;
    private float inStrafe;
    private boolean inJump;
    private boolean inDown;
    private int glide = GLIDE_TICKS;
    private int attackCooldown;
    private boolean folded;

    public PaperBeastEntity(EntityType<? extends PaperBeastEntity> type, Level level) {
        super(type, level);
        kind = BeastKind.of(type);
        if (kind.flies()) {
            moveControl = new FlyingMoveControl(this, 20, true);
            setNoGravity(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes(BeastKind kind) {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, kind.health())
                .add(Attributes.ATTACK_DAMAGE, kind.damage())
                .add(Attributes.MOVEMENT_SPEED, kind.speed())
                .add(Attributes.FLYING_SPEED, kind.flies() ? kind.speed() * 1.6 : 0.4)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.STEP_HEIGHT, kind.flies() ? 0.6 : 1.0);
    }

    public static PaperBeastEntity create(Player owner, BeastKind kind, Vec3 at, float potency) {
        PaperBeastEntity beast = new PaperBeastEntity(kind.type(), owner.level());
        beast.setTame(true, false);
        beast.setOwnerUUID(owner.getUUID());
        beast.moveTo(at.x, at.y, at.z, owner.getYRot(), 0);
        double strength = Config.PAPER_BEAST_STRENGTH.get() * potency;
        beast.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.max(2, kind.health() * strength));
        beast.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(kind.damage() * strength);
        beast.setHealth(beast.getMaxHealth());
        return beast;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        if (BeastKind.of(getType()).flies()) {
            FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
            navigation.setCanOpenDoors(false);
            navigation.setCanFloat(true);
            return navigation;
        }
        return super.createNavigation(level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CONTROLLED, false);
        builder.define(GLIDE, GLIDE_TICKS);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.3, true));
        goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.1, 6.0F, 2.0F));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        // Only what its owner hits, nothing else.
        targetSelector.addGoal(1, new OwnersTargetGoal(this));
    }

    public BeastKind getKind() {
        return kind;
    }

    public boolean isControlled() {
        return entityData.get(CONTROLLED);
    }

    void setControlled(boolean controlled) {
        entityData.set(CONTROLLED, controlled);
        inForward = 0;
        inStrafe = 0;
        inJump = false;
        inDown = false;
        // Its own mind sleeps while you're in it.
        for (Goal.Flag flag : Goal.Flag.values()) {
            goalSelector.setControlFlag(flag, !controlled);
            targetSelector.setControlFlag(flag, !controlled);
        }
        if (controlled) {
            setTarget(null);
            getNavigation().stop();
        }
    }

    /**
     * Mobs turn their movement and looking goals back on every 5 ticks; while possessed they stay off, or following its
     * owner would teleport it back to your body whenever it got 12 blocks away.
     */
    @Override
    protected void updateControlFlags() {
        if (!isControlled()) {
            super.updateControlFlags();
        }
    }

    /** Glide left for the winged cat, 0 to 1. */
    public float glideFraction() {
        return entityData.get(GLIDE) / (float) GLIDE_TICKS;
    }

    /** Steering from the player seeing through this beast. */
    void steer(float forward, float strafe, boolean jump, boolean down, float yaw, float pitch) {
        inForward = Mth.clamp(forward, -1, 1);
        inStrafe = Mth.clamp(strafe, -1, 1);
        inJump = jump;
        inDown = down;
        setYRot(Mth.wrapDegrees(yaw));
        setXRot(Mth.clamp(pitch, -90, 90));
        yHeadRot = getYRot();
        yBodyRot = getYRot();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (isControlled()) {
            getNavigation().stop();
            applySteering();
        }
    }

    /** While steered, only its own velocity moves it, never its AI's walking input. */
    @Override
    public void travel(Vec3 input) {
        super.travel(isControlled() ? Vec3.ZERO : input);
    }

    /** Moves the way the possessing player steers: straight velocity, so it feels direct. */
    private void applySteering() {
        xxa = 0;
        zza = 0;
        jumping = false;
        double speed = kind.controlledSpeed();
        Vec3 flat = Vec3.directionFromRotation(0, getYRot());
        Vec3 left = new Vec3(flat.z, 0, -flat.x);
        if (kind.flies()) {
            Vec3 forward = Vec3.directionFromRotation(getXRot(), getYRot());
            Vec3 move = forward.scale(inForward).add(left.scale(inStrafe));
            double vertical = (inJump ? 1 : 0) - (inDown ? 1 : 0);
            move = move.add(0, vertical, 0);
            if (move.lengthSqr() > 1) {
                move = move.normalize();
            }
            setDeltaMovement(move.scale(speed));
            return;
        }
        Vec3 move = flat.scale(inForward).add(left.scale(inStrafe));
        if (move.lengthSqr() > 1) {
            move = move.normalize();
        }
        double y = getDeltaMovement().y;
        if (onGround()) {
            glide = GLIDE_TICKS;
            if (inJump) {
                y = kind.jump();
            }
        } else if (kind == BeastKind.CAT && inJump && glide > 0 && y < 0) {
            // Wings out: fall slowly and carry forward faster.
            glide--;
            y = Math.max(y, -0.06);
            speed *= 1.4;
        }
        setDeltaMovement(move.x * speed, y, move.z * speed);
    }

    /** The possessing player's attack. Returns whether anything was hit. */
    boolean controlledAttack() {
        if (attackCooldown > 0) {
            return false;
        }
        attackCooldown = ATTACK_COOLDOWN;
        playAttack();
        LivingEntity target = targetInFront(kind == BeastKind.RABBIT ? 3.0 : 2.5);
        if (target == null) {
            if (kind == BeastKind.HOUND || kind == BeastKind.RABBIT) {
                // Lunge (hound) or horn charge (rabbit) the way it's facing.
                Vec3 dash = Vec3.directionFromRotation(0, getYRot()).scale(kind == BeastKind.RABBIT ? 1.1 : 0.9);
                setDeltaMovement(dash.x, onGround() ? 0.3 : getDeltaMovement().y, dash.z);
                hasImpulse = true;
            }
            return false;
        }
        strike(target);
        return true;
    }

    @Nullable
    private LivingEntity targetInFront(double reach) {
        Vec3 eye = getEyePosition();
        Vec3 end = eye.add(getLookAngle().scale(reach));
        AABB area = getBoundingBox().expandTowards(getLookAngle().scale(reach)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level(), this, eye, end, area,
                e -> e instanceof LivingEntity living && living.isAlive() && canHit(living));
        if (hit != null && hit.getEntity() instanceof LivingEntity living) {
            return living;
        }
        // Forgiving aim: the nearest foe within a small cone.
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : level().getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && canHit(e))) {
            Vec3 to = candidate.getBoundingBox().getCenter().subtract(eye);
            double distance = to.length();
            if (distance <= reach + candidate.getBbWidth() && to.normalize().dot(getLookAngle()) > 0.6
                    && distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean canHit(LivingEntity target) {
        if (target == this) {
            return false;
        }
        LivingEntity owner = getOwner();
        if (owner != null && (target == owner || Spell.isAlly(target, owner))) {
            return false;
        }
        return !(target instanceof PaperBeastEntity beast && beast.getOwnerUUID() != null
                && beast.getOwnerUUID().equals(getOwnerUUID()));
    }

    /** Hits the target the way this beast fights. */
    private void strike(LivingEntity target) {
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        DamageSource source = damageSources().mobAttack(this);
        switch (kind) {
            case OWL -> {
                target.hurt(source, damage);
                // Marked: you can see it through walls for 10 seconds.
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false), this);
            }
            case HOUND -> {
                target.hurt(source, damage);
                target.knockback(0.6, getX() - target.getX(), getZ() - target.getZ());
            }
            case CAT -> {
                target.hurt(source, damage);
                target.invulnerableTime = 0;
                target.hurt(source, damage);
            }
            case RABBIT -> {
                // Momentum: the horn hits harder the faster the rabbit is moving.
                double moving = getDeltaMovement().length();
                target.hurt(source, damage * (float) (1 + 4 * moving));
                target.knockback(0.4 + moving, getX() - target.getX(), getZ() - target.getZ());
            }
        }
        playSound(kind == BeastKind.HOUND ? SoundEvents.WOLF_GROWL : SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 1.6F);
    }

    private void playAttack() {
        if (kind.attackAnim() != null) {
            triggerAnim("main", "attack");
        }
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!(target instanceof LivingEntity living) || attackCooldown > 0 || !canHit(living)) {
            return false;
        }
        attackCooldown = ATTACK_COOLDOWN;
        playAttack();
        strike(living);
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (entityData.get(GLIDE) != glide) {
            entityData.set(GLIDE, glide);
        }
        LivingEntity owner = getOwner();
        if (!Companions.isNamed(this) && (owner == null || !owner.isAlive() || owner.level() != level())) {
            unfold();
        }
    }

    /** Falls back into scraps of paper and disappears. */
    public void unfold() {
        if (folded || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        folded = true;
        paperBurst(serverLevel);
        discard();
    }

    private void paperBurst(ServerLevel level) {
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PAPER)),
                getX(), getY() + getBbHeight() / 2, getZ(), 20, 0.3, 0.3, 0.3, 0.08);
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() / 2, getZ(), 6, 0.2, 0.2, 0.2, 0.02);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BOOK_PAGE_TURN, getSoundSource(), 1.0F, 0.7F);
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel serverLevel && !folded) {
            folded = true;
            paperBurst(serverLevel);
        }
        super.die(source);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // It's paper.
        if (source.is(DamageTypeTags.IS_FIRE)) {
            amount *= 3;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        return canHit(target) && super.wantsToAttack(target, owner);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    @Nullable
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return null;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return switch (kind) {
            case OWL -> SoundEvents.PARROT_AMBIENT;
            case HOUND -> SoundEvents.WOLF_AMBIENT;
            case CAT -> SoundEvents.CAT_AMBIENT;
            case RABBIT -> SoundEvents.RABBIT_AMBIENT;
        };
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.3F;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.BOOK_PAGE_TURN;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BOOK_PUT;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        RawAnimation idle = RawAnimation.begin().thenLoop(kind.idleAnim());
        RawAnimation move = RawAnimation.begin().thenLoop(kind.moveAnim());
        RawAnimation air = RawAnimation.begin().thenLoop(kind.airAnim());
        AnimationController<PaperBeastEntity> main = new AnimationController<>(this, "main", 3, state -> {
            if (!kind.flies() && !onGround() && !isInWater() && kind == BeastKind.CAT) {
                return state.setAndContinue(air);
            }
            return state.setAndContinue(state.isMoving() ? move : idle);
        });
        if (kind.attackAnim() != null) {
            main.triggerableAnim("attack", RawAnimation.begin().thenPlay(kind.attackAnim()));
        }
        controllers.add(main);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** The owner's UUID, or null. Here for {@link PaperBeasts}. */
    @Nullable
    UUID owner() {
        return getOwnerUUID();
    }
}
