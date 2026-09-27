package com.smartpickup;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Feature 2: automatically refill an emptied or broken held stack from the
 * main inventory (slots 9..35), using a whole-stack swap.
 *
 * <p>Two sub-scenarios, each with its own config toggle:
 * <ul>
 *   <li><b>Used up (count reaches 0)</b> e.g. arrows, food, throwables:
 *       detected every server tick by comparing the previous tick's held stack
 *       with the current one. If the stack was a non-damageable item with a
 *       positive count last tick and is now empty, it was consumed -> refill.</li>
 *   <li><b>Broken (durability exhausted)</b>: handled precisely via
 *       {@link net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent}
 *       which fires exactly when a held tool breaks.</li>
 * </ul>
 *
 * <p>Refills only while <b>no GUI is open</b> on the client (the client syncs its screen
 * state to the server), never in Creative when configured, and does nothing when no
 * matching stack exists.
 */
public final class RefillHandler {

    private RefillHandler() {
    }

    private record HandSnapshot(int mainSlot, ItemStack main, ItemStack off) {
    }

    /** Previous-tick snapshot of each player's held stacks, used to detect "used up". */
    private static final Map<Player, HandSnapshot> LAST = new IdentityHashMap<>();

    /**
     * Called every server tick for each player.
     */
    public static void tick(Player player) {
        if (!Config.masterEnabled || !Config.refillEnabled) {
            return;
        }
        if (Config.refillSkipCreative && player.getAbilities().instabuild) {
            return;
        }
        // Refill only while NO GUI is open on the client. The server cannot distinguish
        // "nothing open" from the player's own inventory screen (both use InventoryMenu),
        // so the client reports its screen state. While any GUI is open the player may be
        // rearranging items, which must not be mistaken for the held item being used up.
        if (!(player.containerMenu instanceof InventoryMenu)) {
            return;
        }
        if (ScreenStateTracker.isOpen(player.getUUID())) {
            return;
        }

        Inventory inv = player.getInventory();
        int mainSlot = inv.selected;
        int offSlot = Inventory.SLOT_OFFHAND;
        ItemStack main = inv.getItem(mainSlot);
        ItemStack off = inv.getItem(offSlot);

        HandSnapshot prev = LAST.put(player, new HandSnapshot(mainSlot, main.copy(), off.copy()));
        if (prev == null) {
            return; // first tick, nothing to compare
        }

        // Main hand: non-damageable item vanished from the SAME selected slot -> consumed.
        // (Requiring the same slot avoids a false positive when the player simply switches
        // to an empty hotbar slot.)
        if (Config.refillOnEmpty
                && mainSlot == prev.mainSlot()
                && !prev.main().isEmpty()
                && !prev.main().isDamageableItem()
                && main.isEmpty()) {
            tryRefill(inv, mainSlot, prev.main());
        }
        // Off hand: same check.
        if (Config.refillOnEmpty
                && !prev.off().isEmpty()
                && !prev.off().isDamageableItem()
                && off.isEmpty()) {
            tryRefill(inv, offSlot, prev.off());
        }
    }

    /**
     * Called from {@code PlayerDestroyItemEvent} when a held tool/weapon breaks.
     * The event gives us the exact hand and the original (broken) stack.
     */
    public static void onItemBroken(Player player, InteractionHand hand, ItemStack broken) {
        if (!Config.masterEnabled || !Config.refillEnabled || !Config.refillOnBreak) {
            return;
        }
        if (Config.refillSkipCreative && player.getAbilities().instabuild) {
            return;
        }
        if (!(player.containerMenu instanceof InventoryMenu)) {
            return;
        }
        if (ScreenStateTracker.isOpen(player.getUUID())) {
            return;
        }
        Inventory inv = player.getInventory();
        int slot = hand == InteractionHand.MAIN_HAND ? inv.selected : Inventory.SLOT_OFFHAND;
        tryRefill(inv, slot, broken);
    }

    /**
     * Searches the main inventory (9..35) for a stack of the same item as
     * {@code old} and performs a whole-stack swap into the hand slot.
     * Does nothing if no match is found.
     */
    private static void tryRefill(Inventory inv, int handSlot, ItemStack old) {
        if (old == null || old.isEmpty()) {
            return;
        }
        for (int i = 9; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && ItemStack.isSameItem(s, old)) {
                ItemStack currentInHand = inv.getItem(handSlot);
                inv.setItem(handSlot, s);
                inv.setItem(i, currentInHand);
                return;
            }
        }
    }
}