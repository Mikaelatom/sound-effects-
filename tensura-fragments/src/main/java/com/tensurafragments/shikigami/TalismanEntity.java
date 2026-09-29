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
    public TalismanEntity(EntityType<? extends TalismanEntity> type, Level level) {
        super(type, level);
    }

    public static TalismanEntity create(ServerPlayer owner) {
        TalismanEntity talisman = new TalismanEntity(ModRegistries.TALISMAN.get(), owner.level());
        talisman.setOwner(owner);
        talisman.setPos(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
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
            Blast.explode(serverLevel, this, getOwner(), position(), Config.TALISMAN_BLAST_RADIUS.get(),
                    Config.TALISMAN_BLAST_DAMAGE.get().floatValue(), Config.BLAST_KNOCKBACK.get(),
                    Config.SELF_DAMAGE_MULTIPLIER.get().floatValue(), 1.0F);
            discard();
        }
    }
}
