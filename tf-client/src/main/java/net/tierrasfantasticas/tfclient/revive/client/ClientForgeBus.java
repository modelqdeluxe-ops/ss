/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.revive.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.minecraftforge.client.event.ScreenEvent.Opening;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.revive.TFRevive;

/** Revivir en el cliente (bus de Forge), como el revivemod: las teclas, no abrir inventarios noqueado y el reinicio al entrar o salir. */
@EventBusSubscriber(
    modid = TFClient.MOD_ID,
    value = {Dist.CLIENT}
)
public final class ClientForgeBus {
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent event) {
        if (event.phase == Phase.END && TFRevive.active()) {
            RevivemodClient.onClientTick(Minecraft.getInstance());
        }
    }

    @SubscribeEvent
    public static void onScreenOpening(Opening event) {
        if (RevivemodClient.LOCAL_DOWNED && event.getNewScreen() instanceof AbstractContainerScreen) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLoggingIn(LoggingIn event) {
        RevivemodClient.reset();
    }

    @SubscribeEvent
    public static void onLoggingOut(LoggingOut event) {
        RevivemodClient.reset();
    }
}
