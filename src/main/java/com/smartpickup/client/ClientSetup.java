package com.smartpickup.client;

import com.smartpickup.ModConstants;
import com.smartpickup.client.gui.SmartPickupConfigScreen;
import com.smartpickup.toast.PickupToastRenderer;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Registers the config screen factory so the NeoForge mod list
 * shows a "Config" button for Smart Pickup, and registers the
 * custom stacked pickup-toast HUD layer.
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

    /**
     * Registers the stacked pickup-toast layer just above the hotbar
     * (after the overlay message layer, so it paints on top of it).
     */
    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.OVERLAY_MESSAGE,
                ResourceLocation.fromNamespaceAndPath(ModConstants.MODID, "pickup_toasts"),
                PickupToastRenderer::render
        );
    }
}