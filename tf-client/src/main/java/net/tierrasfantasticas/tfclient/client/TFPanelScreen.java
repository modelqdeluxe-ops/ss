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
 * Dibuja la ventana de los oficios como en la 1.3.0: la parte de abajo de un cofre de 5 filas (sus 36 huecos tienen
 * los botones, nunca los objetos del jugador) y encima el marco del pack Medieval Jobs, con su rejilla de 5×2 sobre los
 * huecos 29-33 y 38-42. Los textos en español van en la cinta y en el cartel; si no caben, se encogen.
 */
public final class TFPanelScreen extends AbstractContainerScreen<TFPanelMenu> {
    /** Esquina del cofre dentro de la textura de 256×256 del marco. */
    private static final int ART_X = 40;
    private static final int ART_Y = 34;
    private static final ResourceLocation CHEST = new ResourceLocation("textures/gui/container/generic_54.png");

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
        // Abajo: la parte del inventario del cofre (126 = donde empieza en la textura del cofre de 6 filas)
        g.blit(CHEST, x, y + TFPanelMenu.BOTTOM_TOP, 0, 126, TFPanelMenu.WIDTH, 96);
        // Arriba, tapando la parte de los huecos del cofre: el marco del oficio
        g.blit(new ResourceLocation(TFClient.MOD_ID, "textures/gui/jobs/" + bg + ".png"), x - ART_X, y - ART_Y, 0, 0, 256, 256, 256, 256);
        label(g, font, menu.ribbon, x + 88, y + 62, 58, 0xFFFFFFFF, 0xFF1F5E2A);
        label(g, font, menu.sign, x + 89, y + 1, 54, 0xFFF8CFAD, 0xFF37161F);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Sin títulos ni «Inventario»: los textos van dibujados en el marco.
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
