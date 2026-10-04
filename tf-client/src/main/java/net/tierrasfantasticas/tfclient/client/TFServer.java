package net.tierrasfantasticas.tfclient.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.tierrasfantasticas.tfclient.TFConfig;

public final class TFServer {
    private TFServer() {}

    /** Botón del menú: primero comprueba los mods del servidor y después conecta. */
    public static void join(Screen parent) {
        Minecraft.getInstance().setScreen(new TFServerCheckScreen(parent));
    }

    /** Conecta directamente al servidor y acepta su paquete de recursos (modelos animados) sin preguntar. */
    public static void connect(Screen parent) {
        String address = TFConfig.serverAddress();
        ServerData data = new ServerData(TFConfig.serverName(), address, false);
        data.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
        ConnectScreen.startConnecting(parent, Minecraft.getInstance(), ServerAddress.parseString(address), data, false);
    }
}
