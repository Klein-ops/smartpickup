package com.smartpickup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * All configuration for Smart Pickup.
 * Every feature has its own independent toggle, plus a master switch.
 * When a toggle is off, vanilla behaviour applies for that feature.
 */
@EventBusSubscriber(modid = ModConstants.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class Config {

    /** Cache of all registered item ids (keyed by registry name string) used by the blacklist UI. */
    public static Set<String> blacklist = new HashSet<>();

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ------------------------------------------------------------------
    // Master switch
    // ------------------------------------------------------------------
    private static final ModConfigSpec.BooleanValue MASTER_ENABLED = BUILDER
            .comment("Master switch for all Smart Pickup features. When false, all features are disabled and vanilla behaviour is fully restored.")
            .define("general.masterEnabled", true);

    // ------------------------------------------------------------------
    // Feature 1: Pickup prioritises the inventory
    // ------------------------------------------------------------------
    private static final ModConfigSpec.BooleanValue PICKUP_ENABLED = BUILDER
            .comment("Feature 1: When picking up items, fill stacks in the hotbar first (vanilla), then stacks in the main inventory, then empty inventory slots. Only when the inventory is full do items go into free hotbar slots.")
            .define("pickup.enabled", true);

    // ------------------------------------------------------------------
    // Feature 2: Auto refill emptied main-hand / off-hand stacks
    // ------------------------------------------------------------------
    private static final ModConfigSpec.BooleanValue REFILL_ENABLED = BUILDER
            .comment("Feature 2: When the item in the main hand or off hand is used up (count reaches 0) or breaks (durability exhausted), automatically swap in a matching stack from the inventory.")
            .define("refill.enabled", true);

    private static final ModConfigSpec.BooleanValue REFILL_ON_EMPTY = BUILDER
            .comment("Refill when the held stack count reaches 0 (e.g. arrows, food, throwables).")
            .define("refill.onEmpty", true);

    private static final ModConfigSpec.BooleanValue REFILL_ON_BREAK = BUILDER
            .comment("Refill when a tool/weapon breaks (durability reaches 0).")
            .define("refill.onBreak", true);

    private static final ModConfigSpec.BooleanValue REFILL_SKIP_CREATIVE = BUILDER
            .comment("Never auto-refill in Creative mode.")
            .define("refill.skipCreative", true);

    // ------------------------------------------------------------------
    // Feature 3: Pickup blacklist
    // ------------------------------------------------------------------
    private static final ModConfigSpec.BooleanValue BLACKLIST_ENABLED = BUILDER
            .comment("Feature 3: Items on the blacklist are not picked up at all (they stay on the ground). Manage the list from the mod's config screen (button opens an item picker UI).")
            .define("blacklist.enabled", true);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST_ITEMS = BUILDER
            .comment("Blacklisted item ids, e.g. \"minecraft:dirt\". Managed through the config UI.")
            .defineListAllowEmpty("blacklist.items", List.of("minecraft:stone"), Config::validateItemId);

    // ------------------------------------------------------------------
    // Feature 1b: hotbar-priority whitelist
    // ------------------------------------------------------------------
    private static final ModConfigSpec.BooleanValue HOTBAR_PRIORITY_ENABLED = BUILDER
            .comment("When enabled, items in the hotbar priority list are picked up into the hotbar first (vanilla behaviour), instead of the inventory-first smart order.")
            .define("pickup.hotbarPriorityEnabled", false);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> HOTBAR_PRIORITY_ITEMS = BUILDER
            .comment("Item ids that always go to the hotbar first when picked up, e.g. \"minecraft:torch\". Managed through the config UI.")
            .defineListAllowEmpty("pickup.hotbarPriorityItems", List.of(), Config::validateItemId);

    // ------------------------------------------------------------------
    // Feature 4: Pickup toast (action bar message)
    // ------------------------------------------------------------------
    private static final ModConfigSpec.BooleanValue TOAST_ENABLED = BUILDER
            .comment("Feature 4: Show a small message above the hotbar when items are picked up.")
            .define("toast.enabled", true);

    private static final ModConfigSpec.DoubleValue TOAST_DURATION = BUILDER
            .comment("How long the pickup message stays visible, in seconds (0.5 - 10).")
            .defineInRange("toast.duration", 3.0, 0.5, 10.0);

    private static final ModConfigSpec.DoubleValue TOAST_MERGE_WINDOW = BUILDER
            .comment("Within this window (seconds), consecutive pickups of the same item are merged into one message with a summed count.")
            .defineInRange("toast.mergeWindow", 1.5, 0.1, 10.0);

    private static final ModConfigSpec.BooleanValue TOAST_BLACKLIST_REJECTED = BUILDER
            .comment("Also show a toast when a blacklisted item was refused.")
            .define("toast.blacklistRejected", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    // ------------------------------------------------------------------
    // Cached runtime values
    // ------------------------------------------------------------------
    public static boolean masterEnabled;
    public static boolean pickupEnabled;
    public static boolean hotbarPriorityEnabled;
    public static boolean refillEnabled;
    public static boolean refillOnEmpty;
    public static boolean refillOnBreak;
    public static boolean refillSkipCreative;
    public static boolean blacklistEnabled;
    public static boolean toastEnabled;
    public static double toastDuration;
    public static double toastMergeWindow;
    public static boolean toastBlacklistRejected;

    /** Cache of all registered item ids that should keep vanilla hotbar-first pickup. */
    public static Set<String> hotbarPriority = new HashSet<>();

    private static boolean validateItemId(final Object obj) {
        if (!(obj instanceof String s)) {
            return false;
        }
        return BuiltInRegistries.ITEM.containsKey(ResourceLocation.tryParse(s));
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        masterEnabled = MASTER_ENABLED.get();
        pickupEnabled = PICKUP_ENABLED.get();
        hotbarPriorityEnabled = HOTBAR_PRIORITY_ENABLED.get();
        refillEnabled = REFILL_ENABLED.get();
        refillOnEmpty = REFILL_ON_EMPTY.get();
        refillOnBreak = REFILL_ON_BREAK.get();
        refillSkipCreative = REFILL_SKIP_CREATIVE.get();
        blacklistEnabled = BLACKLIST_ENABLED.get();
        toastEnabled = TOAST_ENABLED.get();
        toastDuration = TOAST_DURATION.get();
        toastMergeWindow = TOAST_MERGE_WINDOW.get();
        toastBlacklistRejected = TOAST_BLACKLIST_REJECTED.get();

        blacklist = new HashSet<>();
        for (String id : BLACKLIST_ITEMS.get()) {
            blacklist.add(id);
        }
        hotbarPriority = new HashSet<>();
        for (String id : HOTBAR_PRIORITY_ITEMS.get()) {
            hotbarPriority.add(id);
        }
    }

    /** Returns whether the given item id (resource location string) is blacklisted. */
    public static boolean isBlacklisted(String itemId) {
        return blacklistEnabled && blacklist.contains(itemId);
    }

    /** Adds an item id to the in-memory blacklist (persisted on config save). */
    public static void addBlacklist(String itemId) {
        List<String> current = new ArrayList<>(BLACKLIST_ITEMS.get());
        if (!current.contains(itemId)) {
            current.add(itemId);
            BLACKLIST_ITEMS.set(current);
            blacklist.add(itemId);
            SPEC.save();
        }
    }

    /** Removes an item id from the in-memory blacklist (persisted on config save). */
    public static void removeBlacklist(String itemId) {
        List<String> current = new ArrayList<>(BLACKLIST_ITEMS.get());
        if (current.remove(itemId)) {
            BLACKLIST_ITEMS.set(current);
            blacklist.remove(itemId);
            SPEC.save();
        }
    }

    /** Returns whether the given item id is in the hotbar-priority whitelist. */
    public static boolean isHotbarPriority(String itemId) {
        return hotbarPriorityEnabled && hotbarPriority.contains(itemId);
    }

    /** Adds an item id to the hotbar-priority whitelist (persisted on config save). */
    public static void addHotbarPriority(String itemId) {
        List<String> current = new ArrayList<>(HOTBAR_PRIORITY_ITEMS.get());
        if (!current.contains(itemId)) {
            current.add(itemId);
            HOTBAR_PRIORITY_ITEMS.set(current);
            hotbarPriority.add(itemId);
            SPEC.save();
        }
    }

    /** Removes an item id from the hotbar-priority whitelist (persisted on config save). */
    public static void removeHotbarPriority(String itemId) {
        List<String> current = new ArrayList<>(HOTBAR_PRIORITY_ITEMS.get());
        if (current.remove(itemId)) {
            HOTBAR_PRIORITY_ITEMS.set(current);
            hotbarPriority.remove(itemId);
            SPEC.save();
        }
    }

    // ------------------------------------------------------------------
    // UI helpers: used by the in-game config/blacklist screens
    // ------------------------------------------------------------------

    public static void setMasterEnabled(boolean v) { MASTER_ENABLED.set(v); masterEnabled = v; SPEC.save(); }
    public static void setPickupEnabled(boolean v) { PICKUP_ENABLED.set(v); pickupEnabled = v; SPEC.save(); }
    public static void setHotbarPriorityEnabled(boolean v) { HOTBAR_PRIORITY_ENABLED.set(v); hotbarPriorityEnabled = v; SPEC.save(); }
    public static void setRefillEnabled(boolean v) { REFILL_ENABLED.set(v); refillEnabled = v; SPEC.save(); }
    public static void setRefillOnEmpty(boolean v) { REFILL_ON_EMPTY.set(v); refillOnEmpty = v; SPEC.save(); }
    public static void setRefillOnBreak(boolean v) { REFILL_ON_BREAK.set(v); refillOnBreak = v; SPEC.save(); }
    public static void setRefillSkipCreative(boolean v) { REFILL_SKIP_CREATIVE.set(v); refillSkipCreative = v; SPEC.save(); }
    public static void setBlacklistEnabled(boolean v) { BLACKLIST_ENABLED.set(v); blacklistEnabled = v; SPEC.save(); }
    public static void setToastEnabled(boolean v) { TOAST_ENABLED.set(v); toastEnabled = v; SPEC.save(); }
    public static void setToastBlacklistRejected(boolean v) { TOAST_BLACKLIST_REJECTED.set(v); toastBlacklistRejected = v; SPEC.save(); }
}