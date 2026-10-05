package com.smartpickup.toast;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.smartpickup.Config;

/**
 * Renders the stacked pickup-toast list directly on the HUD (replaces the
 * vanilla action-bar message).
 *
 * <p>Entries stack from bottom to top above the hotbar: the newest entry is
 * rendered at the bottom at full size/opacity, older entries move upwards and
 * shrink/fade step by step (per the 4-level rule: 1st = smallest/faintest,
 * 2nd = smaller/fainter, 3rd = slightly smaller/fainter, 4th = unchanged).
 * Every entry fades out once its lifetime is over.
 */
public final class PickupToastRenderer {

    /** Max entries kept at once; entry at index 0 is the newest. */
    private static final int MAX_ENTRIES = 4;

    /**
     * Scale for the 4 levels: [0]=newest (bottom, unchanged), then 3
     * progressively smaller. Deliberately gentle: entries shrink to reduce
     * their presence, but must remain clearly readable.
     */
    private static final float[] LEVEL_SCALE = {1.0F, 0.92F, 0.84F, 0.76F};

    /**
     * Alpha for the 4 levels: [0]=newest (full), then 3 progressively fainter.
     * The faintest level is still well readable (150/255).
     */
    private static final int[] LEVEL_ALPHA = {255, 220, 185, 150};

    /** Fraction of the configured duration spent fully visible before fading. */
    private static final float HOLD_FRACTION = 0.6F;

    /**
     * Vertical gap between two stacked entries, in scaled pixels. The stack
     * grows UPWARD from the action-bar position (screenHeight - 68), which is
     * where the vanilla message used to appear.
     */
    private static final int LINE_GAP = 12;

    /**
     * Y offset of the action-bar baseline (same as the vanilla overlay
     * message: guiHeight() - 68). The newest entry sits here; older entries
     * move further up.
     */
    private static final int BASE_Y_FROM_BOTTOM = 68;

    private static final class Entry {
        final String key;
        final Component text;
        final long addedTick;
        final int level; // 0 = newest ... 3 = oldest
        final int color; // RGB text color matching the item's rarity

        Entry(String key, Component text, long addedTick, int level, int color) {
            this.key = key;
            this.text = text;
            this.addedTick = addedTick;
            this.level = level;
            this.color = color;
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private PickupToastRenderer() {
    }

    /**
     * Adds a new toast entry. Call from the client thread when a pickup payload
     * arrives.
     *
     * @param key  stable identity of the entry, e.g. the item id; used to update
     *             an existing entry instead of stacking a duplicate
     * @param text rendered text
     * @param color RGB text color matching the item's rarity
     */
    public static void push(String key, Component text, int color) {
        Minecraft mc = Minecraft.getInstance();
        long nowTick = mc.level == null ? 0 : mc.level.getGameTime();

        List<Entry> next = new ArrayList<>(MAX_ENTRIES);
        // Newest entry goes first (level 0 = full size/opacity).
        next.add(new Entry(key, text, nowTick, 0, color));
        // Shift previous entries up one level; keep at most MAX_ENTRIES.
        for (int i = 0; i < ENTRIES.size() && next.size() < MAX_ENTRIES; i++) {
            Entry e = ENTRIES.get(i);
            next.add(new Entry(e.key, e.text, e.addedTick, next.size(), e.color));
        }
        ENTRIES.clear();
        ENTRIES.addAll(next);
    }

    /**
     * If the newest entry matches {@code key}, refreshes its text and lifetime
     * (used to merge consecutive pickups of the same item). Otherwise behaves
     * like {@link #push(String, Component, int)}.
     */
    public static void updateLatest(String key, Component text, int color) {
        Minecraft mc = Minecraft.getInstance();
        long nowTick = mc.level == null ? 0 : mc.level.getGameTime();
        if (!ENTRIES.isEmpty() && ENTRIES.get(0).key.equals(key)) {
            ENTRIES.set(0, new Entry(key, text, nowTick, 0, color));
        } else {
            push(key, text, color);
        }
    }

    /**
     * Removes all entries (e.g. on respawn / level change).
     */
    public static void clear() {
        ENTRIES.clear();
    }

    /**
     * HUD layer render callback (registered via RegisterGuiLayersEvent).
     */
    public static void render(GuiGraphics gui, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (!Config.toastEnabled) {
            return;
        }
        if (ENTRIES.isEmpty()) {
            return;
        }

        long nowTick = mc.level.getGameTime();
        // Total lifetime comes from the configured duration; fade starts after
        // HOLD_FRACTION of it, and finishes at the end.
        float totalSeconds = (float) Config.toastDuration;
        float holdSeconds = totalSeconds * HOLD_FRACTION;
        ENTRIES.removeIf(e -> (nowTick - e.addedTick) / 20.0F > totalSeconds);

        if (ENTRIES.isEmpty()) {
            return;
        }

        Font font = mc.font;
        int screenWidth = gui.guiWidth();
        int screenHeight = gui.guiHeight();

        // Baseline at the vanilla action-bar height; the newest entry sits
        // here, older entries stack upward (smaller y).
        int baseY = screenHeight - BASE_Y_FROM_BOTTOM;

        // Draw oldest first (top), newest last (bottom), so newest overlaps on top.
        // Entry at index 0 is newest -> bottom. Reverse iteration for paint order.
        for (int i = ENTRIES.size() - 1; i >= 0; i--) {
            Entry e = ENTRIES.get(i);
            float age = (nowTick - e.addedTick) / 20.0F;

            float alpha = 1.0F;
            if (age > holdSeconds) {
                float fadeProgress = (age - holdSeconds) / Math.max(0.05F, totalSeconds - holdSeconds);
                alpha = 1.0F - Math.min(1.0F, fadeProgress);
            }
            if (alpha <= 0.02F) {
                continue;
            }

            // Base scale/alpha from the level (0=newest ... 3=oldest).
            int level = e.level;
            float scale = LEVEL_SCALE[Math.min(level, LEVEL_SCALE.length - 1)];
            int baseAlpha = LEVEL_ALPHA[Math.min(level, LEVEL_ALPHA.length - 1)];
            int colorAlpha = (int) (baseAlpha * alpha);

            // Offset upward from the baseline: older entries are higher up.
            // Multiply by the entry's own scale so smaller text stays closer
            // to its predecessor and the stack stays compact.
            float yOffset = i * LINE_GAP * scale;

            // Draw with the level-based scale and the item's rarity color.
            drawScaled(gui, font, e.text, scale, colorAlpha, e.color, baseY - yOffset);
        }
    }

    private static void drawScaled(GuiGraphics gui, Font font, Component text,
                                   float scale, int alpha, int rgb, float centerY) {
        int screenWidth = gui.guiWidth();
        float textWidth = font.width(text) * scale;
        float x = (screenWidth - textWidth) / 2.0F;
        float y = centerY - font.lineHeight * scale / 2.0F;

        // Combine the alpha (fade + level dimming) with the rarity color (RGB).
        int color = (alpha << 24) | (rgb & 0xFFFFFF);
        gui.pose().pushPose();
        gui.pose().translate(x, y, 0.0F);
        gui.pose().scale(scale, scale, 1.0F);
        // White text with drop shadow; alpha encoded in the ARGB color.
        gui.drawString(font, text, 0, 0, color, true);
        gui.pose().popPose();
    }
}
