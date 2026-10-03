package com.tensurafragments.shikigami;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.ally.Companions;
import com.tensurafragments.ally.OwnersTargetGoal;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A block brought to life with a paper talisman. Its stats come from the block: hard blocks make slow, tough
 * shikigami that hit hard; soft blocks make quick, fragile ones. It turns back into its block when it dies or
 * its time runs out.
 */
public class ShikigamiEntity extends TamableAnimal {
    private static final EntityDataAccessor<BlockState> BLOCK =
            SynchedEntityData.defineId(ShikigamiEntity.class, EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<Integer> LIFETIME =
            SynchedEntityData.defineId(ShikigamiEntity.class, EntityDataSerializers.INT);
    /** Made with a leaf instead of paper (weaker; the talisman on it is drawn green). */
    private static final EntityDataAccessor<Boolean> LEAF =
            SynchedEntityData.defineId(ShikigamiEntity.class, EntityDataSerializers.BOOLEAN);

    private boolean reverted;

    public ShikigamiEntity(EntityType<? extends ShikigamiEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.ATTACK_DAMAGE, 3)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ARMOR, 0)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0);
    }

    /** Hardness used for stats, capped so obsidian-class blocks don't make invincible shikigami. */
    public static float effectiveHardness(float destroySpeed) {
        return Mth.clamp(destroySpeed, 0.3F, 10.0F);
    }

    /** @param potency 1 for a paper talisman, less for leaves: scales health, damage and lifetime */
    public static ShikigamiEntity create(ServerPlayer owner, BlockState state, float hardness, BlockPos at, float potency) {
        ShikigamiEntity shikigami = new ShikigamiEntity(ModRegistries.SHIKIGAMI.get(), owner.level());
        shikigami.entityData.set(BLOCK, state);
        shikigami.entityData.set(LIFETIME, Math.max(20, Math.round(Config.SHIKIGAMI_LIFETIME_TICKS.get() * potency)));
        shikigami.entityData.set(LEAF, potency < 1);
        shikigami.setTame(true, false);
        shikigami.setOwnerUUID(owner.getUUID());
        shikigami.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, owner.getYRot() + 180, 0);

        float h = effectiveHardness(hardness);
        double strength = Config.SHIKIGAMI_STRENGTH.get() * potency;
        shikigami.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.max(2, (10 + 6 * h) * strength));
        shikigami.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue((2 + 1.2 * h) * strength);
        shikigami.getAttribute(Attributes.ARMOR).setBaseValue(Math.min(20, 2 * h));
        shikigami.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(Mth.clamp(0.38 - 0.02 * h, 0.18, 0.38));
        shikigami.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(Math.min(1.0, h / 10.0));
        shikigami.setHealth(shikigami.getMaxHealth());
        return shikigami;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BLOCK, Blocks.STONE.defaultBlockState());
        builder.define(LIFETIME, 2400);
        builder.define(LEAF, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.0, 6.0F, 2.0F));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        // Only what its owner hits, nothing else.
        targetSelector.addGoal(1, new OwnersTargetGoal(this));
    }

    public BlockState getBlock() {
        return entityData.get(BLOCK);
    }

    public int getTicksLeft() {
        return Math.max(0, entityData.get(LIFETIME) - tickCount);
    }

    public boolean isLeaf() {
        return entityData.get(LEAF);
    }

    public int getLifetime() {
        return entityData.get(LIFETIME);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (Companions.isNamed(this)) {
            // Named: no time limit, and it waits for you while you're away.
            return;
        }
        LivingEntity owner = getOwner();
        if (tickCount >= getLifetime() || owner == null || !owner.isAlive() || owner.level() != level()) {
            revert();
        }
    }

    /** Turns back into its block (dropped as an item) and disappears. */
    public void revert() {
        if (reverted || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        reverted = true;
        dropBlock();
        serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, getBlock()),
                getX(), getY() + 0.5, getZ(), 30, 0.3, 0.3, 0.3, 0.1);
        serverLevel.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.5, getZ(), 8, 0.3, 0.3, 0.3, 0.02);
        playSound(TensuraSoundEvents.GENERIC_UNCAST.get(), 0.8F, 1.0F);
        discard();
    }

    private void dropBlock() {
        ItemStack stack = new ItemStack(getBlock().getBlock());
        if (!stack.isEmpty()) {
            spawnAtLocation(stack);
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        if (!reverted) {
            reverted = true;
            dropBlock();
        }
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (target instanceof ShikigamiEntity other && other.getOwnerUUID() != null
                && other.getOwnerUUID().equals(getOwnerUUID())) {
            return false;
        }
        return super.wantsToAttack(target, owner);
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
    protected SoundEvent getAmbientSound() {
        return TensuraSoundEvents.GOLEM_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return TensuraSoundEvents.GOLEM_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return TensuraSoundEvents.GOLEM_DEATH.get();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Block", NbtUtils.writeBlockState(getBlock()));
        tag.putInt("Lifetime", getLifetime());
        tag.putBoolean("Leaf", isLeaf());
        tag.putInt("Age", tickCount);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Block")) {
            entityData.set(BLOCK, NbtUtils.readBlockState(level().holderLookup(Registries.BLOCK), tag.getCompound("Block")));
        }
        if (tag.contains("Lifetime")) {
            entityData.set(LIFETIME, tag.getInt("Lifetime"));
        }
        entityData.set(LEAF, tag.getBoolean("Leaf"));
        tickCount = tag.getInt("Age");
    }
}
