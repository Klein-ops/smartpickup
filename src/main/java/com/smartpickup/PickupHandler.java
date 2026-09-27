package com.smartpickup;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Feature 1: intelligent pickup order.
 *
 * <p>Vanilla behaviour fills the hotbar (slots 0..8) first, then the main
 * inventory (slots 9..35). This handler inverts that for <em>new</em> slots,
 * while still respecting the user's clarified rule:
 * <ul>
 *   <li>If the hotbar already contains a partially filled stack of the same
 *       item, it is still used first (this is vanilla behaviour and is kept).</li>
 *   <li>Otherwise prefer partial stacks in the main inventory, then empty
 *       inventory slots, then empty hotbar slots.</li>
 *   <li>If nothing at all can take the item, vanilla behaviour is kept
 *       (the item stays on the ground).</li>
 *   <li>Items on the hotbar-priority whitelist keep the vanilla order:
 *       hotbar partial stacks, then empty hotbar slots, then the inventory.</li>
 * </ul>
 */
public final class PickupHandler {

    private PickupHandler() {
    }

    /**
     * Attempts to add the given item to the player's inventory following the
     * smart-pickup priority. Returns the number of items that were <em>not</em>
     * picked up (0 = fully picked up).
     *
     * <p>Inventory slot layout: 0-8 hotbar, 9-35 main inventory, 36-39 armour, 40 offhand.
     */
    public static int smartPickup(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        Inventory inv = player.getInventory();

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        // Items on the hotbar-priority whitelist keep vanilla pickup order:
        // hotbar partial stacks -> hotbar empty slots -> inventory.
        if (Config.isHotbarPriority(itemId)) {
            return vanillaOrder(inv, stack);
        }

        // Pass 1: partial stacks in the hotbar (vanilla preference, kept deliberately).
        int remaining = addToPartialStacks(inv, stack, 0, 9);
        if (remaining == 0) {
            return 0;
        }
        stack.setCount(remaining);

        // Pass 2: partial stacks in the main inventory (9..35).
        remaining = addToPartialStacks(inv, stack, 9, 36);
        if (remaining == 0) {
            return 0;
        }
        stack.setCount(remaining);

        // Pass 3: empty slots in the main inventory (9..35).
        remaining = addToEmptySlots(inv, stack, 9, 36);
        if (remaining == 0) {
            return 0;
        }
        stack.setCount(remaining);

        // Pass 4: empty slots in the hotbar (0..8) - only when inventory is full.
        remaining = addToEmptySlots(inv, stack, 0, 9);
        return remaining;
    }

    /**
     * Vanilla pickup order for whitelisted items: partial stacks in the hotbar,
     * then empty hotbar slots, then partial stacks and empty slots in the main
     * inventory. Returns the number of items not picked up.
     */
    private static int vanillaOrder(Inventory inv, ItemStack stack) {
        // Pass 1: partial stacks in the hotbar (0..8).
        int remaining = addToPartialStacks(inv, stack, 0, 9);
        if (remaining == 0) {
            return 0;
        }
        stack.setCount(remaining);

        // Pass 2: empty slots in the hotbar (0..8).
        remaining = addToEmptySlots(inv, stack, 0, 9);
        if (remaining == 0) {
            return 0;
        }
        stack.setCount(remaining);

        // Pass 3: partial stacks in the main inventory (9..35).
        remaining = addToPartialStacks(inv, stack, 9, 36);
        if (remaining == 0) {
            return 0;
        }
        stack.setCount(remaining);

        // Pass 4: empty slots in the main inventory (9..35).
        remaining = addToEmptySlots(inv, stack, 9, 36);
        return remaining;
    }

    private static int addToPartialStacks(Inventory inv, ItemStack stack, int from, int to) {
        int remaining = stack.getCount();
        for (int i = from; i < to; i++) {
            ItemStack slot = inv.getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItem(slot, stack)
                    && ItemStack.isSameItemSameComponents(slot, stack)
                    && slot.getCount() < slot.getMaxStackSize()) {
                int space = slot.getMaxStackSize() - slot.getCount();
                int add = Math.min(space, remaining);
                slot.grow(add);
                remaining -= add;
                if (remaining <= 0) {
                    return 0;
                }
            }
        }
        return remaining;
    }

    private static int addToEmptySlots(Inventory inv, ItemStack stack, int from, int to) {
        int remaining = stack.getCount();
        for (int i = from; i < to; i++) {
            if (inv.getItem(i).isEmpty()) {
                int maxSize = stack.getMaxStackSize();
                int add = Math.min(maxSize, remaining);
                ItemStack copy = stack.copy();
                copy.setCount(add);
                inv.setItem(i, copy);
                remaining -= add;
                if (remaining <= 0) {
                    return 0;
                }
            }
        }
        return remaining;
    }

    /**
     * Plays the vanilla item pickup sound for the player. Used instead of the
     * default sound because we cancel the vanilla pickup event.
     */
    public static void playPickupSound(Player player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                0.2F, (player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 1.4F + 2.0F);
    }
}