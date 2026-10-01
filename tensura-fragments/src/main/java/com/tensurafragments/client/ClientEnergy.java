package com.tensurafragments.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Which entities the client should draw as glowing green energy spells, plus their green trails and bursts. */
public final class ClientEnergy {
    /** Energy entities and where each was last seen, so the burst goes off where it ended. */
    private static final Int2ObjectMap<Vec3> ENTITIES = new Int2ObjectOpenHashMap<>();
    private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.45F, 1.0F, 0.35F), 1.6F);
    /** The outline energy spells glow with. */
    public static final int GLOW_COLOUR = 0x6EFF5A;

    private ClientEnergy() {
    }

    public static void add(int entityId) {
        ENTITIES.putIfAbsent(entityId, null);
    }

    public static boolean isEnergy(Entity entity) {
        return !ENTITIES.isEmpty() && ENTITIES.containsKey(entity.getId());
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ENTITIES.clear();
            return;
        }
        ClientLevel level = mc.level;
        ENTITIES.int2ObjectEntrySet().removeIf(entry -> {
            Entity entity = level.getEntity(entry.getIntKey());
            if (entity == null || entity.isRemoved()) {
                if (entry.getValue() != null) {
                    burst(level, entry.getValue());
                }
                return true;
            }
            Vec3 centre = entity.position().add(0, entity.getBbHeight() / 2, 0);
            entry.setValue(centre);
            for (int i = 0; i < 3; i++) {
                level.addParticle(GREEN, centre.x + level.random.nextGaussian() * 0.15, centre.y + level.random.nextGaussian() * 0.15,
                        centre.z + level.random.nextGaussian() * 0.15, 0, 0, 0);
            }
            if (level.random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.HAPPY_VILLAGER, centre.x, centre.y, centre.z, 0, 0, 0);
            }
            return false;
        });
    }

    /** A spray of green sparks where an energy spell lands. */
    private static void burst(ClientLevel level, Vec3 at) {
        for (int i = 0; i < 24; i++) {
            double dx = level.random.nextGaussian() * 0.25;
            double dy = level.random.nextGaussian() * 0.25;
            double dz = level.random.nextGaussian() * 0.25;
            level.addParticle(i % 3 == 0 ? ParticleTypes.HAPPY_VILLAGER : GREEN, at.x + dx * 2, at.y + dy * 2, at.z + dz * 2,
                    dx, dy, dz);
        }
        level.addParticle(ParticleTypes.FLASH, at.x, at.y, at.z, 0, 0, 0);
    }
}
