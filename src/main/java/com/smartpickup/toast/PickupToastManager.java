package com.smartpickup.toast;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import com.smartpickup.Config;

/**
 * Client-side manager for the pickup toast (Feature 4).
 *
 * <p>Shows a short action-bar message above the hotbar when items are picked
 * up. Consecutive pickups of the same item within the configured merge window
 * are summed into one message instead of spamming the action bar.
 */
public final class PickupToastManager {

    private static String lastItemId = null;
    private static int lastCount = 0;
    private static long lastShownTick = Long.MIN_VALUE;

    private PickupToastManager() {
    }

    /**
     * Called when a pickup toast payload arrives from the server.
     *
     * @param itemId registry id of the item, e.g. "minecraft:dirt"
     * @param count  number picked up; 0 means "blacklisted, refused"
     */
    public static void onPickup(String itemId, int count) {
        if (!Config.toastEnabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        if (count == 0) {
            // Blacklist rejection message.
            Component name = itemDisplayName(itemId);
            if (name != null) {
                mc.player.displayClientMessage(
                        Component.translatable("message.smartpickup.blacklist_rejected", name), true);
            }
            lastItemId = null; // don't merge rejections with pickups
            return;
        }

        // Merge consecutive pickups of the same item within the merge window.
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        double windowTicks = Config.toastMergeWindow * 20.0;
        if (itemId.equals(lastItemId) && now - lastShownTick <= windowTicks) {
            lastCount += count;
        } else {
            lastItemId = itemId;
            lastCount = count;
        }
        lastShownTick = now;

        Component name = itemDisplayName(itemId);
        if (name == null) {
            return;
        }
        MutableComponent msg = Component.translatable("message.smartpickup.picked", name, lastCount);
        mc.player.displayClientMessage(msg, true);
    }

    private static Component itemDisplayName(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("air"))) {
            return null;
        }
        return item.getDescription().copy();
    }
}