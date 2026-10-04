package com.tensurafragments.rune;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/** A rune inscribed on a weapon, and how many hits it has left. */
public record WeaponRune(String id, int charges) {
    public static final Codec<WeaponRune> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("rune").forGetter(WeaponRune::id),
            Codec.INT.fieldOf("charges").forGetter(WeaponRune::charges)
    ).apply(i, WeaponRune::new));
    public static final StreamCodec<ByteBuf, WeaponRune> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, WeaponRune::id, ByteBufCodecs.VAR_INT, WeaponRune::charges, WeaponRune::new);

    @Nullable
    public Rune rune() {
        return Rune.byId(id);
    }
}
