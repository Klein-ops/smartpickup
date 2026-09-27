package com.smartpickup.network;

import com.smartpickup.ModConstants;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers the mod's network payloads with NeoForge's payload system.
 */
@EventBusSubscriber(modid = ModConstants.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class NetworkHandler {

    private NetworkHandler() {
    }

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ModConstants.CHANNEL_VERSION);
        registrar.playToClient(
                PickupToastPayload.TYPE,
                PickupToastPayload.STREAM_CODEC,
                (payload, context) -> com.smartpickup.toast.ClientToastEvents.handlePickupToast(payload)
        );
        registrar.playToServer(
                ScreenStatePayload.TYPE,
                ScreenStatePayload.STREAM_CODEC,
                (payload, context) -> com.smartpickup.ScreenStateTracker.set(context.player().getUUID(), payload.open())
        );
    }
}