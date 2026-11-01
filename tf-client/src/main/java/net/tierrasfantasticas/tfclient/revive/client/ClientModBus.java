/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.revive.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.revive.TFRevive;

/**
 * Revivir en el cliente (bus del mod), como el revivemod: lo que se ve en pantalla al estar noqueado, las dos teclas
 * (Rendirse / Morir y Auto-revivir, en Opciones › Controles, «Revive Mod») y su pantalla de configuración, que ahora es
 * la del TF Client (Mods › TF Client › Configuración). Si el revivemod sigue instalado, no se registra nada de esto.
 */
@EventBusSubscriber(
    modid = TFClient.MOD_ID,
    bus = Bus.MOD,
    value = {Dist.CLIENT}
)
public final class ClientModBus {
    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        if (!TFRevive.active()) return;
        event.registerAboveAll("revive_hud", (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> RevivemodClient.onHud(guiGraphics, screenWidth));
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        if (!TFRevive.active()) return;
        event.register(RevivemodKeybinds.SURRENDER);
        event.register(RevivemodKeybinds.SELF_REVIVE);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (!TFRevive.active()) return;
        ModList.get().getModContainerById(TFClient.MOD_ID).ifPresent(c -> c.registerExtensionPoint(ConfigScreenFactory.class,
                () -> new ConfigScreenFactory((mc, parent) -> new ReviveConfigScreen(parent))));
    }
}
