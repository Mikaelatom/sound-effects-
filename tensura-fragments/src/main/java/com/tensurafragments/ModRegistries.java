package com.tensurafragments;

import com.mojang.serialization.Codec;
import com.tensurafragments.card.CardEntity;
import com.tensurafragments.grimoire.Binding;
import com.tensurafragments.grimoire.GrimoireContents;
import com.tensurafragments.grimoire.SealingGrimoireItem;
import com.tensurafragments.shikigami.BarrierAnchorEntity;
import com.tensurafragments.shikigami.ShikigamiEntity;
import com.tensurafragments.shikigami.TalismanEntity;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModRegistries {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, TensuraFragments.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, TensuraFragments.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TensuraFragments.MODID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, TensuraFragments.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, TensuraFragments.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CardEntity>> CARD = ENTITY_TYPES.register("card",
            () -> EntityType.Builder.<CardEntity>of(CardEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("card"));

    public static final DeferredHolder<EntityType<?>, EntityType<ShikigamiEntity>> SHIKIGAMI = ENTITY_TYPES.register("shikigami",
            () -> EntityType.Builder.<ShikigamiEntity>of(ShikigamiEntity::new, MobCategory.MISC)
                    .sized(0.9F, 0.9F)
                    .clientTrackingRange(10)
                    .build("shikigami"));

    public static final DeferredHolder<EntityType<?>, EntityType<TalismanEntity>> TALISMAN = ENTITY_TYPES.register("talisman",
            () -> EntityType.Builder.<TalismanEntity>of(TalismanEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("talisman"));

    public static final DeferredHolder<EntityType<?>, EntityType<BarrierAnchorEntity>> BARRIER_ANCHOR = ENTITY_TYPES.register("barrier_anchor",
            () -> EntityType.Builder.<BarrierAnchorEntity>of(BarrierAnchorEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.6F)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build("barrier_anchor"));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GrimoireContents>> GRIMOIRE_CONTENTS =
            COMPONENTS.registerComponentType("grimoire_contents",
                    builder -> builder.persistent(GrimoireContents.CODEC).networkSynchronized(GrimoireContents.STREAM_CODEC));

    public static final DeferredItem<SealingGrimoireItem> SEALING_GRIMOIRE = ITEMS.registerItem("sealing_grimoire",
            SealingGrimoireItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredHolder<SoundEvent, SoundEvent> CARD_PLACE = SOUNDS.register("card_place",
            () -> SoundEvent.createVariableRangeEvent(TensuraFragments.id("card_place")));

    /** Cards left in the deck. -1 means "not initialised yet", which is treated as a full deck. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> DECK = ATTACHMENTS.register("deck",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).build());

    /** Set on creatures released from a grimoire. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Binding>> BINDING = ATTACHMENTS.register("binding",
            () -> AttachmentType.<Binding>builder(() -> null).serialize(Binding.CODEC).build());

    /** Whether Shikigami Control's automatic Substitution is on. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> SUBSTITUTION_ENABLED = ATTACHMENTS.register(
            "substitution_enabled", () -> AttachmentType.builder(() -> true).serialize(Codec.BOOL).copyOnDeath().build());

    /** Ticks since the deck last regained a card. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> DECK_REGEN = ATTACHMENTS.register("deck_regen",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).build());

    private ModRegistries() {
    }

    static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        COMPONENTS.register(modEventBus);
        modEventBus.addListener((EntityAttributeCreationEvent event) ->
                event.put(SHIKIGAMI.get(), ShikigamiEntity.createAttributes().build()));
        SOUNDS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
    }
}
