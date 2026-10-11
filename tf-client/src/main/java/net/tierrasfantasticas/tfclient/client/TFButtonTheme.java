package net.tierrasfantasticas.tfclient.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;

/**
 * Tema de TF para los botones fuera de una partida (menú principal, mundos, opciones, conexión...): placa azul noche
 * con doble filete dorado y letra Cinzel. Dentro de una partida no se toca nada, para que cada jugador vea sus
 * paquetes de recursos.
 */
public final class TFButtonTheme {
    /** Ancho de cada punta en píxeles de interfaz (ver tools/gen_buttons.py). */
    private static final int CAP = 16;
    private static final int TEXELS_PER_PIXEL = 4;

    private TFButtonTheme() {}

    /** true si este botón debe llevar el tema de TF ahora mismo. */
    public static boolean themed(AbstractWidget widget) {
        if (Minecraft.getInstance().level != null) return false;
        if (widget.getWidth() < 40 || widget.getHeight() < 14) return false; // botones de icono: se quedan como están
        return !(widget instanceof Checkbox) && !(widget instanceof ImageButton);
    }

    /** Botón normal o de alternar opciones. Devuelve false si no se pudo dibujar (se usa el de Minecraft). */
    public static boolean renderButton(AbstractButton button, GuiGraphics g) {
        if (!themed(button) || !TFTextures.BUTTON_SLICE.ready()) return false;
        boolean hot = button.active && button.isHoveredOrFocused();
        int state = !button.active ? 2 : hot ? 1 : 0;
        drawPlate(g, TFTextures.BUTTON_SLICE, button.getX(), button.getY(), button.getWidth(), button.getHeight(), state, 1f);
        int color = !button.active ? 0xA0A0A0 : hot ? 0xFFD667 : 0xF5EAD0;
        drawLabel(g, button.getMessage(), button.getX(), button.getY(), button.getWidth(), button.getHeight(), color, 1f, true);
        return true;
    }

    /** Deslizador (volumen, FOV...): placa de TF, tirador dorado y texto. */
    public static boolean renderSlider(AbstractSliderButton slider, double value, GuiGraphics g) {
        if (!themed(slider) || !TFTextures.BUTTON_SLICE.ready()) return false;
        boolean hot = slider.active && slider.isHoveredOrFocused();
        int x = slider.getX();
        int y = slider.getY();
        int w = slider.getWidth();
        int h = slider.getHeight();
        drawPlate(g, TFTextures.BUTTON_SLICE, x, y, w, h, slider.active ? 0 : 2, 1f);
        int handleX = x + (int) (Mth.clamp(value, 0.0, 1.0) * (w - 8));
        g.fill(handleX, y + 1, handleX + 8, y + h - 1, 0xFF1A1206);
        g.fill(handleX + 1, y + 2, handleX + 7, y + h - 2, hot ? 0xFFFFD667 : 0xFFE2B04A);
        g.fill(handleX + 1, y + 2, handleX + 7, y + 3, 0xFFFFF3C4);
        g.fill(handleX + 1, y + h - 3, handleX + 7, y + h - 2, 0xFF9A6A1C);
        int color = !slider.active ? 0xA0A0A0 : hot ? 0xFFD667 : 0xF5EAD0;
        drawLabel(g, slider.getMessage(), x, y, w, h, color, 1f, true);
        return true;
    }

    /** Placa en tres piezas: puntas a tamaño fijo y centro estirado al ancho del botón. */
    public static void drawPlate(GuiGraphics g, TFTextures texture, int x, int y, int w, int h, int state, float alpha) {
        int texW = texture.width();
        int texH = texture.height();
        int stateH = texH / 3;
        int capTex = CAP * TEXELS_PER_PIXEL;
        int cap = Math.min(Math.round(CAP * h / 20f), w / 2);
        float v = state * stateH;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1f, 1f, 1f, alpha);
        g.blit(texture.id(), x, y, cap, h, 0f, v, capTex, stateH, texW, texH);
        g.blit(texture.id(), x + w - cap, y, cap, h, texW - capTex, v, capTex, stateH, texW, texH);
        if (w - 2 * cap > 0) {
            g.blit(texture.id(), x + cap, y, w - 2 * cap, h, capTex, v, texW - 2 * capTex, stateH, texW, texH);
        }
        g.setColor(1f, 1f, 1f, 1f);
    }

    /** Texto centrado con la letra de TF; si no cabe, se reduce un poco. */
    public static void drawLabel(GuiGraphics g, Component message, int x, int y, int w, int h, int rgb, float alpha, boolean shadow) {
        Font font = Minecraft.getInstance().font;
        MutableComponent text = Component.empty().setStyle(Style.EMPTY.withFont(TFMenuButton.FONT)).append(message);
        int textW = font.width(text);
        int maxW = w - 2 * Math.min(12, w / 6);
        float scale = textW > maxW && textW > 0 ? Math.max(0.6f, maxW / (float) textW) : 1f;
        int a = Mth.ceil(Mth.clamp(alpha, 0.02f, 1f) * 255f) << 24;
        g.pose().pushPose();
        g.pose().translate(x + w / 2f, y + h / 2f - 4f * scale, 0f);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, -textW / 2, 0, rgb | a, shadow);
        g.pose().popPose();
    }
}
