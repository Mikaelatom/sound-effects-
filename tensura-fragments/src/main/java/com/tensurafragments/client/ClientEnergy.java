package com.tensurafragments.client;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;

/** Which entities the client should draw as glowing green energy spells, plus their green trails. */
public final class ClientEnergy {
    private static final IntSet ENTITIES = new IntOpenHashSet();
    private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.45F, 1.0F, 0.35F), 1.3F);

    private ClientEnergy() {
    }

    public static void add(int entityId) {
        ENTITIES.add(entityId);
    }

    public static boolean isEnergy(Entity entity) {
        return !ENTITIES.isEmpty() && ENTITIES.contains(entity.getId());
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ENTITIES.clear();
            return;
        }
        ENTITIES.removeIf(id -> {
            Entity entity = mc.level.getEntity(id);
            if (entity == null || entity.isRemoved()) {
                return true;
            }
            double y = entity.getY() + entity.getBbHeight() / 2;
            mc.level.addParticle(GREEN, entity.getX(), y, entity.getZ(), 0, 0, 0);
            if (mc.level.random.nextInt(3) == 0) {
                mc.level.addParticle(ParticleTypes.HAPPY_VILLAGER, entity.getX(), y, entity.getZ(), 0, 0, 0);
            }
            return false;
        });
    }
}
