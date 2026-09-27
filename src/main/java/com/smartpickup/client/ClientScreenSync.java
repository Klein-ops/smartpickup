package com.smartpickup.client;

import com.smartpickup.ModConstants;
import com.smartpickup.network.ScreenStatePayload;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side: tells the server whether a GUI screen is currently open, so that
 * auto-refill only runs while nothing is open.
 *
 * <p>The state is only sent to the server when it actually changes (open -> closed or
 * closed -> open), so this costs nothing in steady state.
 */
@EventBusSubscriber(modid = ModConstants.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ClientScreenSync {

    private static boolean lastOpen = false;

    private ClientScreenSync() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            lastOpen = false;
            return;
        }

        boolean open = mc.screen != null;
        if (open != lastOpen) {
            lastOpen = open;
            PacketDistributor.sendToServer(new ScreenStatePayload(open));
        }
    }
}