package com.toomda.garagevillager.mixin;

import com.toomda.garagevillager.register.ModBlocks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantScreen.class)
public abstract class MerchantScreenPriceMixin
        extends AbstractContainerScreen<MerchantMenu> {

    @Shadow private int shopItem;

    protected MerchantScreenPriceMixin(MerchantMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Inject(method = "renderContents", at = @At("TAIL"))
    private void garagevillager$renderPriceInfo(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci
    ) {
        final int GARAGE_MAGIC_LEVEL = 1337;
        MerchantMenu menu = this.menu;

        int traderLevel = menu.getTraderLevel();
        if (traderLevel != GARAGE_MAGIC_LEVEL) {
            return;
        }

        MerchantOffers offers = menu.getOffers();
        if (offers.isEmpty()) {
            return;
        }
        if (this.shopItem < 0 || this.shopItem >= offers.size()) {
            return;
        }

        Slot resultSlot = menu.getSlot(2);
        if (resultSlot == null || resultSlot.getItem().isEmpty()) {
            return;
        }

        MerchantOffer offer = offers.get(this.shopItem);
        int price = offer.getXp();

        Slot paySlot0 = menu.getSlot(0);
        Slot paySlot1 = menu.getSlot(1);

        ItemStack pay0 = paySlot0 != null ? paySlot0.getItem() : ItemStack.EMPTY;
        ItemStack pay1 = paySlot1 != null ? paySlot1.getItem() : ItemStack.EMPTY;

        int paid = emeraldValue(pay0) + emeraldValue(pay1);
        int change = Math.max(0, paid - price);
        if (change <= 0) {
            return;
        }

        int slotScreenY = this.topPos + resultSlot.y;

        int textY = slotScreenY + 28;

        int COLOR_LABEL = 0xFFFFFFFF;
        int COLOR_VALUE = 0xFFFFD700;

        String label = "Change: ";
        String amountStr = String.valueOf(change);

        int labelWidth = this.font.width(label);
        int amountWidth = this.font.width(amountStr);
        int iconWidth = 16;
        int spacing = 1;

        int totalWidth = labelWidth + amountWidth + spacing + iconWidth;

        int rightBound = this.leftPos + this.imageWidth - 8;
        int textX = rightBound - totalWidth;

        int amountX = textX + labelWidth;
        int iconX = amountX + amountWidth + spacing;
        int iconY = textY - 4;
        graphics.drawString(
                this.font,
                label,
                textX,
                textY,
                COLOR_LABEL,
                true
        );

        graphics.drawString(
                this.font,
                amountStr,
                amountX,
                textY,
                COLOR_VALUE,
                true
        );

        graphics.renderFakeItem(new ItemStack(Items.EMERALD), iconX, iconY);
    }

    private int emeraldValue(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (stack.is(Items.EMERALD)) return stack.getCount();
        if (stack.is(Items.EMERALD_BLOCK)) return stack.getCount() * 9;
        if (stack.is(ModBlocks.EMERALD_CORE_BLOCK.get().asItem())) return stack.getCount() * 81;
        return 0;
    }


}
