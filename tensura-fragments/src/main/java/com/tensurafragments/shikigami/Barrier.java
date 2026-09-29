package com.tensurafragments.shikigami;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.skill.Magicules;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A talisman barrier: a wall running around three or more anchors. While it's up it pushes hostile mobs out
 * (burning them a little), destroys projectiles that aren't yours, and drains magicules every second.
 */
public final class Barrier {
    private static final Map<UUID, long[]> LEADER_CACHE = new HashMap<>();

    private Barrier() {
    }

    /** The owner's anchors in their current dimension, in placement order. */
    public static List<BarrierAnchorEntity> anchors(ServerPlayer owner) {
        List<BarrierAnchorEntity> anchors = new ArrayList<>(owner.serverLevel().getEntities(ModRegistries.BARRIER_ANCHOR.get(),
                anchor -> anchor.isAlive() && anchor.isOwnedBy(owner)));
        anchors.sort(Comparator.comparingInt(BarrierAnchorEntity::getOrder));
        return anchors;
    }

    public static boolean isUp(ServerPlayer owner) {
        return anchors(owner).stream().anyMatch(BarrierAnchorEntity::isActive);
    }

    /** Placement order of the anchor that runs the barrier (cached per tick). */
    static int leaderOrder(ServerPlayer owner) {
        long now = owner.level().getGameTime();
        long[] cached = LEADER_CACHE.get(owner.getUUID());
        if (cached == null || cached[0] != now) {
            List<BarrierAnchorEntity> anchors = anchors(owner);
            cached = new long[] {now, anchors.isEmpty() ? -1 : anchors.get(0).getOrder()};
            LEADER_CACHE.put(owner.getUUID(), cached);
        }
        return (int) cached[1];
    }

    /** Links the anchors into a loop (sorted around their centre so the wall doesn't cross itself). */
    public static boolean raise(ServerPlayer owner) {
        List<BarrierAnchorEntity> anchors = anchors(owner);
        if (anchors.size() < 3) {
            owner.displayClientMessage(Component.translatable("tensurafragments.shikigami.barrier_needs_anchors", 3), true);
            return false;
        }
        Vec3 centre = centroid(anchors);
        anchors.sort(Comparator.comparingDouble(a -> Math.atan2(a.getZ() - centre.z, a.getX() - centre.x)));
        for (int i = 0; i < anchors.size(); i++) {
            anchors.get(i).link(anchors.get((i + 1) % anchors.size()));
        }
        owner.level().playSound(null, centre.x, centre.y, centre.z, TensuraSoundEvents.CAST_SPACE.get(),
                SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    public static void dispel(ServerPlayer owner) {
        List<BarrierAnchorEntity> anchors = anchors(owner);
        if (anchors.isEmpty()) {
            return;
        }
        Vec3 centre = centroid(anchors);
        anchors.forEach(BarrierAnchorEntity::crumble);
        owner.level().playSound(null, centre.x, centre.y, centre.z, TensuraSoundEvents.BARRIER_BREAK.get(),
                SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** Runs once per tick, on the leading anchor, while the barrier is up. */
    static void tick(ServerPlayer owner, BarrierAnchorEntity leader) {
        List<BarrierAnchorEntity> anchors = anchors(owner);
        double potency = anchors.stream().mapToDouble(BarrierAnchorEntity::getPotency).average().orElse(1);
        if (anchors.size() < 3 || leader.activeTicks > Config.BARRIER_DURATION_TICKS.get() * potency) {
            dispel(owner);
            return;
        }
        if (leader.activeTicks % 20 == 0 && !Magicules.trySpend(owner, Config.BARRIER_UPKEEP_PER_SECOND.get())) {
            owner.displayClientMessage(Component.translatable("tensurafragments.shikigami.barrier_collapsed"), true);
            dispel(owner);
            return;
        }

        List<Vec3> polygon = new ArrayList<>();
        BarrierAnchorEntity start = anchors.get(0);
        BarrierAnchorEntity current = start;
        do {
            polygon.add(current.position());
            current = current.getNext();
        } while (current != null && current != start && polygon.size() <= anchors.size());

        double minY = anchors.stream().mapToDouble(Entity::getY).min().orElse(leader.getY()) - 0.5;
        double maxY = anchors.stream().mapToDouble(Entity::getY).max().orElse(leader.getY()) + leader.getWallHeight();
        AABB box = bounds(polygon, minY, maxY);
        Vec3 centre = centroid(anchors);
        ServerLevel level = owner.serverLevel();
        boolean damageTick = leader.activeTicks % 20 == 0;

        for (Entity entity : level.getEntities((Entity) null, box, e -> e.isAlive() && !(e instanceof BarrierAnchorEntity))) {
            if (!contains(polygon, entity.getX(), entity.getZ()) || entity.getY() > maxY || entity.getY() + entity.getBbHeight() < minY) {
                continue;
            }
            if (entity instanceof Projectile projectile) {
                if (!isFriendly(projectile.getOwner(), owner)) {
                    level.sendParticles(ParticleTypes.ENCHANTED_HIT, entity.getX(), entity.getY(), entity.getZ(), 6, 0.1, 0.1, 0.1, 0.1);
                    entity.discard();
                }
            } else if (entity instanceof LivingEntity living && isHostile(living, owner)) {
                Vec3 away = new Vec3(entity.getX() - centre.x, 0, entity.getZ() - centre.z);
                away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
                entity.setDeltaMovement(away.x * 0.6, 0.25, away.z * 0.6);
                entity.hurtMarked = true;
                if (damageTick && Config.BARRIER_DAMAGE_PER_SECOND.get() > 0) {
                    living.hurt(level.damageSources().indirectMagic(leader, owner), Config.BARRIER_DAMAGE_PER_SECOND.get().floatValue());
                }
            }
        }
    }

    private static boolean isFriendly(Entity entity, ServerPlayer owner) {
        return entity == owner || (entity instanceof OwnableEntity ownable && owner.getUUID().equals(ownable.getOwnerUUID()));
    }

    private static boolean isHostile(LivingEntity living, ServerPlayer owner) {
        if (isFriendly(living, owner)) {
            return false;
        }
        return living instanceof Enemy || (living instanceof Mob mob && mob.getTarget() == owner);
    }

    /** Ray casting point-in-polygon test on the XZ plane. */
    static boolean contains(List<Vec3> polygon, double x, double z) {
        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            Vec3 a = polygon.get(i);
            Vec3 b = polygon.get(j);
            if ((a.z > z) != (b.z > z) && x < (b.x - a.x) * (z - a.z) / (b.z - a.z) + a.x) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static AABB bounds(List<Vec3> polygon, double minY, double maxY) {
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Vec3 p : polygon) {
            minX = Math.min(minX, p.x);
            minZ = Math.min(minZ, p.z);
            maxX = Math.max(maxX, p.x);
            maxZ = Math.max(maxZ, p.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Vec3 centroid(List<BarrierAnchorEntity> anchors) {
        Vec3 sum = Vec3.ZERO;
        for (BarrierAnchorEntity anchor : anchors) {
            sum = sum.add(anchor.position());
        }
        return sum.scale(1.0 / anchors.size());
    }
}
