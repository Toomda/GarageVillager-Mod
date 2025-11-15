package com.toomda.garagevillager.register;

import com.toomda.garagevillager.entity.GarageVillagerEntity;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

public class ModEntityAttributes {
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.GARAGE_VILLAGER.get(),
                GarageVillagerEntity.createAttributes().build());
    }
}
