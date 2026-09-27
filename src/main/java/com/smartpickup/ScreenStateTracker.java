package com.smartpickup;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side record of which players currently have a GUI open on their client.
 *
 * <p>Updated from {@link com.smartpickup.network.ScreenStatePayload} (sent by the client
 * whenever its screen opens or closes). Auto-refill is suppressed while a player has any
 * screen open.
 */
public final class ScreenStateTracker {

    private static final Map<UUID, Boolean> OPEN = new ConcurrentHashMap<>();

    private ScreenStateTracker() {
    }

    /** Records the new screen state for a player. */
    public static void set(UUID playerId, boolean open) {
        if (playerId == null) {
            return;
        }
        if (open) {
            OPEN.put(playerId, Boolean.TRUE);
        } else {
            OPEN.remove(playerId);
        }
    }

    /** Returns whether the player currently has a GUI open. Unknown players count as "closed". */
    public static boolean isOpen(UUID playerId) {
        return playerId != null && OPEN.containsKey(playerId);
    }

    /** Clears the state, e.g. when a player logs out. */
    public static void clear(UUID playerId) {
        if (playerId != null) {
            OPEN.remove(playerId);
        }
    }
}