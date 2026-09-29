package com.tensurafragments.grimoire;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tensurafragments.ModRegistries;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** Marks a creature released from a grimoire: who it serves and when it goes back into the book. */
public record Binding(UUID binder, long until) {
    public static final Codec<Binding> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("binder").forGetter(Binding::binder),
            Codec.LONG.fieldOf("until").forGetter(Binding::until)
    ).apply(i, Binding::new));

    @Nullable
    public static Binding get(Entity entity) {
        return entity.hasData(ModRegistries.BINDING) ? entity.getData(ModRegistries.BINDING) : null;
    }

    public static boolean isBoundTo(Entity entity, Entity binder) {
        Binding binding = get(entity);
        return binding != null && binding.binder().equals(binder.getUUID());
    }

    public static void bind(Entity entity, Entity binder, long until) {
        entity.setData(ModRegistries.BINDING, new Binding(binder.getUUID(), until));
    }

    static void unbind(Entity entity) {
        entity.removeData(ModRegistries.BINDING);
    }
}
