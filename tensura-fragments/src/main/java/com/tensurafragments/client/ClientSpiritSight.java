package com.tensurafragments.client;

import com.tensurafragments.yifa.WispEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Spirit Sight on the client: only you see your own bound spirits, and only with Spirit Sight on do you see wild
 * ones. Sight also shows anything invisible nearby as a shimmer of soul sparks.
 */
public final class ClientSpiritSight {
    private static boolean sight;
    private static int jutsu;

    private ClientSpiritSight() {
    }

    public static void update(boolean on, int jutsuElement) {
        sight = on;
        jutsu = jutsuElement;
    }

    public static boolean hasSight() {
        return sight;
    }

    /** 0 fire, 1 wind. */
    public static int jutsu() {
        return jutsu;
    }

    public static boolean canSee(WispEntity wisp) {
        Minecraft mc = Minecraft.getInstance();
        return sight || mc.player != null && wisp.getOwnerId() == mc.player.getId();
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof WispEntity wisp && canSee(wisp) && mc.level.random.nextInt(3) == 0) {
                mc.level.addParticle(wisp.getElement().dust(0.8F), wisp.getX(), wisp.getY(), wisp.getZ(), 0, 0.01, 0);
            } else if (sight && entity instanceof LivingEntity living && living.isInvisible() && living != mc.player
                    && living.distanceTo(mc.player) < 24 && mc.level.random.nextInt(2) == 0) {
                mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                        living.getRandomX(0.6), living.getRandomY(), living.getRandomZ(0.6), 0, 0.01, 0);
            }
        }
    }
}
