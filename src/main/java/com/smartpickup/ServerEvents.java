package com.smartpickup;

import com.smartpickup.network.PickupToastPayload;

import net.minecraft.core.registries.BuiltInRegistries;
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
            if (Config.toastEnabled && Config.toastBlacklistRejected) {
                PacketDistributor.sendToPlayer(player,
                        new PickupToastPayload(itemId, 0)); // 0 = rejected marker
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
                            new PickupToastPayload(itemId, picked));
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
    }
}