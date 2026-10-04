package net.tierrasfantasticas.tfclient.client;

import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.TFConfig;

/** Menú principal de TF: fondo del servidor, conexión directa, mundo local, opciones y salir. */
public class TFTitleScreen extends Screen {
    private final long openedAt = Util.getMillis();

    public TFTitleScreen() {
        super(Component.literal("Tierras Fantásticas"));
    }

    @Override
    protected void init() {
        TFClient.LOGGER.info("TF Client: menu principal listo");
        int buttonWidth = Math.min(240, this.width - 40);
        int left = (this.width - buttonWidth) / 2;
        int top = Math.min((int) (this.height * 0.64f), this.height - 84);
        int half = (buttonWidth - 4) / 2;

        String play = ("Jugar en " + TFConfig.serverName()).toUpperCase(Locale.ROOT);
        addRenderableWidget(new TFButton(left, top, buttonWidth, 24, Component.literal(play), b -> joinServer(), true));
        addRenderableWidget(new TFButton(left, top + 30, half, 20, Component.literal("Mundo local"),
                b -> this.minecraft.setScreen(new SelectWorldScreen(this)), false));
        addRenderableWidget(new TFButton(left + half + 4, top + 30, buttonWidth - half - 4, 20, Component.literal("Opciones"),
                b -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options)), false));
        addRenderableWidget(new TFButton(left, top + 54, buttonWidth, 20, Component.literal("Salir del juego"),
                b -> this.minecraft.stop(), false));
    }

    /** Conecta directamente al servidor y acepta su paquete de recursos (modelos animados) sin preguntar. */
    private void joinServer() {
        String address = TFConfig.serverAddress();
        ServerData data = new ServerData(TFConfig.serverName(), address, false);
        data.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
        ConnectScreen.startConnecting(this, this.minecraft, ServerAddress.parseString(address), data, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        float fade = Mth.clamp((now - openedAt) / 700f, 0f, 1f);
        float ui = Mth.clamp((now - openedAt - 250L) / 600f, 0f, 1f);

        g.fill(0, 0, this.width, this.height, 0xFF000000 | TFDraw.NIGHT);
        TFDraw.background(g, this.width, this.height, fade, now, mouseX, mouseY, 0f);
        // Oscurecemos arriba y abajo para leer el texto sin tapar el título del banner
        g.fillGradient(0, 0, this.width, 48, TFDraw.argb(0.7f, TFDraw.NIGHT), TFDraw.argb(0f, TFDraw.NIGHT));
        g.fillGradient(0, (int) (this.height * 0.52f), this.width, this.height, TFDraw.argb(0f, TFDraw.NIGHT), TFDraw.argb(0.88f, TFDraw.NIGHT));

        // Emblema y nombre arriba a la izquierda
        float bob = (float) Math.sin(now / 800.0) * 1.5f;
        TFDraw.logo(g, 26f, 25f + bob, 40f, ui);
        if (ui > 0.05f) {
            g.drawString(this.font, Component.literal(TFConfig.serverName().toUpperCase(Locale.ROOT)), 50, 15, TFDraw.argb(ui, TFDraw.GOLD_LIGHT), true);
            g.drawString(this.font, "TF Client", 50, 27, TFDraw.argb(ui, TFDraw.MUTED), true);
            String version = "TF Client " + TFClient.VERSION + " · Minecraft 1.20.1";
            g.drawString(this.font, version, 4, this.height - 12, TFDraw.argb(ui * 0.8f, TFDraw.MUTED), true);
            String ip = TFConfig.serverAddress();
            g.drawString(this.font, ip, this.width - this.font.width(ip) - 4, this.height - 12, TFDraw.argb(ui * 0.8f, TFDraw.MUTED), true);
        }

        for (GuiEventListener child : this.children()) {
            if (child instanceof TFButton button) button.setAlpha(ui);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
