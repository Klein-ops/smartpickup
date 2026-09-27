package com.smartpickup.network;

import com.smartpickup.ModConstants;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server payload telling the server whether the player currently has a
 * screen (GUI) open.
 *
 * <p>The server cannot tell the difference between "no screen open" and "the player's
 * own inventory screen is open" - in both cases {@code containerMenu} is the
 * {@link net.minecraft.world.inventory.InventoryMenu}. Auto-refill must therefore only
 * run while nothing is open on the client, otherwise rearranging items in the inventory
 * would be mistaken for "the held item was used up".
 */
public record ScreenStatePayload(boolean open) implements CustomPacketPayload {

    public static final Type<ScreenStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModConstants.MODID, "screen_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ScreenStatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeBoolean(payload.open()),
                    buf -> new ScreenStatePayload(buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}