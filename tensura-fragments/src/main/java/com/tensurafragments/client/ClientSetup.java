package com.tensurafragments.client;

import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.shikigami.BeastKind;
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
        event.registerEntityRenderer(ModRegistries.HELL_CIRCLE.get(), HellCircleRenderer::new);
        event.registerEntityRenderer(ModRegistries.HELL_STORM.get(), HellStormRenderer::new);
        for (BeastKind kind : BeastKind.values()) {
            event.registerEntityRenderer(kind.type(), context -> new PaperBeastRenderer(context, kind));
        }
        event.registerEntityRenderer(ModRegistries.SPIRIT.get(), SpiritRenderer::new);
        event.registerEntityRenderer(ModRegistries.WISP.get(), WispRenderer::new);
        event.registerEntityRenderer(ModRegistries.FIRE_WHIRL.get(), FireWhirlRenderer::new);
        event.registerEntityRenderer(ModRegistries.CARD_TORNADO.get(), CardTornadoRenderer::new);
        event.registerEntityRenderer(ModRegistries.RELEASE_AURA.get(), ReleaseAuraRenderer::new);
    }

    @SubscribeEvent
    public static void registerKeys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(ClientCombat.TOGGLE);
        event.register(ClientCombat.BLOCK);
        event.register(ClientCombat.GRAB);
        event.register(ClientCombat.DASH);
        event.register(ClientCombat.STYLE);
    }

    @SubscribeEvent
    public static void registerParticles(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(com.tensurafragments.ModRegistries.SWISH.get(), SwishParticle.Provider::new);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(TensuraFragments.id("combat_hud"), ClientCombat::render);
        event.registerAboveAll(TensuraFragments.id("card_hud"), CardHud::render);
        event.registerAboveAll(TensuraFragments.id("shikigami_hud"), ShikigamiHud::render);
        event.registerAboveAll(TensuraFragments.id("grimoire_hud"), GrimoireHud::render);
        event.registerAboveAll(TensuraFragments.id("rainbow_hud"), RainbowHud::render);
        event.registerAboveAll(TensuraFragments.id("flame_hud"), FlameHud::render);
        event.registerAboveAll(TensuraFragments.id("possession_hud"), ClientPossession::renderHud);
        event.registerAboveAll(TensuraFragments.id("spirit_hud"), SpiritHud::render);
        event.registerAboveAll(TensuraFragments.id("energy_hud"), EnergyHud::render);
        event.registerAboveAll(TensuraFragments.id("soul_hud"), SoulHud::render);
        event.registerAboveAll(TensuraFragments.id("spirit_passage_hud"), SpiritPassageHud::render);
    }
}
