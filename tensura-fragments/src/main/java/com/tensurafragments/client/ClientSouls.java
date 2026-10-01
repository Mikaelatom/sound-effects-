package com.tensurafragments.client;

import com.tensurafragments.soul.CapturedSoul;
import com.tensurafragments.soul.SoulBond;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;

/** Soul Reaper on the client: which entities are summoned souls or possessed, their wisps, and the HUD's data. */
public final class ClientSouls {
    /**
     * Summoned souls are drawn in soul blue. The tint multiplies the creature's own colours, so it's a deep blue to turn
     * even warm-coloured creatures ghostly.
     */
    public static final int SUMMON_TINT = 0x5A9CFF;
    private static final int SUMMON_GLOW = 0x7FE8FF;
    private static final int POSSESSED_GLOW = 0xB070FF;
    private static final Int2IntMap ENTITIES = new Int2IntOpenHashMap();
    private static int points;
    private static List<CapturedSoul> souls = List.of();
    private static int selected;

    static {
        ENTITIES.defaultReturnValue(-1);
    }

    private ClientSouls() {
    }

    public static void add(int entityId, int kind) {
        ENTITIES.put(entityId, kind);
    }

    /** {@link SoulBond#KIND_SUMMONED}, {@link SoulBond#KIND_POSSESSED}, or -1. */
    public static int kind(Entity entity) {
        return ENTITIES.isEmpty() ? -1 : ENTITIES.get(entity.getId());
    }

    public static boolean isSummoned(Entity entity) {
        return kind(entity) == SoulBond.KIND_SUMMONED;
    }

    public static int glowColour(Entity entity) {
        return isSummoned(entity) ? SUMMON_GLOW : POSSESSED_GLOW;
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ENTITIES.clear();
            return;
        }
        ClientLevel level = mc.level;
        ENTITIES.int2IntEntrySet().removeIf(entry -> {
            Entity entity = level.getEntity(entry.getIntKey());
            if (entity == null || entity.isRemoved()) {
                return true;
            }
            boolean summoned = entry.getIntValue() == SoulBond.KIND_SUMMONED;
            if (level.random.nextInt(summoned ? 3 : 6) == 0) {
                double x = entity.getX() + (level.random.nextDouble() - 0.5) * entity.getBbWidth();
                double y = entity.getY() + level.random.nextDouble() * entity.getBbHeight();
                double z = entity.getZ() + (level.random.nextDouble() - 0.5) * entity.getBbWidth();
                level.addParticle(summoned ? ParticleTypes.SOUL : ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0, 0.03, 0);
            }
            return false;
        });
    }

    public static void sync(int newPoints, List<CapturedSoul> newSouls, int newSelected) {
        points = newPoints;
        souls = List.copyOf(newSouls);
        selected = newSelected;
    }

    public static int points() {
        return points;
    }

    public static CapturedSoul selected() {
        return souls.isEmpty() ? null : souls.get(Math.floorMod(selected, souls.size()));
    }
}
