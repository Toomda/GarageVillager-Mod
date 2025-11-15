package com.toomda.garagevillager.network;

import com.toomda.garagevillager.GarageVillager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetGaragePricePayload(int containerId, int slotIndex, int price)
        implements CustomPacketPayload {

    public static final Type<SetGaragePricePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GarageVillager.MODID, "set_garage_price"));

    public static final StreamCodec<FriendlyByteBuf, SetGaragePricePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeVarInt(packet.containerId);
                        buf.writeVarInt(packet.slotIndex);
                        buf.writeVarInt(packet.price);
                    },
                    buf -> new SetGaragePricePayload(
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt()
                    )
            );

    @Override
    public Type<SetGaragePricePayload> type() {
        return TYPE;
    }
}
