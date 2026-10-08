package net.tierrasfantasticas.tfclient.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.TFConfig;

/** "Comprobando el servidor…": mira qué mods pide el servidor y decide si conectar o avisar. */
public class TFServerCheckScreen extends Screen {
    private final Screen parent;
    private volatile boolean cancelled;

    public TFServerCheckScreen(Screen parent) {
        super(Component.literal("Comprobando el servidor"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(new TFMenuButton(this.width / 2 - 49, this.height / 2 + 24, 98, 20, TFMenuButton.label("Cancelar"),
                button -> cancel(), false));
        Thread thread = new Thread(this::runCheck, "TF Client server check");
        thread.setDaemon(true);
        thread.start();
    }

    private void cancel() {
        cancelled = true;
        Minecraft.getInstance().setScreen(parent);
    }

    private void runCheck() {
        TFServerCheck.Result result = TFServerCheck.check(TFConfig.serverAddress());
        Minecraft.getInstance().execute(() -> {
            if (cancelled || Minecraft.getInstance().screen != this) return;
            if (!result.reachable()) {
                // Si el ping falla, se intenta conectar igualmente: el juego mostrará el error real.
                TFClient.LOGGER.warn("TF Client: no se pudo comprobar el servidor: {}", result.error());
                TFServer.connect(parent);
            } else if (!result.missing().isEmpty()) {
                Minecraft.getInstance().setScreen(new TFMissingModsScreen(parent, result.missing()));
            } else {
                TFServer.connect(parent);
            }
        });
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int dots = (int) ((System.currentTimeMillis() / 400L) % 4L);
        g.drawCenteredString(this.font, TFMenuButton.label("Comprobando " + TFConfig.serverName() + ".".repeat(dots)),
                this.width / 2, this.height / 2 - 8, 0xFFF3DC);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        cancel();
    }
}
