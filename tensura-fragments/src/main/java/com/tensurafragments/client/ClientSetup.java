package com.tensurafragments.client;

import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@EventBusSubscriber(modid = TensuraFragments.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistries.CARD.get(), CardRenderer::new);
        event.registerEntityRenderer(ModRegistries.SHIKIGAMI.get(), ShikigamiRenderer::new);
        event.registerEntityRenderer(ModRegistries.TALISMAN.get(), TalismanRenderer::new);
        event.registerEntityRenderer(ModRegistries.BARRIER_ANCHOR.get(), BarrierAnchorRenderer::new);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(TensuraFragments.id("card_hud"), CardHud::render);
        event.registerAboveAll(TensuraFragments.id("shikigami_hud"), ShikigamiHud::render);
        event.registerAboveAll(TensuraFragments.id("grimoire_hud"), GrimoireHud::render);
    }
}
