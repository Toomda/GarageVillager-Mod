package com.toomda.garagevillager.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fix für den "Blanko-Trade-Button", wenn alle Trades verschwinden,
 * während der MerchantScreen offen ist.
 */
@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin
        extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<MerchantMenu> {
    @Shadow
    private int shopItem;

    @Shadow
    int scrollOff;

    protected MerchantScreenMixin(MerchantMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }
    @Inject(
            method = "renderContents",
            at = @At("HEAD")
    )
    private void garagevillager$hideButtonsIfNoOffers(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci
    ) {
        MerchantOffers offers = this.menu.getOffers();
        int size = offers.size();

        if (size == 0) {
            this.shopItem = 0;
            this.scrollOff = 0;

            for (Renderable renderable : this.renderables) {
                if (renderable instanceof Button button) {
                    button.visible = false;
                }
            }
            return;
        }

        if (this.shopItem < 0) {
            this.shopItem = 0;
        } else if (this.shopItem >= size) {
            this.shopItem = size - 1;
        }

        int maxScroll = Math.max(0, size - 7);
        if (this.scrollOff < 0) {
            this.scrollOff = 0;
        } else if (this.scrollOff > maxScroll) {
            this.scrollOff = maxScroll;
        }
    }
}
