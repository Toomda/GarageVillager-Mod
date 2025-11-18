package com.toomda.garagevillager.register;

import com.toomda.garagevillager.GarageVillager;
import com.toomda.garagevillager.item.GarageVillagerSpawnEggItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(GarageVillager.MODID);

    public static final DeferredItem<SpawnEggItem> GARAGE_VILLAGER_SPAWN_EGG =
            ITEMS.registerItem("garage_villager_spawn_egg",
                    properties -> new GarageVillagerSpawnEggItem(
                            ModEntities.GARAGE_VILLAGER.get(),
                            properties
                    )
            );

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
