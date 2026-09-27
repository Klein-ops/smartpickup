package com.smartpickup.client;

import com.smartpickup.ModConstants;
import com.smartpickup.client.gui.SmartPickupConfigScreen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Registers the config screen factory so the NeoForge mod list
 * shows a "Config" button for Smart Pickup.
 */
@EventBusSubscriber(modid = ModConstants.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        net.neoforged.fml.ModList.get().getModContainerById(ModConstants.MODID)
                .ifPresent(container -> container.registerExtensionPoint(
                        IConfigScreenFactory.class,
                        (IConfigScreenFactory) (modContainer, parentScreen) ->
                                new SmartPickupConfigScreen(parentScreen)
                ));
    }
}