package com.tensurafragments;

import com.tensurafragments.network.ModNetwork;
import com.tensurafragments.skill.ModSkills;
import com.tensurafragments.spiritrace.SpiritRaces;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(TensuraFragments.MODID)
public class TensuraFragments {
    public static final String MODID = "tensurafragments";

    public TensuraFragments(IEventBus modEventBus, ModContainer modContainer) {
        ModRegistries.register(modEventBus);
        ModSkills.init();
        SpiritRaces.init();
        modEventBus.addListener(ModNetwork::registerPayloads);
        // Combat Mode's player animations, swish trails and slam shockwaves (the combat animation kit).
        com.tensurafragments.combatanim.CombatAnim.init(modEventBus, MODID);
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
