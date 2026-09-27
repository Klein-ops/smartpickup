package com.smartpickup.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.smartpickup.Config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The main config screen for Smart Pickup, accessible from the mod list.
 *
 * <p>Options are grouped into categories (General / Pickup / Refill / Lists /
 * Toast). Each category is drawn as its own panel with a coloured header, and
 * rows inside a category use a comfortable spacing. The button itself only
 * shows the short state text (On / Off); the long feature name is drawn as a
 * separate label on the left. If the content does not fit the screen, the
 * whole option list can be scrolled with the mouse wheel.
 */
public class SmartPickupConfigScreen extends Screen {

    private static final int ROW_H_MIN = 20;   // must be >= button height (20) so rows never overlap
    private static final int ROW_H_MAX = 24;
    private static final int HEADER_H = 14;
    private static final int CAT_GAP = 10;     // vertical gap between categories
    private static final int TOP_MARGIN = 30;
    private static final int BOTTOM_MARGIN = 46;
    private static final int SCROLL_STEP = 12;          // pixels scrolled per mouse-wheel notch
    private static final int SCROLL_BAR_W = 8;          // scroll-bar width (wide enough to grab easily)
    private static final int SCROLL_BAR_GAP = 4;        // gap between panel right edge and the bar
    private static final int SCROLL_BAR_MIN_THUMB = 24; // minimum grab-handle height

    private final Screen parent;

    private int panelLeft;
    private int panelWidth;
    private int rowH = ROW_H_MAX;
    private int maxScroll = 0;
    private int scrollOffset = 0;
    private int viewTop;          // top of the scrollable viewport
    private int viewBottom;       // bottom of the scrollable viewport
    private boolean draggingBar;  // true while the user drags the scroll-bar thumb

    private final List<Category> categories = new ArrayList<>();

    private static final class Row {
        final String labelKey;                // null for full-width action rows
        final boolean fullWidth;              // action rows (manage-list buttons)
        final BooleanSupplier getter;         // toggle rows only
        final Consumer<Boolean> setter;       // toggle rows only
        final Button.OnPress onPress;         // action rows only
        Button widget;
        int y;

        Row(String labelKey, boolean fullWidth, BooleanSupplier getter, Consumer<Boolean> setter, Button.OnPress onPress) {
            this.labelKey = labelKey;
            this.fullWidth = fullWidth;
            this.getter = getter;
            this.setter = setter;
            this.onPress = onPress;
        }
    }

    private static final class Category {
        final String titleKey;
        final List<Row> rows = new ArrayList<>();
        int y;

        Category(String titleKey) {
            this.titleKey = titleKey;
        }
    }

    public SmartPickupConfigScreen(Screen parent) {
        super(Component.translatable("screen.smartpickup.config.title"));
        this.parent = parent;
    }

    private static Component stateText(boolean on) {
        return Component.translatable(on ? "gui.smartpickup.on" : "gui.smartpickup.off");
    }

    private Button toggle(int x, int y, int w, BooleanSupplier getter, Consumer<Boolean> setter) {
        return Button.builder(stateText(getter.getAsBoolean()), btn -> {
            boolean newVal = !getter.getAsBoolean();
            setter.accept(newVal);
            btn.setMessage(stateText(newVal));
        }).bounds(x, y, w, 20).build();
    }

    private Category addCategory(String titleKey) {
        Category cat = new Category(titleKey);
        categories.add(cat);
        return cat;
    }

    private void addToggleRow(Category cat, String labelKey, BooleanSupplier getter, Consumer<Boolean> setter) {
        cat.rows.add(new Row(labelKey, false, getter, setter, null));
    }

    private void addActionRow(Category cat, String labelKey, Button.OnPress onPress) {
        cat.rows.add(new Row(labelKey, true, null, null, onPress));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        this.panelWidth = Math.min(320, Math.max(200, this.width - 40));
        this.panelLeft = cx - this.panelWidth / 2;
        this.scrollOffset = 0;

        categories.clear();

        // --- Build the option groups ---
        Category general = addCategory("category.smartpickup.general");
        addToggleRow(general, "option.smartpickup.master", () -> Config.masterEnabled, Config::setMasterEnabled);

        Category pickup = addCategory("category.smartpickup.pickup");
        addToggleRow(pickup, "option.smartpickup.pickup", () -> Config.pickupEnabled, Config::setPickupEnabled);
        addToggleRow(pickup, "option.smartpickup.hotbarPriority", () -> Config.hotbarPriorityEnabled, Config::setHotbarPriorityEnabled);
        addActionRow(pickup, "button.smartpickup.manageHotbarPriority",
                btn -> this.minecraft.setScreen(new HotbarPriorityScreen(this)));

        Category refill = addCategory("category.smartpickup.refill");
        addToggleRow(refill, "option.smartpickup.refill", () -> Config.refillEnabled, Config::setRefillEnabled);
        addToggleRow(refill, "option.smartpickup.refill.onEmpty", () -> Config.refillOnEmpty, Config::setRefillOnEmpty);
        addToggleRow(refill, "option.smartpickup.refill.onBreak", () -> Config.refillOnBreak, Config::setRefillOnBreak);
        addToggleRow(refill, "option.smartpickup.refill.skipCreative", () -> Config.refillSkipCreative, Config::setRefillSkipCreative);

        Category lists = addCategory("category.smartpickup.lists");
        addToggleRow(lists, "option.smartpickup.blacklist", () -> Config.blacklistEnabled, Config::setBlacklistEnabled);
        addActionRow(lists, "button.smartpickup.manageBlacklist",
                btn -> this.minecraft.setScreen(new BlacklistScreen(this)));

        Category toast = addCategory("category.smartpickup.toast");
        addToggleRow(toast, "option.smartpickup.toast", () -> Config.toastEnabled, Config::setToastEnabled);
        addToggleRow(toast, "option.smartpickup.toast.blacklistRejected", () -> Config.toastBlacklistRejected, Config::setToastBlacklistRejected);

        // --- Compute a comfortable row height that fits if possible (20..24 px) ---
        int totalRows = 0;
        int fixedH = 0;
        for (Category cat : categories) {
            totalRows += cat.rows.size();
            fixedH += HEADER_H;
        }
        fixedH += (categories.size() - 1) * CAT_GAP;
        int avail = this.height - TOP_MARGIN - BOTTOM_MARGIN;
        this.rowH = Math.max(ROW_H_MIN, Math.min(ROW_H_MAX, (avail - fixedH) / Math.max(1, totalRows)));

        // --- Lay out categories top-down ---
        int y = TOP_MARGIN;
        int btnX = panelLeft + panelWidth - 56;
        for (Category cat : categories) {
            cat.y = y;
            y += HEADER_H;
            for (Row row : cat.rows) {
                row.y = y;
                y += rowH;
                if (row.fullWidth) {
                    row.widget = Button.builder(Component.translatable(row.labelKey), row.onPress)
                            .bounds(panelLeft, row.y, panelWidth, 20)
                            .build();
                } else {
                    row.widget = toggle(btnX, row.y, 50, row.getter, row.setter);
                }
                addRenderableWidget(row.widget);
            }
            y += CAT_GAP;
        }

        // Content may overflow on small screens -> enable scrolling.
        int contentEnd = y - CAT_GAP;
        this.viewTop = TOP_MARGIN;
        this.viewBottom = this.height - BOTTOM_MARGIN;
        this.maxScroll = Math.max(0, contentEnd - viewTop - (viewBottom - viewTop));
        this.draggingBar = false;
        if (this.maxScroll > 0) {
            this.scrollOffset = 0;
        }

        // Done button (fixed, not scrolled).
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.done"),
                        btn -> this.minecraft.setScreen(parent))
                .bounds(cx - 50, this.height - 28, 100, 20)
                .build());

        updateWidgetPositions();
    }

    private void updateWidgetPositions() {
        for (Category cat : categories) {
            for (Row row : cat.rows) {
                if (row.widget != null) {
                    row.widget.setY(row.y - scrollOffset);
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx, mouseX, mouseY, partialTick);
        super.render(gfx, mouseX, mouseY, partialTick);

        gfx.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        for (Category cat : categories) {
            int catY = cat.y - scrollOffset;
            int catH = HEADER_H + cat.rows.size() * rowH;
            gfx.fill(panelLeft - 2, catY - 2, panelLeft + panelWidth + 2, catY + catH + 2, 0x60000000);
            gfx.drawString(this.font, Component.translatable(cat.titleKey), panelLeft + 2, catY + 1, 0xFFFFA0);
            for (Row row : cat.rows) {
                if (!row.fullWidth) {
                    gfx.drawString(this.font, Component.translatable(row.labelKey),
                            panelLeft + 2, row.y - scrollOffset + 6, 0xE0E0E0);
                }
            }
        }

        if (maxScroll > 0) {
            // --- Scroll bar (track + thumb) on the right of the panel ---
            int barX = panelLeft + panelWidth + SCROLL_BAR_GAP;
            int barTop = viewTop;
            int barH = viewBottom - viewTop;
            gfx.fill(barX, barTop, barX + SCROLL_BAR_W, barTop + barH, 0x50000000); // track

            int viewH = Math.max(1, viewBottom - viewTop);
            int thumbH = Math.max(SCROLL_BAR_MIN_THUMB, (int) ((long) viewH * viewH / Math.max(1, viewH + maxScroll)));
            int thumbMaxY = barH - thumbH;
            int thumbY = barTop + (thumbMaxY <= 0 ? 0 : (int) ((long) scrollOffset * thumbMaxY / maxScroll));
            int thumbColor = isHovered(barX, thumbY, SCROLL_BAR_W, thumbH, mouseX, mouseY) ? 0xFFC0C0C0 : 0xFF909090;
            gfx.fill(barX, thumbY, barX + SCROLL_BAR_W, thumbY + thumbH, thumbColor);

            gfx.drawCenteredString(this.font, Component.translatable("gui.smartpickup.scrollHint"),
                    this.width / 2, this.height - 30, 0x808080);
        }
    }

    private boolean isHovered(int x, int y, int w, int h, int mx, int my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int delta = (int) Math.signum(scrollY);
        int newScroll = clamp(scrollOffset - delta * SCROLL_STEP, 0, maxScroll);
        if (newScroll != scrollOffset) {
            scrollOffset = newScroll;
            updateWidgetPositions();
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (maxScroll > 0 && button == 0) {
            int barX = panelLeft + panelWidth + SCROLL_BAR_GAP;
            int barTop = viewTop;
            int barH = viewBottom - viewTop;
            if (mouseX >= barX && mouseX < barX + SCROLL_BAR_W && mouseY >= barTop && mouseY < barTop + barH) {
                int thumbY = thumbTop();
                if (mouseY >= thumbY && mouseY < thumbY + thumbHeight()) {
                    draggingBar = true; // start dragging the thumb
                } else {
                    // Click on the track: jump towards that position.
                    int target = (int) ((mouseY - barTop - thumbHeight() / 2.0) / Math.max(1, barH - thumbHeight()) * maxScroll);
                    scrollTo(clamp(target, 0, maxScroll));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingBar) {
            draggingBar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingBar && button == 0 && maxScroll > 0) {
            int barTop = viewTop;
            int barH = viewBottom - viewTop;
            int thumbH = thumbHeight();
            int track = Math.max(1, barH - thumbH);
            int target = (int) ((mouseY - barTop - thumbH / 2.0) / track * maxScroll);
            scrollTo(clamp(target, 0, maxScroll));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void scrollTo(int target) {
        if (target != scrollOffset) {
            scrollOffset = target;
            updateWidgetPositions();
        }
    }

    /** Top of the visible scroll-bar thumb (only valid when maxScroll > 0). */
    private int thumbTop() {
        int barTop = viewTop;
        int barH = viewBottom - viewTop;
        int thumbMaxY = Math.max(0, barH - thumbHeight());
        return barTop + (thumbMaxY == 0 ? 0 : (int) ((long) scrollOffset * thumbMaxY / Math.max(1, maxScroll)));
    }

    /** Height of the visible scroll-bar thumb (only valid when maxScroll > 0). */
    private int thumbHeight() {
        int viewH = Math.max(1, viewBottom - viewTop);
        return Math.max(SCROLL_BAR_MIN_THUMB, (int) ((long) viewH * viewH / Math.max(1, viewH + maxScroll)));
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}