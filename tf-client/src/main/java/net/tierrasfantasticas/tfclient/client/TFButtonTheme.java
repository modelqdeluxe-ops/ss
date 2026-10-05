package net.tierrasfantasticas.tfclient.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.Util;
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
        drawPlate(g, texture, x, y, w, h, state, alpha, false);
    }

    private static void drawPlate(GuiGraphics g, TFTextures texture, int x, int y, int w, int h, int state, float alpha, boolean additive) {
        int texW = texture.width();
        int texH = texture.height();
        int stateH = texH / 3;
        int capTex = CAP * TEXELS_PER_PIXEL;
        int cap = Math.min(Math.round(CAP * h / 20f), w / 2);
        float v = state * stateH;
        RenderSystem.enableBlend();
        if (additive) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }
        g.setColor(1f, 1f, 1f, alpha);
        g.blit(texture.id(), x, y, cap, h, 0f, v, capTex, stateH, texW, texH);
        g.blit(texture.id(), x + w - cap, y, cap, h, texW - capTex, v, capTex, stateH, texW, texH);
        if (w - 2 * cap > 0) {
            g.blit(texture.id(), x + cap, y, w - 2 * cap, h, capTex, v, texW - 2 * capTex, stateH, texW, texH);
        }
        g.setColor(1f, 1f, 1f, 1f);
        if (additive) RenderSystem.defaultBlendFunc();
    }

    /**
     * Brillo de los botones de oro (distinto al del emblema, que es una franja que lo cruza): el oro "respira" con un
     * latido cálido muy suave y aparecen chispitas que se encienden y apagan en distintos puntos del borde, como luz
     * que rebota en el metal.
     */
    public static void drawGoldShimmer(GuiGraphics g, int x, int y, int w, int h, int state, float alpha, int seed) {
        long now = Util.getMillis();
        // Latido: se suma la propia placa con muy poca opacidad
        float breath = 0.5f + 0.5f * (float) Math.sin((now + seed * 977L) / 1700.0 * Math.PI);
        drawPlate(g, TFTextures.BUTTON_SLICE_PRIMARY, x, y, w, h, state, alpha * (0.06f + 0.12f * breath), true);

        if (!TFTextures.SPARKLE.ready()) return;
        // Dos series de chispas desfasadas; cada una ocupa una ranura de tiempo y sale en un punto al azar del borde
        for (int series = 0; series < 2; series++) {
            long slotMs = 1150L + series * 370L;
            long t = now + seed * 331L + series * 523L;
            long slot = t / slotMs;
            float life = (t % slotMs) / (float) (slotMs * 0.7f);
            if (life >= 1f) continue;
            java.util.Random random = new java.util.Random(slot * 31L + seed * 7L + series);
            float px = x + Math.max(6, h / 2) + random.nextFloat() * Math.max(1, w - 2 * Math.max(6, h / 2));
            float py = random.nextBoolean() ? y + 2.5f : y + h - 2.5f;
            float strength = (float) Math.sin(life * Math.PI);
            float size = h * (0.55f + 0.35f * random.nextFloat()) * (0.5f + 0.5f * strength);
            g.pose().pushPose();
            g.pose().translate(px, py, 0f);
            g.pose().mulPose(Axis.ZP.rotationDegrees(45f * life));
            g.pose().scale(size / TFTextures.SPARKLE.width(), size / TFTextures.SPARKLE.height(), 1f);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            g.setColor(1f, 0.93f, 0.72f, alpha * strength);
            int sw = TFTextures.SPARKLE.width();
            int sh = TFTextures.SPARKLE.height();
            g.blit(TFTextures.SPARKLE.id(), -sw / 2, -sh / 2, 0f, 0f, sw, sh, sw, sh);
            g.setColor(1f, 1f, 1f, 1f);
            RenderSystem.defaultBlendFunc();
            g.pose().popPose();
        }
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
