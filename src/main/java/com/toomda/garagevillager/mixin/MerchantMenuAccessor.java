// com.toomda.garagevillager.mixin.MerchantMenuAccessor.java
package com.toomda.garagevillager.mixin;

import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MerchantMenu.class)
public interface MerchantMenuAccessor {
    @Accessor("trader")
    Merchant garagevillager$getTrader();

    @Accessor("tradeContainer")
    MerchantContainer garagevillager$getTradeContainer();
}
