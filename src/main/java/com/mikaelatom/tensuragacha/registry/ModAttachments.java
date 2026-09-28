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
}
