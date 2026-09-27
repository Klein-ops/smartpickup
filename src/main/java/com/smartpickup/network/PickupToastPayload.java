package com.smartpickup.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.smartpickup.ModConstants;

/**
 * Server -> Client payload: tells the client to show a pickup toast
 * above the hotbar. Sent when the server handles an item pickup
 * (and optionally when a blacklisted item is refused).
 */
public record PickupToastPayload(String itemId, int count) implements CustomPacketPayload {

    public static final Type<PickupToastPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModConstants.MODID, "pickup_toast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PickupToastPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.itemId());
                        buf.writeVarInt(payload.count());
                    },
                    buf -> new PickupToastPayload(buf.readUtf(), buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}