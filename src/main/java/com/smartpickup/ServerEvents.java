package com.smartpickup;

import com.smartpickup.network.PickupToastPayload;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-side event wiring for all four features.
 * Registered on {@link NeoForge#EVENT_BUS} from the mod constructor.
 */
public final class ServerEvents {

    /**
     * Cooldown (ticks) before the same item entity triggers another
     * "blacklist rejected" toast. Prevents spam: the pickup-pre event fires
     * every tick while the player stands on a refused item, but the player
     * should only see one toast per entity.
     */
    private static final long REJECT_TOAST_COOLDOWN_TICKS = 20L;

    /** Last tick a reject toast was sent, keyed by item-entity UUID. */
    private static final java.util.Map<java.util.UUID, Long> REJECT_TOAST_LAST_SENT = new java.util.HashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.register(ServerEvents.class);
    }

    // ------------------------------------------------------------------
    // Feature 1 + blacklist + toast: pickup interception
    // ------------------------------------------------------------------

    /**
     * Fired on the logical server when a player collides with an item entity
     * and it is eligible for pickup. We intercept it here to apply the smart
     * pickup order instead of the vanilla hotbar-first order.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPickupPre(ItemEntityPickupEvent.Pre event) {
        if (!Config.masterEnabled) {
            return;
        }
        ServerPlayer player = (ServerPlayer) event.getPlayer();
        ItemEntity entity = event.getItemEntity();
        ItemStack stack = entity.getItem();
        if (stack.isEmpty()) {
            return;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        // Feature 3: blacklist -> refuse pickup entirely.
        if (Config.blacklistEnabled && Config.isBlacklisted(itemId)) {
            event.setCanPickup(TriState.FALSE);
            // The Pre event fires every tick while the player stands on the
            // refused item; only send one toast per item entity per second.
            if (Config.toastEnabled && Config.toastBlacklistRejected) {
                java.util.UUID entityId = entity.getUUID();
                long gameTime = player.serverLevel().getGameTime();
                Long lastSent = REJECT_TOAST_LAST_SENT.get(entityId);
                if (lastSent == null || gameTime - lastSent >= REJECT_TOAST_COOLDOWN_TICKS) {
                    REJECT_TOAST_LAST_SENT.put(entityId, gameTime);
                    PacketDistributor.sendToPlayer(player,
                            new PickupToastPayload(itemId, 0, rarityColor(stack), stack.getHoverName())); // 0 = rejected marker
                }
            }
            return;
        }

        // Feature 1: smart pickup order.
        if (Config.pickupEnabled) {
            // Respect vanilla eligibility rules (pickup delay and thrower/target
            // reservation) so that freshly thrown items are NOT vacuumed back up
            // and items reserved for another player are left alone.
            if (entity.hasPickUpDelay()) {
                return;
            }
            java.util.UUID target = entity.getTarget();
            if (target != null && !target.equals(player.getUUID())) {
                return;
            }

            // Cancel the vanilla pickup and do our own.
            event.setCanPickup(TriState.FALSE);
            int remaining = PickupHandler.smartPickup(player, stack);
            int picked = stack.getCount() - remaining;
            if (picked > 0) {
                if (remaining > 0) {
                    entity.getItem().setCount(remaining);
                } else {
                    entity.discard();
                }
                PickupHandler.playPickupSound(player);
                if (Config.toastEnabled) {
                    PacketDistributor.sendToPlayer(player,
                            new PickupToastPayload(itemId, picked, rarityColor(stack), stack.getHoverName()));
                }
            }
        }
        // Feature 1 off: vanilla pickup proceeds (do nothing).
    }

    // ------------------------------------------------------------------
    // Feature 2: auto refill
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        RefillHandler.tick(event.getEntity());
    }

    @SubscribeEvent
    public static void onItemDestroyed(PlayerDestroyItemEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        // Only auto-refill tools/weapons destroyed while held in a hand.
        if (event.getHand() != null) {
            RefillHandler.onItemBroken(event.getEntity(), event.getHand(), event.getOriginal());
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ScreenStateTracker.clear(event.getEntity().getUUID());
        RefillHandler.onLogout(event.getEntity());
        REJECT_TOAST_LAST_SENT.clear();
    }

    /**
     * Computes the text color (0xRRGGBB) for an item based on its rarity,
     * exactly like the vanilla item name (white/yellow/aqua/light-purple).
     * Uses the full ItemStack so enchantments boost the rarity the same way
     * vanilla does (enchanted tool -> blue, enchanted golden apple -> purple).
     */
    private static int rarityColor(ItemStack stack) {
        ChatFormatting color = stack.getRarity().color();
        Integer rgb = color.getColor();
        return rgb == null ? 0xFFFFFF : rgb;
    }
}