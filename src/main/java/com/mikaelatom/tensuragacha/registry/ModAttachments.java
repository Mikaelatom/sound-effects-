package com.mikaelatom.tensuragacha.registry;

import com.mikaelatom.tensuragacha.TensuraGacha;
import com.mojang.serialization.Codec;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, TensuraGacha.MODID);

    /** Rolls since the last jackpot, kept across deaths. */
    public static final Supplier<AttachmentType<Integer>> PITY = ATTACHMENT_TYPES.register("pity",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());

    /** Soul Coins for the Soul Market, kept across deaths. */
    public static final Supplier<AttachmentType<Long>> SOUL_COINS = ATTACHMENT_TYPES.register("soul_coins",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).copyOnDeath().build());

    /** Race rolls since the last Hard or Extreme race, kept across deaths. */
    public static final Supplier<AttachmentType<Integer>> RACE_PITY = ATTACHMENT_TYPES.register("race_pity",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());
}
