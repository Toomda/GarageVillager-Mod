package com.toomda.garagevillager.menu;

import com.toomda.garagevillager.entity.GarageVillagerEntity;
import com.toomda.garagevillager.register.ModMenus;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class GarageVillagerOwnerMenu  extends AbstractContainerMenu {

    private final GarageVillagerEntity villager;
    private final SimpleContainer inventory;
    private final int[] prices = new int[17];
    private int emeraldBalance;

    public GarageVillagerOwnerMenu(int containerId, Inventory playerInv)
    {
        this(containerId, playerInv, null);
    }

    public GarageVillagerOwnerMenu(int containerId, Inventory playerInv, GarageVillagerEntity villager)
    {
        super(ModMenus.GARAGE_VILLAGER_OWNER_MENU.get(), containerId);
        this.villager = villager;
        this.inventory = villager != null ? villager.getInventory() : new SimpleContainer(17);

        if (villager != null) {
            for (int i = 0; i < prices.length; i++) {
                prices[i] = villager.getPrice(i);
            }
            this.emeraldBalance = villager.getEmeraldBalance();
        } else {
            this.emeraldBalance = 0;
        }

        int startX = 8;
        int startY = 7;
        int slotSizeX = 24;
        int slotSizeY = 37;

        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 7; col++) {
                int index = row * 7 + col;
                if (index >= inventory.getContainerSize()) break;
                addVillagerSlot(index,
                        startX + col * slotSizeX,
                        startY + row * slotSizeY);
            }
        }

        int row3StartX = 56;
        int row3StartY = 81;

        for (int col = 0; col < 3; col++) {
            int index = 14 + col;
            addVillagerSlot(index,
                    row3StartX + col * slotSizeX,
                    row3StartY);
        }

        int playerInvY = 140;

        // Main inventory 3x9
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9,
                        8 + col * 18,
                        playerInvY + row * 18));
            }
        }

        // Hotbar
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInv, col,
                    8 + col * 18,
                    playerInvY + 58));
        }

        for (int i = 0; i < prices.length; i++) {
            final int idx = i;
            this.addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    return villager != null ? villager.getPrice(idx) : prices[idx];
                }

                @Override
                public void set(int value) {
                    prices[idx] = value;
                }
            });
        }

        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return villager != null ? villager.getEmeraldBalance() : emeraldBalance;
            }

            @Override
            public void set(int value) {
                emeraldBalance = value;
            }
        });

    }

    private void addVillagerSlot(int index, int x, int y) {
        this.addSlot(new Slot(inventory, index, x, y) {
            @Override
            public void setChanged() {
                super.setChanged();
                if (villager != null) {
                    villager.onTradesUpdated();
                }
            }
        });
    }


    public int getEmeraldBalance() {
        return emeraldBalance;
    }

    public boolean setEmeraldBalance(int value) {
        int clamped = Math.max(0, value);
        if (villager != null) {
            villager.setEmeraldBalance(clamped); // Server-Quelle
        }
        emeraldBalance = clamped; // lokale Kopie
        return true;
    }

    public void collectBalance(ServerPlayer player) {
        if (villager == null) return;
        if (emeraldBalance <= 0) return;

        int amount = emeraldBalance;
        emeraldBalance = 0;
        villager.setEmeraldBalance(0);

        int remaining = amount;

        while (remaining > 0) {
            int stackSize = Math.min(remaining, 64);
            ItemStack stack = new ItemStack(Items.EMERALD, stackSize);

            boolean added = player.getInventory().add(stack);
            if (!added) {
                player.drop(stack, false);
            }

            remaining -= stackSize;
        }

    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);

        if (villager != null && container == this.inventory) {
            villager.onTradesUpdatedFromOwner();
        }
    }


    public boolean changePrice(int slotIndex, int delta) {
        if (slotIndex < 0 || slotIndex >= prices.length) {
            return false;
        }

        int current = prices[slotIndex];
        int newPrice = Math.max(0, current + delta); // nie unter 0

        if (newPrice == current) {
            // nichts geändert (z.B. von 0 auf -1 versucht)
            return false;
        }

        prices[slotIndex] = newPrice;

        if (villager != null) {
            villager.setPrice(slotIndex, newPrice);
            villager.onTradesUpdatedFromOwner();
        }

        return true;
    }

    public int getPrice(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= prices.length) {
            return 0;
        }
        return prices[slotIndex];
    }

    public boolean setPrice(int slotIndex, int value) {
        if (slotIndex < 0 || slotIndex >= prices.length) {
            return false;
        }

        int newPrice = Math.max(0, value);
        int current = prices[slotIndex];

        if (newPrice == current) {
            return false;
        }

        prices[slotIndex] = newPrice;

        if (villager != null) {
            villager.setPrice(slotIndex, newPrice);
            villager.onTradesUpdatedFromOwner(); // 🔥 wichtig für Live-Update beim Käufer
        }

        return true;
    }


    @Override
    public ItemStack quickMoveStack(Player player, int i) {
        return ItemStack.EMPTY;
    }

    public GarageVillagerEntity getVillager() {
        return villager;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
