package com.tensurafragments.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tensurafragments.ModRegistries;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Marks a creature serving a Soul Reaper: a summoned soul (gone when its time is up) or a creature possessed by souls
 * (yours for good). {@code stacks} is how many souls have possessed it. A summoned soul remembers which captured soul it
 * is, so recalling it gives that soul back.
 */
public record SoulBond(UUID owner, long until, boolean summoned, int stacks, @Nullable CapturedSoul soul) {
    public static final Codec<SoulBond> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("owner").forGetter(SoulBond::owner),
            Codec.LONG.fieldOf("until").forGetter(SoulBond::until),
            Codec.BOOL.fieldOf("summoned").forGetter(SoulBond::summoned),
            Codec.INT.fieldOf("stacks").forGetter(SoulBond::stacks),
            CapturedSoul.CODEC.optionalFieldOf("soul").forGetter(bond -> Optional.ofNullable(bond.soul()))
    ).apply(i, (owner, until, summoned, stacks, soul) -> new SoulBond(owner, until, summoned, stacks, soul.orElse(null))));

    /** What the client is told, to draw it: a ghostly summoned soul, or a possessed creature. */
    public static final int KIND_SUMMONED = 0;
    public static final int KIND_POSSESSED = 1;

    @Nullable
    public static SoulBond get(Entity entity) {
        return entity.hasData(ModRegistries.SOUL_BOND) ? entity.getData(ModRegistries.SOUL_BOND) : null;
    }

    public static boolean isBoundTo(@Nullable Entity entity, Entity owner) {
        SoulBond bond = entity == null ? null : get(entity);
        return bond != null && bond.owner().equals(owner.getUUID());
    }

    public int kind() {
        return summoned && stacks == 0 ? KIND_SUMMONED : KIND_POSSESSED;
    }
}
