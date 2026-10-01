package com.tensurafragments.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The soul of something you killed, kept by Soul Reaper: what it was (entity type id), what it was called, and how
 * much EP it had.
 */
public record CapturedSoul(String type, String name, double ep) {
    public static final Codec<CapturedSoul> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(CapturedSoul::type),
            Codec.STRING.fieldOf("name").forGetter(CapturedSoul::name),
            Codec.DOUBLE.fieldOf("ep").forGetter(CapturedSoul::ep)
    ).apply(i, CapturedSoul::new));
    public static final StreamCodec<ByteBuf, CapturedSoul> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CapturedSoul::type,
            ByteBufCodecs.STRING_UTF8, CapturedSoul::name,
            ByteBufCodecs.DOUBLE, CapturedSoul::ep,
            CapturedSoul::new);

    public static final String PLAYER = "minecraft:player";

    public boolean isPlayer() {
        return PLAYER.equals(type);
    }
}
