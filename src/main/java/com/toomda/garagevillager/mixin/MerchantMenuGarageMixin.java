package com.toomda.garagevillager.mixin;

import com.toomda.garagevillager.GarageMerchantContainerMarker;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantMenu.class)
public abstract class MerchantMenuGarageMixin {

    @Final
    @Shadow private MerchantContainer tradeContainer;

    @Inject(method = "setMerchantLevel", at = @At("TAIL"))
    private void garagevillager$markGarageLevel(int level, CallbackInfo ci) {
        final int GARAGE_MAGIC_LEVEL = 1337;

        if (level == GARAGE_MAGIC_LEVEL && this.tradeContainer instanceof GarageMerchantContainerMarker marker) {
            marker.garagevillager$setIsGarage(true);
        }
    }
}
