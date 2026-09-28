package com.mikaelatom.tensuragacha.registry;

import com.mikaelatom.tensuragacha.TensuraGacha;
import com.mikaelatom.tensuragacha.item.AstralCoreItem;
import com.mikaelatom.tensuragacha.item.RaceGachaTicketItem;
import com.mikaelatom.tensuragacha.item.SoulGachaTicketItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TensuraGacha.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TensuraGacha.MODID);

    public static final DeferredItem<SoulGachaTicketItem> SOUL_GACHA_TICKET = ITEMS.register("soul_gacha_ticket",
            () -> new SoulGachaTicketItem(new Item.Properties().rarity(Rarity.RARE)));

    public static final DeferredItem<RaceGachaTicketItem> RACE_GACHA_TICKET = ITEMS.register("race_gacha_ticket",
            () -> new RaceGachaTicketItem(new Item.Properties().rarity(Rarity.EPIC)));

    public static final DeferredItem<AstralCoreItem> ASTRAL_CORE = ITEMS.register("astral_core",
            () -> new AstralCoreItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.tensuragacha"))
                    .icon(() -> SOUL_GACHA_TICKET.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(SOUL_GACHA_TICKET.get());
                        output.accept(RACE_GACHA_TICKET.get());
                        output.accept(ASTRAL_CORE.get());
                    })
                    .build());
}
