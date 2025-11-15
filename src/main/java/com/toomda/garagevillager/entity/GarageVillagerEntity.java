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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
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

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void rebuildOffersFromInventory() {
        this.offers.clear();
        this.offerToSlot.clear();

        for (int i = 0; i < this.inventory.getContainerSize(); i++) {
            ItemStack stack = this.inventory.getItem(i);
            int price = this.prices[i];

            if (stack.isEmpty() || price <= 0) {
                continue;
            }
            ItemStack result = stack.copy();
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

                serverPlayer.sendMerchantOffers(
                        merchantMenu.containerId,
                        currentOffers,
                        0,
                        this.getVillagerXp(),
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
                            0,
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
            this.inventory.setItem(slotIndex, ItemStack.EMPTY);
            this.prices[slotIndex] = 0;
        }

        this.rebuildOffersFromInventory();
        syncOffersToOpenBuyers_afterPurchase();
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
                        0,
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
                            0,
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
