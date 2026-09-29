package com.tensurafragments.grimoire;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Everything sealed in a grimoire, stored on the item. Immutable: change it with the {@code with...} methods. */
public record GrimoireContents(List<Page> pages, int selected) {
    public static final GrimoireContents EMPTY = new GrimoireContents(List.of(), 0);

    public enum Kind {
        CREATURE, MAGIC
    }

    /**
     * One sealed thing. {@code data} is the entity saved with its id (see {@code Entity.saveAsPassenger}); for magic
     * {@code speed} is how fast it was flying when caught.
     */
    public record Page(Kind kind, String name, CompoundTag data, float speed) {
        public static final Codec<Page> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.xmap(Kind::valueOf, Kind::name).fieldOf("kind").forGetter(Page::kind),
                Codec.STRING.fieldOf("name").forGetter(Page::name),
                CompoundTag.CODEC.fieldOf("data").forGetter(Page::data),
                Codec.FLOAT.optionalFieldOf("speed", 1.0F).forGetter(Page::speed)
        ).apply(i, Page::new));

        public static final StreamCodec<ByteBuf, Page> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT.map(n -> Kind.values()[n], Kind::ordinal), Page::kind,
                ByteBufCodecs.STRING_UTF8, Page::name,
                ByteBufCodecs.COMPOUND_TAG, Page::data,
                ByteBufCodecs.FLOAT, Page::speed,
                Page::new);
    }

    public static final Codec<GrimoireContents> CODEC = RecordCodecBuilder.create(i -> i.group(
            Page.CODEC.listOf().fieldOf("pages").forGetter(GrimoireContents::pages),
            Codec.INT.optionalFieldOf("selected", 0).forGetter(GrimoireContents::selected)
    ).apply(i, GrimoireContents::new));

    public static final StreamCodec<ByteBuf, GrimoireContents> STREAM_CODEC = StreamCodec.composite(
            Page.STREAM_CODEC.apply(ByteBufCodecs.list()), GrimoireContents::pages,
            ByteBufCodecs.VAR_INT, GrimoireContents::selected,
            GrimoireContents::new);

    public GrimoireContents {
        pages = List.copyOf(pages);
        selected = pages.isEmpty() ? 0 : Math.floorMod(selected, pages.size());
    }

    public boolean isEmpty() {
        return pages.isEmpty();
    }

    public Page selectedPage() {
        return pages.isEmpty() ? null : pages.get(selected);
    }

    public GrimoireContents withAdded(Page page) {
        List<Page> list = new ArrayList<>(pages);
        list.add(page);
        return new GrimoireContents(list, list.size() - 1);
    }

    public GrimoireContents withRemoved(int index) {
        List<Page> list = new ArrayList<>(pages);
        list.remove(index);
        return new GrimoireContents(list, Math.min(selected, Math.max(0, list.size() - 1)));
    }

    public GrimoireContents withNextSelected() {
        return new GrimoireContents(pages, selected + 1);
    }
}
