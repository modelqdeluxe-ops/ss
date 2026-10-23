/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.tierrasfantasticas.tfclient.TFConfig;

public final class TFServer {
    private TFServer() {}

    /**
     * Botón del menú: sin AutoModpack, se pide primero (TFAutoModpack); con él, se conecta y AutoModpack descarga los
     * mods y recursos del servidor (1.3.121: ya no se enseña la lista de mods que faltan).
     */
    public static void join(Screen parent) {
        if (!TFAutoModpack.present()) Minecraft.getInstance().setScreen(new TFAutoModpack.Missing(parent));
        else connect(parent);
    }

    /** Conecta directamente al servidor y acepta su paquete de recursos (modelos animados) sin preguntar. */
    public static void connect(Screen parent) {
        String address = TFConfig.serverAddress();
        ServerData data = new ServerData(TFConfig.serverName(), address, false);
        data.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
        ConnectScreen.startConnecting(parent, Minecraft.getInstance(), ServerAddress.parseString(address), data, false);
    }
}
