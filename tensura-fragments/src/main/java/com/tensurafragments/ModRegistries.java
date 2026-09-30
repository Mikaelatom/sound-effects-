package com.tensurafragments;

import com.mojang.serialization.Codec;
import com.tensurafragments.card.CardEntity;
import com.tensurafragments.flame.DraconicHellfireEffect;
import com.tensurafragments.flame.HellCircleEntity;
import com.tensurafragments.flame.HellStormEntity;
import com.tensurafragments.grimoire.Binding;
import com.tensurafragments.grimoire.GrimoireContents;
import com.tensurafragments.grimoire.SealingGrimoireItem;
import com.tensurafragments.shikigami.BarrierAnchorEntity;
import com.tensurafragments.shikigami.BeastKind;
import com.tensurafragments.shikigami.PaperBeastEntity;
import com.tensurafragments.shikigami.ShikigamiEntity;
import com.tensurafragments.shikigami.TalismanEntity;
import com.tensurafragments.spirit.SpiritEntity;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
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
    private static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, TensuraFragments.MODID);
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

    public static final DeferredHolder<EntityType<?>, EntityType<HellCircleEntity>> HELL_CIRCLE = ENTITY_TYPES.register("hell_circle",
            () -> EntityType.Builder.<HellCircleEntity>of(HellCircleEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(1).noSave().build("hell_circle"));

    public static final DeferredHolder<EntityType<?>, EntityType<HellStormEntity>> HELL_STORM = ENTITY_TYPES.register("hell_storm",
            () -> EntityType.Builder.<HellStormEntity>of(HellStormEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(1).noSave().build("hell_storm"));

    public static final DeferredHolder<EntityType<?>, EntityType<PaperBeastEntity>> PAPER_OWL = paperBeast("paper_owl", 0.6F, 0.6F);
    public static final DeferredHolder<EntityType<?>, EntityType<PaperBeastEntity>> PAPER_HOUND = paperBeast("paper_hound", 0.8F, 1.0F);
    public static final DeferredHolder<EntityType<?>, EntityType<PaperBeastEntity>> PAPER_CAT = paperBeast("paper_cat", 0.6F, 0.8F);
    public static final DeferredHolder<EntityType<?>, EntityType<PaperBeastEntity>> PAPER_RABBIT = paperBeast("paper_rabbit", 0.5F, 0.6F);

    public static final DeferredHolder<EntityType<?>, EntityType<SpiritEntity>> SPIRIT = ENTITY_TYPES.register("spirit",
            () -> EntityType.Builder.<SpiritEntity>of(SpiritEntity::new, MobCategory.MISC)
                    .sized(0.8F, 1.8F).clientTrackingRange(10).updateInterval(1).noSave().fireImmune().build("spirit"));

    /** Draconic Hellfire: Hell Storm's burn that never goes out. */
    public static final DeferredHolder<MobEffect, DraconicHellfireEffect> DRACONIC_HELLFIRE =
            MOB_EFFECTS.register("draconic_hellfire", DraconicHellfireEffect::new);

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

    /** Which spell talisman Shikigami Control throws (index into Spell). */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> SELECTED_SPELL = ATTACHMENTS.register(
            "selected_spell", () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());

    /** Which spell the Rainbow Talismans skill throws (index into Spell). */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> RAINBOW_SPELL = ATTACHMENTS.register(
            "rainbow_spell", () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());

    /** Which fire spell Flame Emperor casts (index into FireSpell). */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> FIRE_SPELL = ATTACHMENTS.register(
            "fire_spell", () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());

    /** Which paper beast Shikigami Control folds (index into BeastKind). */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> SELECTED_BEAST = ATTACHMENTS.register(
            "selected_beast", () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());

    /** Which spirit Spirit Control calls next (index into SpiritKind). */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> SPIRIT_INDEX = ATTACHMENTS.register(
            "spirit_index", () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());

    /** Which spirit Rainbow Magic calls next (index into SpiritKind); its own turn order, apart from Spirit Control's. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> RAINBOW_SPIRIT_INDEX = ATTACHMENTS.register(
            "rainbow_spirit_index", () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());

    /** Whether your attacks call spirits (Spirit Link). */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> SPIRIT_LINK = ATTACHMENTS.register(
            "spirit_link", () -> AttachmentType.builder(() -> true).serialize(Codec.BOOL).copyOnDeath().build());

    /** Ticks since the deck last regained a card. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> DECK_REGEN = ATTACHMENTS.register("deck_regen",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).build());

    private ModRegistries() {
    }

    private static DeferredHolder<EntityType<?>, EntityType<PaperBeastEntity>> paperBeast(String name, float width, float height) {
        return ENTITY_TYPES.register(name, () -> EntityType.Builder.<PaperBeastEntity>of(PaperBeastEntity::new, MobCategory.MISC)
                .sized(width, height).clientTrackingRange(10).updateInterval(1).build(name));
    }

    static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        COMPONENTS.register(modEventBus);
        modEventBus.addListener((EntityAttributeCreationEvent event) -> {
            event.put(SHIKIGAMI.get(), ShikigamiEntity.createAttributes().build());
            for (BeastKind kind : BeastKind.values()) {
                event.put(kind.type(), PaperBeastEntity.createAttributes(kind).build());
            }
        });
        SOUNDS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
    }
}
