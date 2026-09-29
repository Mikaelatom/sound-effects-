package com.tensurafragments.client;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;

/** Which entities the client should draw in rainbow (Rainbow Magic spells), plus their rainbow trails. */
public final class ClientRainbow {
    private static final IntSet ENTITIES = new IntOpenHashSet();

    private ClientRainbow() {
    }

    public static void add(int entityId) {
        ENTITIES.add(entityId);
    }

    public static boolean isRainbow(Entity entity) {
        return !ENTITIES.isEmpty() && ENTITIES.contains(entity.getId());
    }

    /** Hue that sweeps across the model and along the rainbow over time. */
    public static float hue(Entity entity, float partialTick) {
        return ((entity.tickCount + partialTick) * 0.03F) % 1F;
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
            int rgb = Mth.hsvToRgb(hue(entity, 0), 0.8F, 1.0F);
            DustParticleOptions dust = new DustParticleOptions(
                    new Vector3f(((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F, (rgb & 0xFF) / 255F), 1.2F);
            mc.level.addParticle(dust, entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ(), 0, 0, 0);
            return false;
        });
    }
}
