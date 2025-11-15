package com.toomda.garagevillager;

import com.toomda.garagevillager.register.ModEntities;
import com.toomda.garagevillager.register.ModMenus;
import com.toomda.garagevillager.screen.GarageVillagerOwnerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = GarageVillager.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = GarageVillager.MODID, value = Dist.CLIENT)
public class GarageVillagerClient {
    public GarageVillagerClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        GarageVillager.LOGGER.info("HELLO FROM CLIENT SETUP");
        GarageVillager.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GARAGE_VILLAGER.get(), VillagerRenderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.GARAGE_VILLAGER_OWNER_MENU.get(), GarageVillagerOwnerScreen::new);
    }
}
