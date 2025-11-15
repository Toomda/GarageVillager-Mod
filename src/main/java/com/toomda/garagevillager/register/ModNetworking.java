package com.toomda.garagevillager.register;

import com.toomda.garagevillager.GarageVillager;
import com.toomda.garagevillager.menu.GarageVillagerOwnerMenu;
import com.toomda.garagevillager.network.CollectBalancePayload;
import com.toomda.garagevillager.network.SetGaragePricePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = GarageVillager.MODID)
public class ModNetworking {
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(GarageVillager.MODID);

        registrar.playToServer(
                SetGaragePricePayload.TYPE,
                SetGaragePricePayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                            return;
                        }

                        if (!(serverPlayer.containerMenu instanceof GarageVillagerOwnerMenu menu)) {
                            return;
                        }

                        if (menu.containerId != payload.containerId()) {
                            return;
                        }

                        int slot = payload.slotIndex();
                        int price = Math.max(0, payload.price());

                        menu.setPrice(slot, price);
                        // DataSlots syncen zurück zum Client
                    });
                }
        );

        registrar.playToServer(
                CollectBalancePayload.TYPE,
                CollectBalancePayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                            return;
                        }

                        if (!(serverPlayer.containerMenu instanceof GarageVillagerOwnerMenu menu)) {
                            return;
                        }

                        if (menu.containerId != payload.containerId()) {
                            return;
                        }

                        menu.collectBalance(serverPlayer);
                    });
                }
        );
    }


    public static void sendSetPrice(int containerId, int slotIndex, int price) {
        var mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return;
        }

        SetGaragePricePayload payload = new SetGaragePricePayload(containerId, slotIndex, price);
        mc.getConnection().send(new ServerboundCustomPayloadPacket(payload));
    }

    public static void sendCollectBalance(int containerId) {
        var mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return;
        }

        CollectBalancePayload payload = new CollectBalancePayload(containerId);
        mc.getConnection().send(new ServerboundCustomPayloadPacket(payload));
    }

}
