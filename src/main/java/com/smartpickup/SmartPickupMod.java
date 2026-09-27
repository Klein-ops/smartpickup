package com.smartpickup;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * Smart Pickup - intelligent item pickup.
 *
 * <p>Features (each independently toggleable, plus a master switch):
 * <ol>
 *   <li>Picked-up items fill the main inventory before free hotbar slots.</li>
 *   <li>Auto-refill empty or broken held stacks from the inventory.</li>
 *   <li>Configurable pickup blacklist with an item-picker UI.</li>
 *   <li>Small action-bar toast when items are picked up.</li>
 * </ol>
 */
@Mod(ModConstants.MODID)
public final class SmartPickupMod {

    private static final Logger LOGGER = LogUtils.getLogger();

    public SmartPickupMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register config (COMMON: loaded on both sides; server values win in multiplayer).
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // Server-side feature logic.
        ServerEvents.register();

        LOGGER.info("Smart Pickup loaded!");
    }
}