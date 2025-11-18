package com.toomda.garagevillager.entity;

import com.mojang.serialization.DataResult;
import com.toomda.garagevillager.menu.GarageMerchantMenu;
import com.toomda.garagevillager.menu.GarageVillagerOwnerMenu;
import com.toomda.garagevillager.register.ModBlocks;
import com.toomda.garagevillager.register.ModItems;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class GarageVillagerEntity extends Villager {
    private UUID ownerUuid;
    private String ownerName = "Garage";   // <--- neu
    private final SimpleContainer inventory = new SimpleContainer(17);
    private final int[] prices = new int[17];
    private int emeraldBalance = 0;
    private final MerchantOffers offers = new MerchantOffers();
    private final Map<MerchantOffer, Integer> offerToSlot = new IdentityHashMap<>();
    final int GARAGE_LEVEL = 1337;

    public GarageVillagerEntity(EntityType<? extends GarageVillagerEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setInvulnerable(true);
    }

    @Override
    public MerchantOffers getOffers() {
        return this.offers;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void rebuildOffersFromInventory() {
        this.offers.clear();
        this.offerToSlot.clear();

        final int MAX_PRICE = 128 * 81; // 10368
        final Item CORE_ITEM = ModBlocks.EMERALD_CORE_BLOCK.get().asItem();

        for (int i = 0; i < this.inventory.getContainerSize(); i++) {
            ItemStack stack = this.inventory.getItem(i);
            int price = this.prices[i];

            if (stack.isEmpty() || price <= 0) {
                continue;
            }

            if (price > MAX_PRICE) {
                price = MAX_PRICE;
                this.prices[i] = MAX_PRICE;
            }

            ItemStack result = stack.copy();
            ItemCost costA;
            ItemCost costB = null;

            if (price <= 64) {
                // 1) nur Emeralds
                costA = new ItemCost(Items.EMERALD, price);

            } else if (price <= (64 * 9 + 64)) {
                // 2) 65..640: Blöcke + evtl. Emeralds (wie bisher)
                int maxBlocks = Math.min(64, price / 9);
                int blocks = 0;
                int emeralds = price;

                for (int b = maxBlocks; b >= 1; b--) {
                    int rest = price - b * 9;
                    if (rest >= 0 && rest <= 64) {
                        blocks = b;
                        emeralds = rest;
                        break;
                    }
                }

                if (blocks == 0) {
                    blocks = 0;
                    emeralds = Math.min(price, 64);
                }

                costA = new ItemCost(Items.EMERALD_BLOCK, blocks);
                if (emeralds > 0) {
                    costB = new ItemCost(Items.EMERALD, emeralds);
                }

            } else if (price <= (128 * 9)) {
                // 3) 641..1152: nur Emerald Blöcke in zwei Slots
                int totalBlocks = (price + 8) / 9; // ceil(price / 9)

                int blocksA = Math.min(64, totalBlocks);
                int blocksB = totalBlocks - blocksA;
                if (blocksB < 0) blocksB = 0;

                costA = new ItemCost(Items.EMERALD_BLOCK, blocksA);
                if (blocksB > 0) {
                    costB = new ItemCost(Items.EMERALD_BLOCK, blocksB);
                }

            } else {
                // 4) >1152: Core-Block + Block Kombinationen (max. 2 Slots)
                //    Suche minimalen Wert >= price mit:
                //    Slot A: Block ODER Core
                //    Slot B: leer / Block / Core
                int bestValue = Integer.MAX_VALUE;
                ItemCost bestA = null;
                ItemCost bestB = null;

                // Slot A: EmeraldBlock (9) oder Core (81)
                for (int typeA = 0; typeA < 2; typeA++) {
                    Item itemA = (typeA == 0) ? Items.EMERALD_BLOCK : CORE_ITEM;
                    int unitA = (typeA == 0) ? 9 : 81;

                    for (int countA = 1; countA <= 64; countA++) {
                        int baseA = unitA * countA;

                        // Fall: nur Slot A belegt
                        if (baseA >= price && baseA < bestValue) {
                            bestValue = baseA;
                            bestA = new ItemCost(itemA, countA);
                            bestB = null;
                        }

                        // Slot B: nichts, Block oder Core
                        for (int typeB = 0; typeB < 2; typeB++) {
                            Item itemB = (typeB == 0) ? Items.EMERALD_BLOCK : CORE_ITEM;
                            int unitB = (typeB == 0) ? 9 : 81;

                            for (int countB = 1; countB <= 64; countB++) {
                                int total = baseA + unitB * countB;
                                if (total >= price && total < bestValue) {
                                    bestValue = total;
                                    bestA = new ItemCost(itemA, countA);
                                    bestB = new ItemCost(itemB, countB);
                                }
                            }
                        }
                    }
                }

                // Sicherheits-Fallback (sollte nie nötig sein)
                if (bestA == null) {
                    bestA = new ItemCost(CORE_ITEM, 64);
                    bestB = new ItemCost(CORE_ITEM, 64);
                }

                costA = bestA;
                costB = bestB;
            }

            int logicalPrice = price;

            MerchantOffer offer;
            if (costB != null) {
                offer = new MerchantOffer(
                        costA,
                        Optional.of(costB),
                        result,
                        1,
                        logicalPrice,
                        0.0F
                );
            } else {
                offer = new MerchantOffer(
                        costA,
                        result,
                        1,
                        logicalPrice,
                        0.0F
                );
            }

            this.offers.add(offer);
            this.offerToSlot.put(offer, i);
        }
    }






    private void syncOffersToOpenBuyers_skipActiveTrades() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        MerchantOffers currentOffers = this.getOffers();

        for (ServerPlayer serverPlayer : serverLevel.players()) {
            if (serverPlayer.containerMenu instanceof GarageMerchantMenu merchantMenu &&
                    merchantMenu.getVillager() == this) {

                serverPlayer.sendMerchantOffers(
                        merchantMenu.containerId,
                        currentOffers,
                        GARAGE_LEVEL,
                        0,
                        false,
                        false
                );
            }
        }
    }

    public void onTradesUpdatedFromOwner() {
        this.rebuildOffersFromInventory();
        this.syncOffersToOpenBuyers_skipActiveTrades();
    }

    public void onTradesUpdated() {
        this.rebuildOffersFromInventory();

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        for (ServerPlayer serverPlayer : serverLevel.players()) {
            if (serverPlayer.containerMenu instanceof GarageMerchantMenu merchantMenu &&
                    merchantMenu.getVillager() == this) {
                boolean hasActiveTrade = !merchantMenu.getSlot(2).getItem().isEmpty();
                if(!hasActiveTrade)
                {
                    serverPlayer.sendMerchantOffers(
                            merchantMenu.containerId,
                            this.getOffers(),
                            GARAGE_LEVEL,
                            0,
                            false,
                            false
                    );
                }
            }
        }
    }



    @Override
    public void notifyTrade(MerchantOffer offer) {
        super.notifyTrade(offer);

        // Only server should modify inventory / balance
        if (this.level().isClientSide()) {
            return;
        }

        Integer slotIndex = this.offerToSlot.get(offer);

        if (slotIndex != null) {
            int price = this.prices[slotIndex];

            if (price > 0) {
                // Add only the "logical" price to the villager balance
                this.addEmeraldBalance(price);
            }

            // Remove the sold item from the garage inventory
            this.inventory.setItem(slotIndex, ItemStack.EMPTY);
            this.prices[slotIndex] = 0;
        }

        // Rebuild & sync offers for other buyers
        this.rebuildOffersFromInventory();
        syncOffersToOpenBuyers_afterPurchase();
    }


    public int getPriceForOffer(MerchantOffer offer) {
        Integer idx = this.offerToSlot.get(offer);
        if (idx == null || idx < 0 || idx >= this.prices.length) {
            return 0;
        }
        return this.prices[idx];
    }


    private void giveEmeraldChange(ServerPlayer player, int change) {
        int remaining = change;

        while (remaining > 0) {
            int stackSize = Math.min(remaining, 64);
            ItemStack refund = new ItemStack(Items.EMERALD, stackSize);

            boolean added = player.getInventory().add(refund);
            if (!added) {
                player.drop(refund, false);
            }

            remaining -= stackSize;
        }
    }

    private void syncOffersToOpenBuyers_afterPurchase() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        MerchantOffers currentOffers = this.getOffers();

        for (ServerPlayer serverPlayer : serverLevel.players()) {
            if (serverPlayer.containerMenu instanceof GarageMerchantMenu merchantMenu &&
                    merchantMenu.getVillager() == this) {

                merchantMenu.getSlot(0).setByPlayer(ItemStack.EMPTY);
                merchantMenu.getSlot(1).setByPlayer(ItemStack.EMPTY);
                merchantMenu.getSlot(2).setByPlayer(ItemStack.EMPTY);

                merchantMenu.setSelectionHint(0);

                merchantMenu.broadcastChanges();

                serverPlayer.sendMerchantOffers(
                        merchantMenu.containerId,
                        currentOffers,
                        GARAGE_LEVEL,
                        0,
                        false,
                        false
                );
            }
        }
    }



    public int getEmeraldBalance() {
        return emeraldBalance;
    }

    public void setEmeraldBalance(int balance) {
        this.emeraldBalance = Math.max(0, balance);
    }

    public void addEmeraldBalance(int delta) {
        this.emeraldBalance = Math.max(0, this.emeraldBalance + delta);
    }

    public int getPrice(int slot) {
        if (slot < 0 || slot >= prices.length) {
            return 0;
        }
        return prices[slot];
    }

    public void setPrice(int slot, int price) {
        if (slot < 0 || slot >= prices.length) {
            return;
        }
        int MAX_PRICE = 128 * 81; // 10368
        int clamped = Math.max(0, Math.min(price, MAX_PRICE));
        prices[slot] = clamped;
    }



    @Override
    public boolean isInvulnerable() {
        return true;
    }

    @Override
    public boolean hurtClient(DamageSource damageSource) {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel p_376221_, DamageSource p_376460_, float p_376610_) {
        return false;
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return false;
    }

    @Override
    public boolean canBeSeenByAnyone() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {

    }

    @Override
    public void push(Vec3 vector) {
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public void push(double x, double y, double z) {
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Villager.createAttributes()
                .add(Attributes.MOVEMENT_SPEED, 0.0D);
    }

    @Override
    protected void registerGoals() {

    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level().isClientSide()) {
            boolean isOwner = ownerUuid != null && ownerUuid.equals(player.getUUID());

            if (isOwner && stack.is(ModItems.GARAGE_VILLAGER_SPAWN_EGG.get())) {
                CompoundTag data = this.saveToItemTag();
                if (stack.getCount() > 1) {
                    ItemStack single = stack.copyWithCount(1);
                    single.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
                    stack.shrink(1);

                    if (!player.addItem(single)) {
                        player.drop(single, false);
                    }
                } else {
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
                }
                this.discard();
                return InteractionResult.CONSUME;
            }

            if (isOwner && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(new SimpleMenuProvider(
                        (containerId, playerInventory, p) ->
                                new GarageVillagerOwnerMenu(containerId, playerInventory, this),
                        Component.literal(player.getName().getString())
                ));
                return InteractionResult.CONSUME;
            }

            if (!isOwner && player instanceof ServerPlayer serverPlayer) {
                this.setTradingPlayer(serverPlayer);
                this.rebuildOffersFromInventory();

                var optionalId = serverPlayer.openMenu(new SimpleMenuProvider(
                        (containerId, inv, p) -> new GarageMerchantMenu(containerId, inv, this),
                        this.getDisplayName()
                ));

                optionalId.ifPresent(id -> {
                    serverPlayer.sendMerchantOffers(
                            id,
                            this.getOffers(),
                            GARAGE_LEVEL,
                            0,
                            false,
                            false
                    );
                });

                return InteractionResult.CONSUME;
            }

            return InteractionResult.CONSUME;
        }

        return InteractionResult.SUCCESS;
    }


    @Override
    public void tick() {
        super.tick();

        setDeltaMovement(Vec3.ZERO);
        if (!onGround()) {
            setPos(position().x, Math.floor(position().y), position().z);
        }

        if (!level().isClientSide()) {
            Player nearest = level().getNearestPlayer(this, 5.0D);
            if (nearest != null) {
                getLookControl().setLookAt(nearest, 30.0F, 30.0F);
            }
        }
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    public void setOwner(Player player) {
        this.ownerUuid = player.getUUID();
        this.ownerName = player.getName().getString();
        setCustomName(Component.literal(this.ownerName));
        setCustomNameVisible(true);
    }


    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);

        if (ownerUuid != null) {
            out.putString("Owner", ownerUuid.toString());
        }
        out.putString("OwnerName", this.ownerName);

        NonNullList<ItemStack> items =
                NonNullList.withSize(inventory.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            items.set(i, inventory.getItem(i));
        }
        ContainerHelper.saveAllItems(out, items);

        out.putIntArray("Prices", prices);
        out.putInt("EmeraldBalance", emeraldBalance);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);

        in.getString("Owner").ifPresent(s -> {
            try {
                ownerUuid = UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {
                ownerUuid = null;
            }
        });

        this.ownerName = in.getStringOr("OwnerName", "Garage");

        if (this.ownerUuid != null) {
            this.setCustomName(Component.literal(this.ownerName));
            this.setCustomNameVisible(true);
        }

        NonNullList<ItemStack> items =
                NonNullList.withSize(inventory.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        inventory.clearContent();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            inventory.setItem(i, items.get(i));
        }
        in.getIntArray("Prices").ifPresent(loaded -> {
            Arrays.fill(this.prices, 0);
            System.arraycopy(
                    loaded, 0,
                    this.prices, 0,
                    Math.min(loaded.length, this.prices.length)
            );
        });

        emeraldBalance = in.getIntOr("EmeraldBalance", 0);
        this.rebuildOffersFromInventory();
    }

    public CompoundTag saveToItemTag() {
        CompoundTag tag = new CompoundTag();

        if (ownerUuid != null) {
            tag.putString("Owner", ownerUuid.toString());
        }
        tag.putString("OwnerName", this.ownerName);

        ListTag itemsTag = new ListTag();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                DataResult<Tag> result = ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack);
                int slot = i;
                result.result().ifPresent(stackTag -> {
                    if (stackTag instanceof CompoundTag stackCompound) {
                        stackCompound.putInt("Slot", slot);
                        itemsTag.add(stackCompound);
                    }
                });
            }
        }
        tag.put("Items", itemsTag);
        tag.putIntArray("Prices", this.prices);
        tag.putInt("EmeraldBalance", this.emeraldBalance);

        return tag;
    }

    public void loadFromItemTag(CompoundTag tag) {
        if (tag.contains("Owner")) {
            try {
                this.ownerUuid = UUID.fromString(tag.getStringOr("Owner", ""));
            } catch (IllegalArgumentException ignored) {
                this.ownerUuid = null;
            }
        } else {
            this.ownerUuid = null;
        }

        this.ownerName = tag.getStringOr("OwnerName", "Garage");

        if (this.ownerUuid != null) {
            this.setCustomName(Component.literal(getOwnerNameForDisplay()));
            this.setCustomNameVisible(true);
        }

        inventory.clearContent();
        ListTag itemsTag = tag.getListOrEmpty("Items");
        for (int i = 0; i < itemsTag.size(); i++) {
            Tag t = itemsTag.get(i);
            if (!(t instanceof CompoundTag stackTag)) continue;

            int slot = stackTag.getIntOr("Slot", 0);
            ItemStack stack = ItemStack.CODEC
                    .parse(NbtOps.INSTANCE, stackTag)
                    .result()
                    .orElse(ItemStack.EMPTY);

            if (!stack.isEmpty() && slot >= 0 && slot < inventory.getContainerSize()) {
                inventory.setItem(slot, stack);
            }
        }

        int[] loadedPrices = tag.getIntArray("Prices").orElse(new int[0]);
        Arrays.fill(this.prices, 0);
        System.arraycopy(
                loadedPrices, 0,
                this.prices, 0,
                Math.min(loadedPrices.length, this.prices.length)
        );

        this.emeraldBalance = tag.getIntOr("EmeraldBalance", 0);
    }

    private String getOwnerNameForDisplay() {
        return this.ownerName;
    }
}
