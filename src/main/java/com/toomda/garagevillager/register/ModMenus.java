package com.toomda.garagevillager.register;

import com.toomda.garagevillager.GarageVillager;
import com.toomda.garagevillager.menu.GarageVillagerOwnerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, GarageVillager.MODID);

    public static final Supplier<MenuType<GarageVillagerOwnerMenu>> GARAGE_VILLAGER_OWNER_MENU =
            MENUS.register("garage_villager_owner",
                    () -> new MenuType<>(GarageVillagerOwnerMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
