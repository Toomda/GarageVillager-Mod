package com.toomda.garagevillager.menu;

import com.toomda.garagevillager.GarageMerchantContainerMarker;
import com.toomda.garagevillager.entity.GarageVillagerEntity;
import com.toomda.garagevillager.mixin.MerchantMenuAccessor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;

public class GarageMerchantMenu extends MerchantMenu {
    private final GarageVillagerEntity garageVillager;

    public GarageMerchantMenu(int containerId, Inventory playerInventory, GarageVillagerEntity trader) {
        super(containerId, playerInventory, trader);
        this.garageVillager = trader;

        if (trader instanceof GarageVillagerEntity) {
            MerchantContainer container =
                    ((MerchantMenuAccessor) this).garagevillager$getTradeContainer();

            ((GarageMerchantContainerMarker) container)
                    .garagevillager$setIsGarage(true);
        }
    }

    public GarageVillagerEntity getVillager() {
        return garageVillager;
    }

    @Override
    public boolean stillValid(Player player) {
        return !this.garageVillager.isRemoved()
                && player.distanceToSqr(this.garageVillager) <= 64.0D;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.garageVillager.setTradingPlayer(null);
    }

}
