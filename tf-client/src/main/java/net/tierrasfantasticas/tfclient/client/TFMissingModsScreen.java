package net.tierrasfantasticas.tfclient.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.tierrasfantasticas.tfclient.TFConfig;

/** Lista de los mods que pide el servidor y no están instalados, con opción de volver o conectar igualmente. */
public class TFMissingModsScreen extends Screen {
    private static final int LINE = 11;
    private final Screen parent;
    private final List<TFServerCheck.MissingMod> missing;
    private double scroll;

    public TFMissingModsScreen(Screen parent, List<TFServerCheck.MissingMod> missing) {
        super(Component.literal("Faltan mods"));
        this.parent = parent;
        this.missing = missing;
    }

    private int listTop() {
        return 58;
    }

    private int listBottom() {
        return this.height - 40;
    }

    @Override
    protected void init() {
        int y = this.height - 30;
        addRenderableWidget(new TFMenuButton(this.width / 2 - 102, y, 98, 20, TFMenuButton.label("Volver"),
                button -> Minecraft.getInstance().setScreen(parent), TFMenuButton.Style.SECONDARY));
        addRenderableWidget(new TFMenuButton(this.width / 2 + 4, y, 98, 20, TFMenuButton.label("Entrar igual"),
                button -> TFServer.connect(parent), TFMenuButton.Style.PRIMARY));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int visible = (listBottom() - listTop()) / LINE;
        double max = Math.max(0, missing.size() - visible);
        scroll = Mth.clamp(scroll - delta * 3, 0, max);
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(this.font, TFMenuButton.label("Te faltan mods para entrar"), this.width / 2, 14, 0xFFD667);
        g.drawCenteredString(this.font, Component.literal(TFConfig.serverName() + " usa " + missing.size()
                + " mods que no tienes instalados. Instala el modpack del servidor."), this.width / 2, 30, 0xE0E0E0);
        g.drawCenteredString(this.font, Component.literal("(rueda del ratón para ver la lista)"), this.width / 2, 42, 0x9AA0AA);

        int top = listTop();
        int bottom = listBottom();
        int left = this.width / 2 - 150;
        int right = this.width / 2 + 150;
        g.fill(left - 4, top - 4, right + 4, bottom + 2, 0xB0000000);
        g.enableScissor(left - 4, top - 4, right + 4, bottom + 2);
        int first = (int) scroll;
        for (int i = first; i < missing.size(); i++) {
            int y = top + (i - first) * LINE;
            if (y > bottom - LINE) break;
            TFServerCheck.MissingMod mod = missing.get(i);
            g.drawString(this.font, "• " + mod.id(), left, y, 0xFFFFFF);
            if (!mod.version().isEmpty()) {
                String version = mod.version();
                g.drawString(this.font, version, right - this.font.width(version), y, 0x9AA0AA);
            }
        }
        g.disableScissor();
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
