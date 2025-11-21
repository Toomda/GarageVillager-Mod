package com.toomda.garagevillager.mixin;

import com.toomda.garagevillager.entity.GarageVillagerEntity;
import com.toomda.garagevillager.register.ModBlocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantResultSlot.class)
public abstract class MerchantResultSlotMixin extends Slot {

    @Final @Shadow private Player player;
    @Final @Shadow private Merchant merchant;
    @Final @Shadow private MerchantContainer slots;

    public MerchantResultSlotMixin(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    @Inject(
            method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void garagevillager$handleGarageTrade(Player player, ItemStack resultStack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        Level level = serverPlayer.level();
        if (level.isClientSide()) {
            return;
        }

        if (!(this.merchant instanceof GarageVillagerEntity garageVillager)) {
            return;
        }

        MerchantOffer offer = this.slots.getActiveOffer();
        if (offer == null) {
            return;
        }

        int price = garageVillager.getPriceForOffer(offer);
        if (price <= 0) {
            return;
        }

        Item coreItem = ModBlocks.EMERALD_CORE_BLOCK.get().asItem();

        ItemStack pay0 = this.slots.getItem(0);
        ItemStack pay1 = this.slots.getItem(1);

        int paidValue =
                emeraldValue(pay0, coreItem) +
                        emeraldValue(pay1, coreItem);

        if (paidValue < price) {
            ci.cancel();
            return;
        }

        int change = paidValue - price;

        if (change > 0) {
            giveEmeraldChange(serverPlayer, change);
        }

        clearCurrencySlot(0, coreItem);
        clearCurrencySlot(1, coreItem);
        garageVillager.notifyTrade(offer);

        ci.cancel();
    }

    private int emeraldValue(ItemStack stack, Item coreItem) {
        if (stack.isEmpty()) return 0;
        if (stack.is(Items.EMERALD)) return stack.getCount();
        if (stack.is(Items.EMERALD_BLOCK)) return stack.getCount() * 9;
        if (stack.is(coreItem)) return stack.getCount() * 81;
        return 0;
    }

    private boolean isCurrency(ItemStack stack, Item coreItem) {
        if (stack.isEmpty()) return false;
        return stack.is(Items.EMERALD)
                || stack.is(Items.EMERALD_BLOCK)
                || stack.is(coreItem);
    }

    private void clearCurrencySlot(int idx, Item coreItem) {
        ItemStack s = this.slots.getItem(idx);
        if (!s.isEmpty() && isCurrency(s, coreItem)) {
            this.slots.setItem(idx, ItemStack.EMPTY);
        }
    }

    private void giveEmeraldChange(ServerPlayer player, int change) {
        Item coreItem = ModBlocks.EMERALD_CORE_BLOCK.get().asItem();

        int remaining = change;

        int cores = remaining / 81;
        remaining -= cores * 81;

        int blocks = remaining / 9;
        remaining -= blocks * 9;

        int emeralds = remaining;

        if (cores > 0) {
            giveItemStacks(player, coreItem, cores);
        }
        if (blocks > 0) {
            giveItemStacks(player, Items.EMERALD_BLOCK, blocks);
        }
        if (emeralds > 0) {
            giveItemStacks(player, Items.EMERALD, emeralds);
        }
    }

    private void giveItemStacks(ServerPlayer player, Item item, int count) {
        int remaining = count;
        int maxStack = item.getDefaultMaxStackSize();

        while (remaining > 0) {
            int stackSize = Math.min(remaining, maxStack);
            ItemStack refund = new ItemStack(item, stackSize);

            boolean added = player.getInventory().add(refund);
            if (!added) {
                player.drop(refund, false);
            }

            remaining -= stackSize;
        }
    }
}
