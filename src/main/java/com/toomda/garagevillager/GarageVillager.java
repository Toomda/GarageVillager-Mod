package com.toomda.garagevillager;

import com.toomda.garagevillager.register.ModEntities;
import com.toomda.garagevillager.register.ModEntityAttributes;
import com.toomda.garagevillager.register.ModItems;
import com.toomda.garagevillager.register.ModMenus;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(GarageVillager.MODID)
public class GarageVillager {
    public static final String MODID = "garagevillager";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GarageVillager(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addToCreativeTabs);
        NeoForge.EVENT_BUS.register(this);
        modEventBus.addListener(ModEntityAttributes::registerAttributes);

        ModEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModMenus.register(modEventBus);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");
    }

    public void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if(event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.GARAGE_VILLAGER_SPAWN_EGG.get());
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }
}
