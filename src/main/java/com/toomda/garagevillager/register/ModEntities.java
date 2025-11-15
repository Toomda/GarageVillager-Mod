package com.toomda.garagevillager.register;

import com.toomda.garagevillager.GarageVillager;
import com.toomda.garagevillager.entity.GarageVillagerEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;


public class ModEntities {

    public static final DeferredRegister.Entities ENTITIES =
            DeferredRegister.createEntities(GarageVillager.MODID);

    public static final Supplier<EntityType<GarageVillagerEntity>> GARAGE_VILLAGER =
            ENTITIES.registerEntityType(
                    "garage_villager",
                    GarageVillagerEntity::new,
                    MobCategory.MISC,
                    builder -> builder.sized(0.6F, 1.95F)
            );

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }
}
