package net.tierrasfantasticas.tfclient.client;

import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.menu.TFPanelMenu;

/**
 * Dibuja la ventana de los oficios: el marco del pack Medieval Jobs (con la rejilla de 5×2 justo debajo de los huecos)
 * y la barra de botones aparte, debajo. Los textos en español van en la cinta y en el cartel; si no caben, se encogen.
 */
public final class TFPanelScreen extends AbstractContainerScreen<TFPanelMenu> {
    /** Esquina de la ventana dentro de la textura de 256×256 del marco. */
    private static final int ART_X = 33;
    private static final int ART_Y = 10;
    private static final ResourceLocation NAV = new ResourceLocation(TFClient.MOD_ID, "textures/gui/jobs/nav.png");

    public TFPanelScreen(TFPanelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = TFPanelMenu.WIDTH;
        imageHeight = TFPanelMenu.HEIGHT;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        String bg = menu.background.matches("[a-z0-9_]{1,40}") ? menu.background : "farmer";
        g.blit(new ResourceLocation(TFClient.MOD_ID, "textures/gui/jobs/" + bg + ".png"), x - ART_X, y - ART_Y, 0, 0, 256, 256, 256, 256);
        g.blit(NAV, x, y + TFPanelMenu.NAV_Y, 0, 0, TFPanelMenu.WIDTH, 30, TFPanelMenu.WIDTH, 30);
        label(g, font, menu.ribbon, x + 95, y + 86, 58, 0xFFFFFFFF, 0xFF1F5E2A);
        label(g, font, menu.sign, x + 96, y + 25, 54, 0xFFF8CFAD, 0xFF37161F);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Sin títulos: los textos van dibujados en el marco.
    }

    /** Texto centrado en (cx, cy); si no cabe en el ancho, se encoge (nunca se sale del marco). */
    private static void label(GuiGraphics g, Font font, String text, int cx, int cy, int maxWidth, int color, int shadow) {
        if (text == null || text.isEmpty()) return;
        text = text.toUpperCase(Locale.ROOT);
        int width = font.width(text);
        float scale = width > maxWidth ? (float) maxWidth / width : 1F;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1);
        int left = -width / 2;
        g.drawString(font, text, left + 1, -3, shadow, false);
        g.drawString(font, text, left, -4, color, false);
        g.pose().popPose();
    }
}
