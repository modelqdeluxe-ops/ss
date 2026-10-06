package net.tierrasfantasticas.tfclient.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.menu.TFMenuStyle;
import net.tierrasfantasticas.tfclient.mixin.AbstractContainerScreenAccessor;

/**
 * Fondo de los menús de oficios (/tf jobs): el marco de madera del pack Medieval Jobs encima del cofre de 5 filas,
 * con la rejilla de 5×2 justo sobre los huecos 29-33 y 38-42, y los textos en español en la cinta y el cartel.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class TFMenuBackgrounds {
    /** Esquina del cofre dentro de la textura de 256×256. */
    private static final int OFFSET_X = 40;
    private static final int OFFSET_Y = 34;
    /** Centro y ancho útil de la cinta del nombre y del cartel de arriba (coordenadas del cofre). */
    private static final int RIBBON_X = 88;
    private static final int RIBBON_Y = 62;
    private static final int RIBBON_W = 58;
    private static final int SIGN_X = 89;
    private static final int SIGN_Y = 1;
    private static final int SIGN_W = 54;

    private TFMenuBackgrounds() {}

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> screen && TFMenuStyle.parse(screen.getTitle()) != null) {
            ((AbstractContainerScreenAccessor) screen).tfclient$setInventoryLabelY(-10000);
        }
    }

    @SubscribeEvent
    public static void onBackground(ContainerScreenEvent.Render.Background event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        String[] style = TFMenuStyle.parse(screen.getTitle());
        if (style == null) return;
        GuiGraphics g = event.getGuiGraphics();
        int x = screen.getGuiLeft();
        int y = screen.getGuiTop();
        ResourceLocation texture = new ResourceLocation(TFClient.MOD_ID, "textures/gui/jobs/" + style[0] + ".png");
        g.pose().pushPose();
        g.pose().translate(0, 0, 1);
        g.blit(texture, x - OFFSET_X, y - OFFSET_Y, 0, 0, 256, 256, 256, 256);
        Font font = Minecraft.getInstance().font;
        label(g, font, style[1], x + RIBBON_X, y + RIBBON_Y, RIBBON_W, 0xFFFFFFFF, 0xFF1F5E2A);
        label(g, font, style[2], x + SIGN_X, y + SIGN_Y, SIGN_W, 0xFFF8CFAD, 0xFF37161F);
        g.pose().popPose();
    }

    /** Texto centrado en (cx, cy); si no cabe en el ancho, se encoge (nunca se sale ni se solapa con el marco). */
    private static void label(GuiGraphics g, Font font, String text, int cx, int cy, int maxWidth, int color, int shadow) {
        if (text == null || text.isEmpty()) return;
        text = text.toUpperCase(java.util.Locale.ROOT);
        int width = font.width(text);
        float scale = width > maxWidth ? (float) maxWidth / width : 1F;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1);
        int left = -width / 2;
        int top = -4;
        g.drawString(font, text, left + 1, top + 1, shadow, false);
        g.drawString(font, text, left, top, color, false);
        g.pose().popPose();
    }
}
