// com.toomda.garagevillager.mixin.MerchantResultSlotMixin.java
package com.toomda.garagevillager.mixin;

import com.toomda.garagevillager.entity.GarageVillagerEntity;
import com.toomda.garagevillager.register.ModBlocks;
import net.minecraft.network.chat.Component;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

        // only our GarageVillager gets special logic
        if (!(this.merchant instanceof GarageVillagerEntity garageVillager)) {
            return;
        }

        MerchantOffer offer = this.slots.getActiveOffer();
        if (offer == null) {
            // no active offer -> let vanilla handle (but should not happen)
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

        // safety: if somehow not enough -> cancel trade
        if (paidValue < price) {
            // optionally clear result slot here
            ci.cancel();
            return;
        }

        int change = paidValue - price;

        // 1) give change as emeralds
        if (change > 0) {
            giveEmeraldChange(serverPlayer, change);
        }

        // 2) remove only currency items from the payment slots
        clearCurrencySlot(0, coreItem);
        clearCurrencySlot(1, coreItem);

        // 3) run villager trade logic (adds emeraldBalance, clears sold item, rebuilds offers, syncs GUI)
        garageVillager.notifyTrade(offer);

        // fully handle trade on our own, skip vanilla onTake
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
        // non-currency items (e.g. player accidentally put a diamond)
        // remain in the slot
    }

    private void giveEmeraldChange(ServerPlayer player, int change) {
        Item coreItem = ModBlocks.EMERALD_CORE_BLOCK.get().asItem();

        int remaining = change;

        // 1) Core-Blöcke (81 Emeralds)
        int cores = remaining / 81;
        remaining -= cores * 81;

        // 2) Emerald-Blöcke (9 Emeralds)
        int blocks = remaining / 9;
        remaining -= blocks * 9;

        // 3) Restliche Emeralds (1 Emerald)
        int emeralds = remaining;

        // Jetzt alles als Itemstacks geben
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
        int maxStack = item.getDefaultMaxStackSize(); // i.d.R. 64

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
