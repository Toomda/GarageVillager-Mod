package com.toomda.garagevillager.entity;

import com.mojang.serialization.DataResult;
import com.toomda.garagevillager.menu.GarageMerchantMenu;
import com.toomda.garagevillager.menu.GarageVillagerOwnerMenu;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
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

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

// GarageVillagerEntity.java
public class GarageVillagerEntity extends Villager {
    private UUID ownerUuid;
    private String ownerName = "Garage";   // <--- neu
    private final SimpleContainer inventory = new SimpleContainer(17);
    private final int[] prices = new int[17];
    private int emeraldBalance = 0;
    private final MerchantOffers offers = new MerchantOffers();
    private final Map<MerchantOffer, Integer> offerToSlot = new IdentityHashMap<>();



    public GarageVillagerEntity(EntityType<? extends GarageVillagerEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setInvulnerable(true);
    }

    @Override
    public MerchantOffers getOffers() {
        return this.offers;
    }

    public void rebuildOffersFromInventory() {
        this.offers.clear();
        this.offerToSlot.clear();

        for (int i = 0; i < this.inventory.getContainerSize(); i++) {
            ItemStack stack = this.inventory.getItem(i);
            int price = this.prices[i];

            // Nur Slots mit Item und Preis > 0
            if (stack.isEmpty() || price <= 0) {
                continue;
            }

            // Käufer bekommt den GESAMTEN Stack aus dem Slot
            ItemStack result = stack.copy(); // Count bleibt wie im Slot

            // uses = 0, maxUses = 1 -> genau EIN Trade möglich
            MerchantOffer offer = new MerchantOffer(
                    new ItemCost(Items.EMERALD, price),
                    result,
                    1,
                    0,
                    0.0F
            );

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

                boolean hasActiveTrade = !merchantMenu.getSlot(2).getItem().isEmpty();

                System.out.println("[GarageVillager] syncOffers (owner-update) -> "
                        + serverPlayer.getName().getString()
                        + " active=" + hasActiveTrade);

                if (hasActiveTrade) {
                    continue;
                }

                serverPlayer.sendMerchantOffers(
                        merchantMenu.containerId,
                        currentOffers,
                        1,
                        this.getVillagerXp(),
                        this.showProgressBar(),
                        this.canRestock()
                );
            }
        }
    }



    public void onTradesUpdatedFromOwner() {
        System.out.println("[GarageVillager] onTradesUpdatedFromOwner called on " + this);
        this.rebuildOffersFromInventory();
        this.syncOffersToOpenBuyers_skipActiveTrades(); // Schutz vor aktivem Trade
    }

    public void onTradesUpdated() {
        System.out.println("[GarageVillager] onTradesUpdated called on " + this);

        this.rebuildOffersFromInventory();

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        for (ServerPlayer serverPlayer : serverLevel.players()) {
            if (serverPlayer.containerMenu instanceof GarageMerchantMenu merchantMenu &&
                    merchantMenu.getVillager() == this) {

                System.out.println("[GarageVillager] sending offers to " + serverPlayer.getName().getString());

                boolean hasActiveTrade = !merchantMenu.getSlot(2).getItem().isEmpty();

                System.out.println("[GarageVillager] has active trade " + hasActiveTrade);

                if(!hasActiveTrade)
                {
                    serverPlayer.sendMerchantOffers(
                            merchantMenu.containerId,
                            this.getOffers(),
                            1,
                            this.getVillagerXp(),
                            this.showProgressBar(),
                            this.canRestock()
                    );
                }
            }
        }
    }



    @Override
    public void notifyTrade(MerchantOffer offer) {
        super.notifyTrade(offer);

        System.out.println("[GarageVillager] Notifying trade");

        int emeraldsPaid = 0;

        ItemStack costA = offer.getCostA();
        if (!costA.isEmpty() && costA.is(Items.EMERALD)) {
            emeraldsPaid += costA.getCount();
        }

        ItemStack costB = offer.getCostB();
        if (!costB.isEmpty() && costB.is(Items.EMERALD)) {
            emeraldsPaid += costB.getCount();
        }

        if (emeraldsPaid > 0) {
            this.addEmeraldBalance(emeraldsPaid);
        }

        Integer slotIndex = this.offerToSlot.get(offer);
        if (slotIndex != null) {
            // Item & Preis aus deinem Storage entfernen
            this.inventory.setItem(slotIndex, ItemStack.EMPTY);
            this.prices[slotIndex] = 0;
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
        prices[slot] = Math.max(0, price);
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
        // KEINE normalen Goals -> keine Bewegung
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level().isClientSide()) {
            boolean isOwner = ownerUuid != null && ownerUuid.equals(player.getUUID());

            // Owner + Spawn-Ei -> einsammeln etc.
            if (isOwner && stack.is(ModItems.GARAGE_VILLAGER_SPAWN_EGG.get())) {
                // 1) Aktuellen Zustand der Entity in ein Tag schreiben
                CompoundTag data = this.saveToItemTag();

                // 2) Falls das Ei gestackt ist, erzeugen wir ein einzelnes Ei mit Daten
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

            // Owner -> dein Owner-GUI
            if (isOwner && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(new SimpleMenuProvider(
                        (containerId, playerInventory, p) ->
                                new GarageVillagerOwnerMenu(containerId, playerInventory, this),
                        Component.literal(player.getName().getString() + "'s Garage Sale")
                ));
                return InteractionResult.CONSUME;
            }

            if (!isOwner && player instanceof ServerPlayer serverPlayer) {
                this.setTradingPlayer(serverPlayer);
                this.rebuildOffersFromInventory();

                // 2) MerchantMenu öffnen – aber mit unserem GarageMerchantMenu
                var optionalId = serverPlayer.openMenu(new SimpleMenuProvider(
                        (containerId, inv, p) -> new GarageMerchantMenu(containerId, inv, this),
                        this.getDisplayName()
                ));

                // 3) Wenn erfolgreich geöffnet, Offers zum Client schicken
                optionalId.ifPresent(id -> {
                    serverPlayer.sendMerchantOffers(
                            id,
                            this.getOffers(),
                            1, // Level-Anzeige
                            this.getVillagerXp(),
                            this.showProgressBar(),
                            this.canRestock()
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
        this.ownerName = player.getName().getString();   // Namen merken
        setCustomName(Component.literal(this.ownerName + "'s Garage Sale"));
        setCustomNameVisible(true);
    }


    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);

        if (ownerUuid != null) {
            out.putString("Owner", ownerUuid.toString());
        }
        out.putString("OwnerName", this.ownerName); // <--- neu

        // Inventory -> NonNullList -> ContainerHelper.saveAllItems
        NonNullList<ItemStack> items =
                NonNullList.withSize(inventory.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            items.set(i, inventory.getItem(i));
        }
        // Schreibt wie früher direkt in die Entity-Daten
        ContainerHelper.saveAllItems(out, items);

        // Prices + EmeraldBalance
        out.putIntArray("Prices", prices);
        out.putInt("EmeraldBalance", emeraldBalance);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);

        // Owner laden
        in.getString("Owner").ifPresent(s -> {
            try {
                ownerUuid = UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {
                ownerUuid = null;
            }
        });

        this.ownerName = in.getStringOr("OwnerName", "Garage");

        if (this.ownerUuid != null) {
            this.setCustomName(Component.literal(this.ownerName + "'s Garage Sale"));
            this.setCustomNameVisible(true);
        }

        // Inventory laden
        NonNullList<ItemStack> items =
                NonNullList.withSize(inventory.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        inventory.clearContent();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            inventory.setItem(i, items.get(i));
        }

        // Prices
        in.getIntArray("Prices").ifPresent(loaded -> {
            Arrays.fill(this.prices, 0);
            System.arraycopy(
                    loaded, 0,
                    this.prices, 0,
                    Math.min(loaded.length, this.prices.length)
            );
        });

        // EmeraldBalance
        emeraldBalance = in.getIntOr("EmeraldBalance", 0);
        this.rebuildOffersFromInventory();
    }

    public CompoundTag saveToItemTag() {
        CompoundTag tag = new CompoundTag();

        // --- Owner ---
        if (ownerUuid != null) {
            tag.putString("Owner", ownerUuid.toString());
        }
        tag.putString("OwnerName", this.ownerName); // <--- neu

        // --- Inventar -> ListTag ("Items") ---
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

        // --- Preise + Emeralds ---
        tag.putIntArray("Prices", this.prices);
        tag.putInt("EmeraldBalance", this.emeraldBalance);

        return tag;
    }

    // Tag aus dem Item-Ei wieder in die Entity laden
    public void loadFromItemTag(CompoundTag tag) {
        // --- Owner laden ---
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
            this.setCustomName(Component.literal(getOwnerNameForDisplay() + "'s Garage Sale"));
            this.setCustomNameVisible(true);
        }

        // --- Inventar laden ---
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

        // --- Preise ---
        int[] loadedPrices = tag.getIntArray("Prices").orElse(new int[0]);
        Arrays.fill(this.prices, 0);
        System.arraycopy(
                loadedPrices, 0,
                this.prices, 0,
                Math.min(loadedPrices.length, this.prices.length)
        );

        // --- Emerald-Balance ---
        this.emeraldBalance = tag.getIntOr("EmeraldBalance", 0);
    }


    // Optional kleine Helfer-Methode:
    private String getOwnerNameForDisplay() {
        return this.ownerName;
    }
}
