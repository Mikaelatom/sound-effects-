package com.tensurafragments;

import com.mojang.serialization.Codec;
import com.tensurafragments.card.CardEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModRegistries {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, TensuraFragments.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, TensuraFragments.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, TensuraFragments.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CardEntity>> CARD = ENTITY_TYPES.register("card",
            () -> EntityType.Builder.<CardEntity>of(CardEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("card"));

    public static final DeferredHolder<SoundEvent, SoundEvent> CARD_PLACE = SOUNDS.register("card_place",
            () -> SoundEvent.createVariableRangeEvent(TensuraFragments.id("card_place")));

    /** Cards left in the deck. -1 means "not initialised yet", which is treated as a full deck. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> DECK = ATTACHMENTS.register("deck",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).build());

    /** Ticks since the deck last regained a card. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> DECK_REGEN = ATTACHMENTS.register("deck_regen",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).build());

    private ModRegistries() {
    }

    static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        SOUNDS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
    }
}
