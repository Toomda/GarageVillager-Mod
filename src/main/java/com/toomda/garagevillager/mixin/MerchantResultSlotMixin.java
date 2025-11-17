package com.toomda.garagevillager.mixin;

import com.toomda.garagevillager.entity.GarageVillagerEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.inventory.Slot;
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

    @Final
    @Shadow private Player player;
    @Final
    @Shadow private Merchant merchant;
    @Final
    @Shadow private MerchantContainer slots;

    // Dummy ctor for Mixin, never actually used at runtime
    public MerchantResultSlotMixin(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    @Inject(
            method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD")
    )
    private void garagevillager$giveChange(Player player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        Level level = serverPlayer.level();
        if (level.isClientSide()) {
            return;
        }

        // nur unser GarageVillager soll Speziallogik bekommen
        if (!(this.merchant instanceof GarageVillagerEntity garageVillager)) {
            return;
        }

        // Aktueller Trade aus dem MerchantContainer
        MerchantOffer offer = this.slots.getActiveOffer();
        if (offer == null) {
            return;
        }

        // 1) "Eigentlicher Preis" aus deiner Entity holen
        int price = garageVillager.getPriceForOffer(offer);
        if (price <= 0) {
            return;
        }

        // 2) "Bezahlten Wert" aus den Offer-Kosten berechnen
        int paidValue = 0;

        ItemStack costA = offer.getCostA();
        if (!costA.isEmpty()) {
            if (costA.is(Items.EMERALD)) {
                paidValue += costA.getCount();
            } else if (costA.is(Items.EMERALD_BLOCK)) {
                paidValue += costA.getCount() * 9;
            }
        }

        ItemStack costB = offer.getCostB();
        if (!costB.isEmpty()) {
            if (costB.is(Items.EMERALD)) {
                paidValue += costB.getCount();
            } else if (costB.is(Items.EMERALD_BLOCK)) {
                paidValue += costB.getCount() * 9;
            }
        }

        int change = paidValue - price;
        if (change <= 0) {
            return; // kein Wechselgeld nötig
        }

        // 3) Wechselgeld als Emeralds auszahlen
        int remaining = change;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, 64);
            ItemStack refund = new ItemStack(Items.EMERALD, stackSize);

            boolean added = serverPlayer.getInventory().add(refund);
            if (!added) {
                serverPlayer.drop(refund, false);
            }

            remaining -= stackSize;
        }

        // 4) Debug-Nachricht in den Chat, damit du siehst, dass es läuft
        serverPlayer.sendSystemMessage(
                Component.literal(
                        "[GarageVillager] Price: " + price +
                                "E, paid: " + paidValue +
                                "E, change: " + change + "E"
                )
        );
    }
}
