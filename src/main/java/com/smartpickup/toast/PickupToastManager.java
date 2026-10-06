package com.smartpickup.toast;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

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
     * @param itemId registry id of the item, e.g. "minecraft:dirt"; used only
     *               as the merge key for consecutive pickups
     * @param count  number picked up; 0 means "blacklisted, refused"
     * @param color  text color (RGB) matching the item's rarity, computed
     *               server-side from the full ItemStack
     * @param name   the item's display name (includes custom names given on
     *               an anvil), computed server-side from the full ItemStack
     */
    public static void onPickup(String itemId, int count, int color, Component name) {
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
            PickupToastRenderer.updateLatest(
                    itemId,
                    Component.translatable("message.smartpickup.blacklist_rejected", name),
                    color);
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

        MutableComponent msg = Component.translatable("message.smartpickup.picked", name, lastCount);
        if (merging) {
            PickupToastRenderer.updateLatest(itemId, msg, color);
        } else {
            PickupToastRenderer.push(itemId, msg, color);
        }
    }
}