package com.smartpickup.toast;

import com.smartpickup.network.PickupToastPayload;

/**
 * Client-side entry point for handling the pickup toast payload.
 * Referenced from {@link com.smartpickup.network.NetworkHandler}.
 */
public final class ClientToastEvents {

    private ClientToastEvents() {
    }

    /**
     * Handles a PickupToastPayload received from the server on the client thread.
     * NeoForge payload handlers already run on the main (client) thread, so it is
     * safe to touch the Minecraft instance directly.
     */
    public static void handlePickupToast(PickupToastPayload payload) {
        PickupToastManager.onPickup(payload.itemId(), payload.count(), payload.color());
    }
}