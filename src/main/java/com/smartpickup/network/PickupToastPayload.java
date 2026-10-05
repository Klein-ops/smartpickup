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
 *
 * @param itemId registry id of the item, e.g. "minecraft:dirt"
 * @param count  number picked up; 0 means "blacklisted, refused"
 * @param color  text color (RGB, 0xRRGGBB) matching the item's rarity,
 *               computed server-side from the full ItemStack (enchantments
 *               boost the rarity, e.g. enchanted tools -> blue, enchanted
 *               golden apple -> purple)
 */
public record PickupToastPayload(String itemId, int count, int color) implements CustomPacketPayload {

    public static final Type<PickupToastPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModConstants.MODID, "pickup_toast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PickupToastPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.itemId());
                        buf.writeVarInt(payload.count());
                        buf.writeVarInt(payload.color());
                    },
                    buf -> new PickupToastPayload(buf.readUtf(), buf.readVarInt(), buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}