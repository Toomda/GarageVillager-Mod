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

        // Result-Slot abfragen – nur zeichnen, wenn ein Trade aktiv ist
        Slot resultSlot = menu.getSlot(2); // 0,1 = Bezahlung; 2 = Ergebnis
        if (resultSlot == null || resultSlot.getItem().isEmpty()) {
            // kein aktiver Trade -> keine Anzeige
            return;
        }

        MerchantOffer offer = offers.get(this.shopItem);

        // Dein “logischer Preis” steckt im XP-Feld:
        int price = offer.getXp();

        // theoretischer Wert der Bezahlung (aus den Kosten des Offers)
        int paid   = emeraldValue(offer.getCostA()) + emeraldValue(offer.getCostB());
        int change = Math.max(0, paid - price);

        if (change <= 0) {
            // wenn kein Wechselgeld anfällt, brauchen wir nichts anzeigen
            return;
        }

        // Position: unter dem Result-Slot
        int slotScreenX = this.leftPos + resultSlot.x;
        int slotScreenY = this.topPos + resultSlot.y;

        int textX = slotScreenX - 14;          // links bündig mit dem Slot
        int textY = slotScreenY + 28;     // ein Stück darunter

        // Farben (mit Alpha!)
        int COLOR_LABEL = 0xFFFFFFFF; // weiß
        int COLOR_VALUE = 0xFFFFD700; // gold

        String label = "Change: ";
        String amountStr = String.valueOf(change);

        // "Change:" in weiß, mit Shadow
        graphics.drawString(
                this.font,
                label,
                textX,
                textY,
                COLOR_LABEL,
                true
        );

        int labelWidth = this.font.width(label);

        // Zahl in Gold, mit Shadow
        int amountX = textX + labelWidth;
        graphics.drawString(
                this.font,
                amountStr,
                amountX,
                textY,
                COLOR_VALUE,
                true
        );

        int amountWidth = this.font.width(amountStr);

        // kleines Emerald-Icon rechts neben der Zahl
        int iconX = amountX + amountWidth + 1;
        int iconY = textY - 4;

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
