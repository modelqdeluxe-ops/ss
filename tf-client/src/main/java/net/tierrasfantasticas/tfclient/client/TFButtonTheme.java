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
 * Tema de TF para los botones fuera de una partida (mundos, opciones, conexión...): la placa de piedra con marco
 * plateado y gemas ámbar de la lámina de ajustes del dueño (button_slice.png, de tools/gen_menu_buttons.py) y letra
 * Cinzel. Las puntas (con la gema) guardan su forma a la altura del botón y el centro liso se corta a lo que mida (sin
 * estirar). Con el ratón encima la placa se aclara y las gemas se encienden con un fundido suave. Dentro de una partida
 * no se toca nada, para que cada jugador vea sus paquetes de recursos.
 */
public final class TFButtonTheme {
    /** Medidas de button_slice.png: alto de la placa, separación entre estados y ancho de cada punta (en texels). */
    private static final int PLATE_H = 135, PAD = 16, CAP_L = 115, CAP_R = 114;
    private static final float HOVER_MS = 140f;
    /** Fundido del ratón de cada botón: {encendido 0..1, último fotograma}. */
    private static final java.util.Map<AbstractWidget, float[]> FADE = new java.util.WeakHashMap<>();

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
        float e = fade(button, hot);
        drawPlate(g, button.getX(), button.getY(), button.getWidth(), button.getHeight(), e, button.active, 1f);
        int color = !button.active ? 0xA0A0A0 : TFMenuPaint.lerp(0xFFF5EAD0, 0xFFFFD667, e) & 0xFFFFFF;
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
        drawPlate(g, x, y, w, h, fade(slider, hot), slider.active, 1f);
        int handleX = x + (int) (Mth.clamp(value, 0.0, 1.0) * (w - 8));
        g.fill(handleX, y + 1, handleX + 8, y + h - 1, 0xFF1A1206);
        g.fill(handleX + 1, y + 2, handleX + 7, y + h - 2, hot ? 0xFFFFD667 : 0xFFE2B04A);
        g.fill(handleX + 1, y + 2, handleX + 7, y + 3, 0xFFFFF3C4);
        g.fill(handleX + 1, y + h - 3, handleX + 7, y + h - 2, 0xFF9A6A1C);
        int color = !slider.active ? 0xA0A0A0 : hot ? 0xFFD667 : 0xF5EAD0;
        drawLabel(g, slider.getMessage(), x, y, w, h, color, 1f, true);
        return true;
    }

    /** Fundido suave del estado de ratón encima (0..1, con curva). */
    private static float fade(AbstractWidget widget, boolean hot) {
        float[] f = FADE.computeIfAbsent(widget, w -> new float[] {0f, 0f});
        float now = net.minecraft.Util.getMillis() % 1_000_000L;
        float dt = f[1] == 0f || now < f[1] ? 1000f : Math.min(250f, now - f[1]);
        f[1] = now;
        float step = dt / HOVER_MS;
        f[0] = hot ? Math.min(1f, f[0] + step) : Math.max(0f, f[0] - step);
        return f[0] * f[0] * (3f - 2f * f[0]);
    }

    /** Placa de piedra: la normal y, encima, la iluminada con la opacidad del ratón; gris si está desactivada. */
    public static void drawPlate(GuiGraphics g, int x, int y, int w, int h, float hover, boolean active, float alpha) {
        if (!TFTextures.BUTTON_SLICE.ready()) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (!active) {
            drawState(g, x, y, w, h, 2, alpha);
        } else {
            drawState(g, x, y, w, h, 0, alpha);
            if (hover > 0.01f) drawState(g, x, y, w, h, 1, alpha * hover);
        }
        g.setColor(1f, 1f, 1f, 1f);
    }

    /** Un estado de la placa: puntas a la altura del botón y el centro cortado (repetido si el botón es muy ancho). */
    private static void drawState(GuiGraphics g, int x, int y, int w, int h, int state, float alpha) {
        TFTextures tex = TFTextures.BUTTON_SLICE;
        int s = Math.max(1, (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
        float k = h / (float) PLATE_H;
        float capL = CAP_L * k, capR = CAP_R * k;
        if (capL + capR > w) {
            float f = w / (capL + capR);
            capL *= f;
            capR *= f;
        }
        float xl = snap(x + capL, s), xr = snap(x + w - capR, s);
        int v = PAD + state * (PLATE_H + PAD);
        int fill = tex.width() - CAP_L - CAP_R;
        g.setColor(1f, 1f, 1f, alpha);
        piece(g, tex, x, y, xl - x, h, 0, v, CAP_L);
        for (float at = xl; at < xr - 0.01f; ) {
            float seg = Math.min(fill * k, xr - at);
            piece(g, tex, at, y, seg, h, CAP_L, v, Math.max(1, Math.min(fill, Math.round(seg / k))));
            at += seg;
        }
        piece(g, tex, xr, y, x + w - xr, h, tex.width() - CAP_R, v, CAP_R);
    }

    private static void piece(GuiGraphics g, TFTextures tex, float x, float y, float w, float h, int u, int v, int uw) {
        if (w <= 0f) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().scale(w / uw, h / PLATE_H, 1f);
        g.blit(tex.id(), 0, 0, u, v, uw, PLATE_H, tex.width(), tex.height());
        g.pose().popPose();
    }

    private static float snap(float gui, int s) {
        return Math.round(gui * s) / (float) s;
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
