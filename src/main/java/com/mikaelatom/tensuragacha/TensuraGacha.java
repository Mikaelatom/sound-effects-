package com.mikaelatom.tensuragacha;

import com.mikaelatom.tensuragacha.registry.ModAttachments;
import com.mikaelatom.tensuragacha.registry.ModBlocks;
import com.mikaelatom.tensuragacha.shop.ItemValues;
import com.mikaelatom.tensuragacha.shop.ShopCommands;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import com.mikaelatom.tensuragacha.registry.ModItems;
import com.mikaelatom.tensuragacha.registry.ModRaces;
import com.mikaelatom.tensuragacha.registry.ModSkills;
import com.mikaelatom.tensuragacha.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(TensuraGacha.MODID)
public class TensuraGacha {
    public static final String MODID = "tensuragacha";

    public TensuraGacha(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModSkills.SKILLS.register(modEventBus);
        ModRaces.RACES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.CREATIVE_TABS.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.SERVER, GachaConfig.SPEC);

        NeoForge.EVENT_BUS.addListener(ShopCommands::register);
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> ItemValues.recompute(event.getServer()));
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> {
            // Fired after /reload (player == null) so prices follow datapack recipe changes.
            if (event.getPlayer() == null) ItemValues.recompute(event.getPlayerList().getServer());
        });
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
