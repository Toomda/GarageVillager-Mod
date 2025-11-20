package com.toomda.garagevillager.mixin;

import com.toomda.garagevillager.GarageMerchantContainerMarker;
import com.toomda.garagevillager.register.ModBlocks;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.inventory.MerchantContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(MerchantContainer.class)
public abstract class MerchantContainerMixin implements Container, GarageMerchantContainerMarker {

    private boolean garagevillager$isGarage;

    @Final
    @Shadow private Merchant merchant;
    @Final
    @Shadow private NonNullList<ItemStack> itemStacks;

    @Shadow @Nullable private MerchantOffer activeOffer;
    @Shadow private int selectionHint;
    @Shadow private int futureXp;

    @Shadow public abstract ItemStack getItem(int index);
    @Shadow public abstract void setItem(int index, ItemStack stack);

    @Override
    public void garagevillager$setIsGarage(boolean value) {
        this.garagevillager$isGarage = value;
    }

    @Override
    public boolean garagevillager$isGarage() {
        return this.garagevillager$isGarage;
    }

    @Inject(method = "updateSellItem", at = @At("HEAD"), cancellable = true)
    private void garagevillager$customUpdateSellItem(CallbackInfo ci) {
        // Only for our GarageVillager menus
        if (!this.garagevillager$isGarage) {
            return;
        }

        ItemStack pay0;
        ItemStack pay1;

        // vanilla pattern: slot 0 / 1 are payment slots
        if (this.getItem(0).isEmpty()) {
            pay0 = this.getItem(1);
            pay1 = ItemStack.EMPTY;
        } else {
            pay0 = this.getItem(0);
            pay1 = this.getItem(1);
        }

        // if both payment slots empty -> clear result
        if (pay0.isEmpty() && pay1.isEmpty()) {
            this.activeOffer = null;
            this.setItem(2, ItemStack.EMPTY);
            this.futureXp = 0;
            this.merchant.notifyTradeUpdated(this.getItem(2));
            ci.cancel();
            return;
        }

        MerchantOffers offers = this.merchant.getOffers();
        if (offers.isEmpty()) {
            this.activeOffer = null;
            this.setItem(2, ItemStack.EMPTY);
            this.futureXp = 0;
            this.merchant.notifyTradeUpdated(this.getItem(2));
            ci.cancel();
            return;
        }

        if (this.selectionHint < 0 || this.selectionHint >= offers.size()) {
            this.selectionHint = 0;
        }

        MerchantOffer offer = offers.get(this.selectionHint);
        if (offer == null || offer.isOutOfStock()) {
            this.activeOffer = offer;
            this.setItem(2, ItemStack.EMPTY);
            this.futureXp = 0;
            this.merchant.notifyTradeUpdated(this.getItem(2));
            ci.cancel();
            return;
        }

        // logical price from your entity (stored in xp field)
        int price = offer.getXp();
        if (price <= 0) {
            // fallback to vanilla if something is weird
            return;
        }

        Item coreItem = ModBlocks.EMERALD_CORE_BLOCK.get().asItem();

        int paidValue =
                emeraldValue(pay0, coreItem) +
                        emeraldValue(pay1, coreItem);

        if (paidValue >= price) {
            // enough paid => show result
            this.activeOffer = offer;
            this.setItem(2, offer.assemble());
            this.futureXp = offer.getXp();
        } else {
            // not enough => disable result slot
            this.activeOffer = null;
            this.setItem(2, ItemStack.EMPTY);
            this.futureXp = 0;
        }

        this.merchant.notifyTradeUpdated(this.getItem(2));
        ci.cancel();
    }

    private int emeraldValue(ItemStack stack, Item coreItem) {
        if (stack.isEmpty()) return 0;
        if (stack.is(Items.EMERALD)) return stack.getCount();
        if (stack.is(Items.EMERALD_BLOCK)) return stack.getCount() * 9;
        if (stack.is(coreItem)) return stack.getCount() * 81;
        return 0;
    }
}
