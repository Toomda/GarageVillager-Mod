package com.toomda.garagevillager.mixin;

import com.toomda.garagevillager.GarageMerchantContainerMarker;
import com.toomda.garagevillager.register.ModBlocks;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantMenu.class)
public abstract class MerchantMenuCurrencyMixin extends AbstractContainerMenu {

    @Shadow
    private MerchantContainer tradeContainer;

    @Shadow
    public abstract MerchantOffers getOffers();

    protected MerchantMenuCurrencyMixin(MenuType<?> type, int containerId) {
        super(type, containerId);
    }

    @Inject(method = "tryMoveItems", at = @At("HEAD"), cancellable = true)
    private void garagevillager$smartCurrencyAutofill(int selectedMerchantRecipe, CallbackInfo ci) {
        if (!(this.tradeContainer instanceof GarageMerchantContainerMarker marker) || !marker.garagevillager$isGarage()) {
            return;
        }

        MerchantOffers offers = this.getOffers();
        if (selectedMerchantRecipe < 0 || selectedMerchantRecipe >= offers.size()) {
            ci.cancel();
            return;
        }

        ItemStack pay0 = this.tradeContainer.getItem(0);
        if (!pay0.isEmpty()) {
            if (!this.moveItemStackTo(pay0, 3, 39, true)) {
                ci.cancel();
                return;
            }
            this.tradeContainer.setItem(0, pay0);
        }

        ItemStack pay1 = this.tradeContainer.getItem(1);
        if (!pay1.isEmpty()) {
            if (!this.moveItemStackTo(pay1, 3, 39, true)) {
                ci.cancel();
                return;
            }
            this.tradeContainer.setItem(1, pay1);
        }

        if (!this.tradeContainer.getItem(0).isEmpty() || !this.tradeContainer.getItem(1).isEmpty()) {
            ci.cancel();
            return;
        }

        MerchantOffer offer = offers.get(selectedMerchantRecipe);
        if (offer == null || offer.isOutOfStock()) {
            ci.cancel();
            return;
        }
        int price = offer.getXp();
        if (price <= 0) {
            ci.cancel();
            return;
        }

        Item coreItem = ModBlocks.EMERALD_CORE_BLOCK.get().asItem();

        int emeraldCount = 0;
        int blockCount = 0;
        int coreCount = 0;

        for (int i = 3; i < 39; ++i) {
            Slot slot = this.slots.get(i);
            ItemStack s = slot.getItem();
            if (s.is(Items.EMERALD)) {
                emeraldCount += s.getCount();
            } else if (s.is(Items.EMERALD_BLOCK)) {
                blockCount += s.getCount();
            } else if (s.is(coreItem)) {
                coreCount += s.getCount();
            }
        }

        int totalValue = emeraldCount + blockCount * 9 + coreCount * 81;
        if (totalValue < price) {
            ci.cancel();
            return;
        }

        int bestValue = Integer.MAX_VALUE;
        int bestTypeA = -1, bestCountA = 0;
        int bestTypeB = -1, bestCountB = 0;

        for (int typeA = 0; typeA < 3; ++typeA) {
            int unitA = unitValue(typeA);
            int maxA = Math.min(64, availableCount(typeA, emeraldCount, blockCount, coreCount));

            for (int countA = 0; countA <= maxA; ++countA) {
                for (int typeB = -1; typeB < 3; ++typeB) {
                    int unitB;
                    int maxB;

                    if (typeB == -1) {
                        unitB = 0;
                        maxB = 0;
                    } else {
                        unitB = unitValue(typeB);
                        int availableB = availableCount(typeB, emeraldCount, blockCount, coreCount);
                        if (typeB == typeA) {
                            availableB -= countA;
                        }
                        if (availableB <= 0) {
                            continue;
                        }
                        maxB = Math.min(64, availableB);
                    }

                    for (int countB = 0; countB <= maxB; ++countB) {
                        if (countA == 0 && countB == 0) continue;

                        int value = countA * unitA + countB * unitB;
                        if (value < price) continue;

                        if (value < bestValue) {
                            bestValue = value;
                            bestTypeA = (countA > 0) ? typeA : -1;
                            bestCountA = countA;
                            bestTypeB = (countB > 0) ? typeB : -1;
                            bestCountB = countB;
                        }
                    }
                }
            }
        }

        if (bestValue == Integer.MAX_VALUE) {
            ci.cancel();
            return;
        }

        if (bestTypeA != -1 && bestCountA > 0) {
            pullToPaymentSlot(bestTypeA, bestCountA, 0, coreItem);
        }

        if (bestTypeB != -1 && bestCountB > 0) {
            pullToPaymentSlot(bestTypeB, bestCountB, 1, coreItem);
        }

        ci.cancel();
    }

    private int unitValue(int type) {
        return switch (type) {
            case 0 -> 1;
            case 1 -> 9;
            case 2 -> 81;
            default -> 0;
        };
    }

    private int availableCount(int type, int emeraldCount, int blockCount, int coreCount) {
        return switch (type) {
            case 0 -> emeraldCount;
            case 1 -> blockCount;
            case 2 -> coreCount;
            default -> 0;
        };
    }

    private Item itemForType(int type, Item coreItem) {
        return switch (type) {
            case 0 -> Items.EMERALD;
            case 1 -> Items.EMERALD_BLOCK;
            case 2 -> coreItem;
            default -> Items.AIR;
        };
    }

    private void pullToPaymentSlot(int type, int needed, int paySlotIndex, Item coreItem) {
        if (needed <= 0) return;

        Item target = itemForType(type, coreItem);
        if (target == Items.AIR) return;

        ItemStack payStack = this.tradeContainer.getItem(paySlotIndex);

        for (int i = 3; i < 39 && needed > 0; ++i) {
            Slot invSlot = this.slots.get(i);
            ItemStack invStack = invSlot.getItem();
            if (!invStack.is(target)) continue;

            int move = Math.min(needed, invStack.getCount());
            if (move <= 0) continue;

            if (payStack.isEmpty()) {
                payStack = invStack.copyWithCount(move);
                invStack.shrink(move);
                invSlot.setChanged();
                this.tradeContainer.setItem(paySlotIndex, payStack);
            } else if (ItemStack.isSameItemSameComponents(payStack, invStack)) {
                int space = Math.min(move, payStack.getMaxStackSize() - payStack.getCount());
                if (space <= 0) continue;

                payStack.grow(space);
                invStack.shrink(space);
                invSlot.setChanged();
                this.tradeContainer.setItem(paySlotIndex, payStack);
                move = space;
            }

            needed -= move;
        }
    }
}
