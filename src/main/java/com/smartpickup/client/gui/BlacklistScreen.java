package com.smartpickup.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.smartpickup.Config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Blacklist manager for Smart Pickup.
 *
 * <p>Layout: a left/right split.
 * <ul>
 *   <li>Left panel  - a searchable grid of <b>all</b> registered items. Clicking an item
 *       toggles it in the blacklist. Already blacklisted items get a red highlight.</li>
 *   <li>Right panel - an independent list of the items that are <b>currently blacklisted</b>.
 *       Each row shows the item icon plus its name and can be clicked to remove it.</li>
 * </ul>
 * Mouse-wheel scrolling works independently for whichever panel the cursor is over.
 */
public class BlacklistScreen extends Screen {

    private static final int SLOT_SIZE = 18;   // cell size in the left item grid
    private static final int ROW_H = 20;       // row height in the right blacklist
    private static final int MARGIN = 12;
    private static final int GAP = 8;
    private static final int CONTENT_TOP = 48;
    private static final int FOOTER = 40;

    private final Screen parent;

    private EditBox searchBox;
    private int scrollLeft = 0;   // in grid rows
    private int scrollRight = 0;  // in list rows
    private String lastSearch = "";

    private final List<ItemStack> allItems = new ArrayList<>();
    private final List<ItemStack> filtered = new ArrayList<>();

    private ItemStack tooltipStack;

    public BlacklistScreen(Screen parent) {
        super(Component.translatable("screen.smartpickup.blacklist.title"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------
    // Layout helpers (shared by render + click + scroll logic)
    // ------------------------------------------------------------------

    private int leftX() { return MARGIN; }

    private int leftWidth() { return (int) ((this.width - 2 * MARGIN - GAP) * 0.60); }

    private int rightX() { return leftX() + leftWidth() + GAP; }

    private int rightWidth() { return Math.max(60, this.width - rightX() - MARGIN); }

    private int contentTop() { return CONTENT_TOP; }

    private int contentBottom() { return this.height - FOOTER; }

    private int gridCols() { return Math.max(1, (leftWidth() - 2) / SLOT_SIZE); }

    private int gridRowsVisible() { return Math.max(1, (contentBottom() - contentTop()) / SLOT_SIZE); }

    private int listRowsVisible() { return Math.max(1, (contentBottom() - contentTop()) / ROW_H); }

    // ------------------------------------------------------------------

    @Override
    protected void init() {
        allItems.clear();
        for (var item : BuiltInRegistries.ITEM) {
            allItems.add(new ItemStack(item));
        }
        allItems.sort(Comparator.comparing(s -> s.getHoverName().getString()));

        scrollLeft = 0;
        scrollRight = 0;
        lastSearch = "";

        searchBox = new EditBox(this.font, leftX() + 4, 26, Math.max(40, leftWidth() - 8), 16,
                Component.translatable("screen.smartpickup.blacklist.search"));
        searchBox.setHint(Component.translatable("screen.smartpickup.blacklist.search"));
        searchBox.setResponder(s -> {
            if (!s.equals(lastSearch)) {
                lastSearch = s;
                rebuildFiltered();
                scrollLeft = 0;
            }
        });
        addRenderableWidget(searchBox);

        rebuildFiltered();

        int cx = this.width / 2;
        addRenderableWidget(Button.builder(
                        Component.translatable("button.smartpickup.clearBlacklist"),
                        b -> clearBlacklist())
                .bounds(cx - 105, this.height - 28, 100, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.done"),
                        b -> this.minecraft.setScreen(parent))
                .bounds(cx + 5, this.height - 28, 100, 20)
                .build());
    }

    /**
     * Overridden to do nothing.
     *
     * <p>Vanilla {@link Screen#setInitialFocus()} walks the first focusable widget when the
     * last input type is a keyboard. The search box is the first focusable widget here, so on
     * open it would grab focus - which on Android immediately popped up the on-screen keyboard,
     * covering the whole UI and making it impossible to click anything. The player now focuses
     * the search box explicitly by tapping it.
     */
    @Override
    protected void setInitialFocus() {
        // Intentionally empty.
    }

    private void rebuildFiltered() {
        filtered.clear();
        String q = lastSearch == null ? "" : lastSearch.trim().toLowerCase();
        for (ItemStack stack : allItems) {
            if (q.isEmpty() || matches(stack, q)) {
                filtered.add(stack);
            }
        }
    }

    private boolean matches(ItemStack stack, String q) {
        if (stack.getHoverName().getString().toLowerCase().contains(q)) {
            return true;
        }
        return itemId(stack).contains(q);
    }

    private void clearBlacklist() {
        for (String id : new ArrayList<>(Config.blacklist)) {
            Config.removeBlacklist(id);
        }
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private boolean isBlacklisted(ItemStack stack) {
        return Config.blacklist.contains(itemId(stack));
    }

    private List<ItemStack> blacklistStacks() {
        List<ItemStack> list = new ArrayList<>();
        for (String id : Config.blacklist) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                list.add(new ItemStack(BuiltInRegistries.ITEM.get(rl)));
            }
        }
        list.sort(Comparator.comparing(s -> s.getHoverName().getString()));
        return list;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.tooltipStack = null;

        // 1) Vanilla pass: background (blur + menu overlay) followed by all registered
        //    widgets (search box + buttons). This MUST happen first, otherwise the
        //    background pass would be drawn on top of our item grid and blur it.
        super.render(gfx, mouseX, mouseY, partialTick);

        // 2) Our custom content, drawn on top so it stays crisp.
        gfx.fill(leftX() - 2, contentTop() - 2, leftX() + leftWidth() + 2, contentBottom() + 2, 0x80000000);
        gfx.fill(rightX() - 2, contentTop() - 2, rightX() + rightWidth() + 2, contentBottom() + 2, 0x80000000);

        gfx.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        gfx.drawString(this.font,
                Component.translatable("screen.smartpickup.blacklist.count", Config.blacklist.size()),
                rightX(), 30, 0xFFD070);

        renderGrid(gfx, mouseX, mouseY);
        renderBlacklist(gfx, mouseX, mouseY);

        if (this.tooltipStack != null) {
            gfx.renderTooltip(this.font, this.tooltipStack, mouseX, mouseY);
        }
    }

    private void renderGrid(GuiGraphics gfx, int mouseX, int mouseY) {
        int cols = gridCols();
        int rows = gridRowsVisible();
        int totalRows = (filtered.size() + cols - 1) / cols;
        int maxScroll = Math.max(0, totalRows - rows);
        if (scrollLeft > maxScroll) {
            scrollLeft = maxScroll;
        }

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int idx = (scrollLeft + row) * cols + col;
                if (idx >= filtered.size()) {
                    return;
                }
                ItemStack stack = filtered.get(idx);
                int x = leftX() + col * SLOT_SIZE;
                int y = contentTop() + row * SLOT_SIZE;

                if (isBlacklisted(stack)) {
                    gfx.fill(x - 1, y - 1, x + 17, y + 17, 0x80FF3030);
                }
                gfx.renderItem(stack, x, y);

                if (isHover(x, y, SLOT_SIZE, SLOT_SIZE, mouseX, mouseY)) {
                    gfx.fill(x - 1, y - 1, x + 17, y + 17, 0x80FFFFFF);
                    this.tooltipStack = stack;
                }
            }
        }
    }

    private void renderBlacklist(GuiGraphics gfx, int mouseX, int mouseY) {
        List<ItemStack> list = blacklistStacks();
        int rows = listRowsVisible();
        int maxScroll = Math.max(0, list.size() - rows);
        if (scrollRight > maxScroll) {
            scrollRight = maxScroll;
        }

        for (int i = 0; i < rows; i++) {
            int idx = scrollRight + i;
            if (idx >= list.size()) {
                break;
            }
            ItemStack stack = list.get(idx);
            int x = rightX();
            int y = contentTop() + i * ROW_H;

            if (isHover(x, y, rightWidth(), ROW_H - 2, mouseX, mouseY)) {
                gfx.fill(x - 1, y - 1, x + rightWidth(), y + ROW_H - 2, 0x40FFFFFF);
            }
            gfx.renderItem(stack, x + 1, y);

            String name = this.font.plainSubstrByWidth(
                    stack.getHoverName().getString(), Math.max(10, rightWidth() - 26));
            gfx.drawString(this.font, name, x + 20, y + 5, 0xFFE0E0E0);
        }
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Left panel: toggle item in/out of the blacklist
        if (inBounds(mouseX, mouseY, leftX(), contentTop(), leftWidth(), contentBottom() - contentTop())) {
            int cols = gridCols();
            int col = (int) ((mouseX - leftX()) / SLOT_SIZE);
            int row = (int) ((mouseY - contentTop()) / SLOT_SIZE);
            if (col >= 0 && col < cols) {
                int idx = (scrollLeft + row) * cols + col;
                if (idx >= 0 && idx < filtered.size()) {
                    String id = itemId(filtered.get(idx));
                    if (Config.blacklist.contains(id)) {
                        Config.removeBlacklist(id);
                    } else {
                        Config.addBlacklist(id);
                    }
                }
            }
            return true;
        }

        // Right panel: click a row to remove it from the blacklist
        if (inBounds(mouseX, mouseY, rightX(), contentTop(), rightWidth(), contentBottom() - contentTop())) {
            int row = (int) ((mouseY - contentTop()) / ROW_H);
            List<ItemStack> list = blacklistStacks();
            int idx = scrollRight + row;
            if (idx >= 0 && idx < list.size()) {
                Config.removeBlacklist(itemId(list.get(idx)));
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int delta = (int) Math.signum(scrollY);

        if (mouseX < rightX()) {
            int cols = gridCols();
            int totalRows = (filtered.size() + cols - 1) / cols;
            int maxScroll = Math.max(0, totalRows - gridRowsVisible());
            scrollLeft = clamp(scrollLeft - delta, 0, maxScroll);
            return true;
        } else {
            int maxScroll = Math.max(0, blacklistStacks().size() - listRowsVisible());
            scrollRight = clamp(scrollRight - delta, 0, maxScroll);
            return true;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static boolean isHover(int x, int y, int w, int h, double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static boolean inBounds(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
