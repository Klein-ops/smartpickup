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
 * <p>Feeds a stacked HUD list ({@link PickupToastRenderer}) instead of the
 * vanilla action-bar message, so multiple pickups can be shown at once.
 * Consecutive pickups of the same item within the configured merge window
 * are summed into one entry instead of spamming the list.
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
            // Blacklist rejection message. Use updateLatest so repeated
            // rejections of the same item (server-side cooldown aside) merge
            // into one entry instead of stacking.
            Component name = itemDisplayName(itemId);
            if (name != null) {
                PickupToastRenderer.updateLatest(
                        itemId,
                        Component.translatable("message.smartpickup.blacklist_rejected", name));
            }
            lastItemId = null; // don't merge rejections with pickups
            return;
        }

        // Merge consecutive pickups of the same item within the merge window:
        // update the existing entry instead of stacking a new one.
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        double windowTicks = Config.toastMergeWindow * 20.0;
        boolean merging = itemId.equals(lastItemId) && now - lastShownTick <= windowTicks;
        if (merging) {
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
        if (merging) {
            PickupToastRenderer.updateLatest(itemId, msg);
        } else {
            PickupToastRenderer.push(itemId, msg);
        }
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