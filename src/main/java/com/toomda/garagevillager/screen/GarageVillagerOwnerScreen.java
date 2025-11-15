package com.toomda.garagevillager.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.toomda.garagevillager.GarageVillager;
import com.toomda.garagevillager.menu.GarageVillagerOwnerMenu;
import com.toomda.garagevillager.register.ModNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.swing.text.Style;

public class GarageVillagerOwnerScreen extends AbstractContainerScreen<GarageVillagerOwnerMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    GarageVillager.MODID,
                    "textures/gui/garage_villagers/owner_inventory.png"
            );

    private EditBox priceField;
    private int editingSlot = -1;
    private Button collectButton;

    public GarageVillagerOwnerScreen(GarageVillagerOwnerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);

        this.imageWidth = 176;
        this.imageHeight = 222;
    }

    @Override
    protected void init() {
        super.init();

        String collectText = "Collect";
        int buttonWidth = 50;
        int buttonHeight = 15;

        int x = this.leftPos + this.imageWidth - buttonWidth - 8;
        int y = this.topPos + 118;

        this.collectButton = Button.builder(
                Component.literal("Collect"),
                btn -> onCollectPressed()
        ).size(buttonWidth, buttonHeight).pos(x, y).build();

        this.addRenderableWidget(this.collectButton);
    }

    private void onCollectPressed() {
        playClickSound();
        ModNetworking.sendCollectBalance(menu.containerId);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        float u0 = 0f;
        float u1 = this.imageWidth / 256f;   // 176 / 256
        float v0 = 0f;
        float v1 = this.imageHeight / 256f;  // 222 / 256

        guiGraphics.blit(
                TEXTURE,
                x, y,
                x + this.imageWidth, y + this.imageHeight,
                u0, u1,
                v0, v1
        );
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderPriceControls(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (priceField != null) {
            priceField.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        if (collectButton != null) {
            collectButton.active = menu.getEmeraldBalance() > 0;
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, 8, 6, 0x404040, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean p_432883_) {
        if (mouseButtonEvent.button() == 0) { // Left click
            int slotsToRender = 17;
            for (int i = 0; i < slotsToRender; i++) {
                Slot slot = this.menu.slots.get(i);

                int slotScreenX = this.leftPos + slot.x;
                int slotScreenY = this.topPos + slot.y;

                int emeraldX = slotScreenX + 3;
                int emeraldY = slotScreenY + 18;

                int minusX = emeraldX - 5;
                int minusY = emeraldY + 2;

                int plusX = emeraldX + 11;
                int plusY = emeraldY + 2;

                // --- '-' klick ---
                if (mouseButtonEvent.x() >= minusX && mouseButtonEvent.x() <= minusX + 7 &&
                        mouseButtonEvent.y() >= minusY && mouseButtonEvent.y() <= minusY + 9) {

                    int current = menu.getPrice(i);
                    int newPrice = Math.max(0, current - 1);

                    if (menu.setPrice(i, newPrice)) {              // Client-Vorschau
                        ModNetworking.sendSetPrice(menu.containerId, i, newPrice); // Server sync
                        playClickSound();
                    }
                    return true;
                }

                // --- '+' klick ---
                if (mouseButtonEvent.x() >= plusX && mouseButtonEvent.x() <= plusX + 7 &&
                        mouseButtonEvent.y() >= plusY && mouseButtonEvent.y() <= plusY + 9) {

                    int current = menu.getPrice(i);
                    int newPrice = current + 1;

                    if (menu.setPrice(i, newPrice)) {
                        ModNetworking.sendSetPrice(menu.containerId, i, newPrice);
                        playClickSound();
                    }
                    return true;
                }

                int emeraldSize = (int)(16 * 0.6f);

                if (mouseButtonEvent.x() >= emeraldX &&
                        mouseButtonEvent.x() <= emeraldX + emeraldSize &&
                        mouseButtonEvent.y() >= emeraldY &&
                        mouseButtonEvent.y() <= emeraldY + emeraldSize) {

                    openPriceEditField(i, emeraldX, emeraldY);
                    return true;
                }
            }
        }

        // Klick an EditBox weitergeben, falls vorhanden
        if (priceField != null && priceField.mouseClicked(mouseButtonEvent, p_432883_)) {
            return true;
        }

        return super.mouseClicked(mouseButtonEvent, p_432883_);
    }

    private void openPriceEditField(int slotIndex, int emeraldX, int emeraldY) {
        this.editingSlot = slotIndex;

        if (this.priceField != null) {
            this.removeWidget(this.priceField);
            this.priceField = null;
        }

        int width = 30;
        int height = 12;

        int fieldX = emeraldX - 8;
        int fieldY = emeraldY - height;

        this.priceField = new EditBox(
                this.font,
                fieldX,
                fieldY,
                width,
                height,
                Component.empty()
        );

        this.priceField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));

        int currentPrice = this.menu.getPrice(slotIndex);
        this.priceField.setValue(String.valueOf(currentPrice));
        this.priceField.setMaxLength(5);
        this.priceField.setFocused(true);
        this.priceField.setBordered(true);

        this.addRenderableWidget(this.priceField);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (priceField != null && priceField.isFocused()) {
            if (priceField.charTyped(event)) {
                return true;
            }
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (priceField != null && priceField.isFocused()) {
            int key = keyEvent.key();
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                commitPriceEditField();
                return true;
            }

            if (key == InputConstants.KEY_ESCAPE) {
                closePriceEditField();
                return true;
            }

            if (priceField.keyPressed(keyEvent)) {
                return true;
            }
        }

        return super.keyPressed(keyEvent);
    }


    private void commitPriceEditField() {
        if (priceField == null || editingSlot < 0) return;

        String text = priceField.getValue().trim();
        if (!text.isEmpty()) {
            try {
                int value = Integer.parseInt(text);
                int clamped = Math.max(0, value);

                menu.setPrice(editingSlot, clamped);

                // IMMER an den Server schicken
                ModNetworking.sendSetPrice(menu.containerId, editingSlot, clamped);
                playClickSound();
            } catch (NumberFormatException ignored) {
            }
        }

        closePriceEditField();
    }

    @Override
    public void onClose() {
        if (priceField != null) {
            commitPriceEditField();
        }
        super.onClose();
    }

    private void closePriceEditField() {
        if (this.priceField != null) {
            this.removeWidget(this.priceField);
            this.priceField = null;
        }
        this.editingSlot = -1;
    }


    private void playClickSound() {
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void renderPriceControls(GuiGraphics guiGraphics) {
        int slotsToRender = 17;
        ItemStack emeraldStack = new ItemStack(Items.EMERALD);

        for (int i = 0; i < slotsToRender; i++) {
            Slot slot = this.menu.slots.get(i);

            // Screen-Koordinaten (!)
            int slotScreenX = this.leftPos + slot.x;
            int slotScreenY = this.topPos + slot.y;

            int emeraldX = slotScreenX + 3;
            int emeraldY = slotScreenY + 18;

            int price = this.menu.getPrice(i);
            String priceText = String.valueOf(price);

            // Emerald + kleine Stack-Zahl
            renderSmallItemWithCount(guiGraphics, emeraldStack, emeraldX, emeraldY, 0.6f, priceText);

            // '-' links vom Emerald
            int minusX = emeraldX - 5;
            int minusY = emeraldY + 2;
            guiGraphics.drawString(this.font, "-", minusX, minusY, 0xFFFF5555, false);

            // '+' rechts, leicht höher gezogen
            int plusX = emeraldX + 11;
            int plusY = emeraldY + 2;
            guiGraphics.drawString(this.font, "+", plusX, plusY, 0xFF55FF55, false);
        }

        int startX = this.leftPos + 4;
        int startY = this.topPos + 124;

        int emeraldX = startX;
        int emeraldY = startY - 6;

        renderSmallItemWithCount(guiGraphics, emeraldStack, emeraldX, emeraldY, 1.0f, "");


        int emeraldWidth = (int)(16 * 1.0f);

        String label = "Balance:";
        int labelX = emeraldX + emeraldWidth + 2;
        guiGraphics.drawString(this.font, label, labelX, startY - 2, 0xFFFFFFFF, true);

        int balance = this.menu.getEmeraldBalance();
        String goldValue = String.valueOf(balance);
        int goldX = labelX + this.font.width(label) + 5;
        guiGraphics.drawString(this.font, goldValue, goldX, startY - 1, 0xFFFFD700, true);
    }


    private void renderSmallItemWithCount(GuiGraphics guiGraphics, ItemStack stack, int x, int y, float scale, String countText) {
        var pose = guiGraphics.pose();
        pose.pushMatrix();
        pose.translate((float) x, (float) y);
        pose.scale(scale, scale);

        guiGraphics.renderItem(stack, 0, 0);
        guiGraphics.renderItemDecorations(this.font, stack, 0, 0, countText);

        pose.popMatrix();
    }

}
