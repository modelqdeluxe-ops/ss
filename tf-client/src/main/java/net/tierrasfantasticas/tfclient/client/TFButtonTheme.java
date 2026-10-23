/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.client;

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
 * Tema de TF para los botones fuera de una partida (mundos, opciones, conexión...). 1.3.120 (el dueño: «rework completo
 * de los botones; no me gustan las texturas actuales; como los de los diálogos de los NPC, mejor diseñados»): la misma
 * pastilla de cristal oscuro con hilo de oro que el menú principal ({@link TFPill}), sin texturas, y letra Cinzel. Con el
 * ratón encima se enciende con un fundido, echa un halo de oro, la cruza un destello y (en los anchos) le crecen hilos
 * de oro a los lados del texto; al pulsar se hunde. Los deslizadores, con un tirador de oro. Dentro de una partida no
 * se toca nada, para que cada jugador vea sus paquetes de recursos.
 */
public final class TFButtonTheme {
    private static final float HOVER_MS = 140f;
    /** Lo de cada botón: el fundido del ratón, el último fotograma, cuándo empezó el destello y si tenía el ratón. */
    private static final class State {
        float hover;
        long last;
        long shine = -1L;
        boolean hot;
    }

    private static final java.util.Map<AbstractWidget, State> STATES = new java.util.WeakHashMap<>();

    /** (La prueba en el juego) El tema también dentro de la partida (para sacar Opciones con él). */
    public static volatile boolean testForce;

    private TFButtonTheme() {}

    /** true si este botón debe llevar el tema de TF ahora mismo. */
    public static boolean themed(AbstractWidget widget) {
        if (Minecraft.getInstance().level != null && !testForce) return false;
        if (widget.getWidth() < 40 || widget.getHeight() < 14) return false; // botones de icono: se quedan como están
        return !(widget instanceof Checkbox) && !(widget instanceof ImageButton);
    }

    /** Botón normal o de alternar opciones. Devuelve false si no se pudo dibujar (se usa el de Minecraft). */
    public static boolean renderButton(AbstractButton button, GuiGraphics g) {
        if (!themed(button)) return false;
        boolean hot = button.active && button.isHoveredOrFocused();
        State st = state(button, hot);
        float e = ease(st.hover);
        boolean pressed = button.active && button.isHovered() && Minecraft.getInstance().mouseHandler.isLeftPressed();
        int s = TFPill.scale();
        float lift = pressed ? Math.max(1, Math.round(0.5f * s)) / (float) s : -Math.round(e * s) / (float) s;
        g.pose().pushPose();
        g.pose().translate(0f, lift, 0f);
        TFPill.paint(g, button.getX(), button.getY(), button.getWidth(), button.getHeight(), TFPill.Style.VIOLET, e, pressed, button.active,
                false, 1f, shine(st));
        int color = !button.active ? 0x8A8494 : TFMenuPaint.lerp(0xFFEAE4D8, 0xFFFFE9A8, e) & 0xFFFFFF;
        drawLabel(g, button.getMessage(), button.getX(), button.getY(), button.getWidth(), button.getHeight(), color, 1f, button.getWidth() >= 120 ? e : 0f, e, false);
        g.pose().popPose();
        return true;
    }

    /** Deslizador (volumen, FOV...): la pastilla, un tirador de oro y el texto. */
    public static boolean renderSlider(AbstractSliderButton slider, double value, GuiGraphics g) {
        if (!themed(slider)) return false;
        boolean hot = slider.active && slider.isHoveredOrFocused();
        State st = state(slider, hot);
        float e = ease(st.hover);
        int x = slider.getX();
        int y = slider.getY();
        int w = slider.getWidth();
        int h = slider.getHeight();
        TFPill.paint(g, x, y, w, h, TFPill.Style.VIOLET, 0.6f * e, false, slider.active, false, 1f, -1f);
        int handleX = x + 3 + (int) (Mth.clamp(value, 0.0, 1.0) * (w - 6 - 6));
        TFPill.knob(g, handleX, y + 3, 6, h - 6, hot, slider.active);
        int color = !slider.active ? 0x8A8494 : TFMenuPaint.lerp(0xFFEAE4D8, 0xFFFFE9A8, e) & 0xFFFFFF;
        // (con contorno oscuro: el texto pasa por encima del tirador de oro)
        drawLabel(g, slider.getMessage(), x, y, w, h, color, 1f, 0f, e, true);
        return true;
    }

    /** El fundido del ratón (y el destello: empieza cuando entra el ratón). */
    private static State state(AbstractWidget widget, boolean hot) {
        State st = STATES.computeIfAbsent(widget, w -> new State());
        long now = net.minecraft.Util.getMillis();
        float dt = st.last == 0L || now < st.last ? 1000f : Math.min(250f, now - st.last);
        st.last = now;
        if (hot && !st.hot) st.shine = now;
        st.hot = hot;
        float step = dt / HOVER_MS;
        st.hover = hot ? Math.min(1f, st.hover + step) : Math.max(0f, st.hover - step);
        return st;
    }

    private static float ease(float f) {
        return f * f * (3f - 2f * f);
    }

    private static float shine(State st) {
        long t = net.minecraft.Util.getMillis() - st.shine;
        return st.shine >= 0L && t < TFPill.SHINE_MS ? t / (float) TFPill.SHINE_MS : -1f;
    }

    /**
     * Texto centrado con la letra de TF (si no cabe, se reduce un poco) y, con thread &gt; 0, los hilos de oro a sus
     * lados (e: el ratón).
     */
    public static void drawLabel(GuiGraphics g, Component message, int x, int y, int w, int h, int rgb, float alpha, float thread, float e,
                                 boolean outline) {
        Font font = Minecraft.getInstance().font;
        MutableComponent text = Component.empty().setStyle(Style.EMPTY.withFont(TFMenuButton.FONT)).append(message);
        int textW = font.width(text);
        int maxW = w - 2 * Math.min(12, w / 6);
        float scale = textW > maxW && textW > 0 ? Math.max(0.6f, maxW / (float) textW) : 1f;
        if (thread > 0.01f) TFPill.threads(g, x, y, w, h, x + w / 2f - textW * scale / 2f, x + w / 2f + textW * scale / 2f, thread, e, alpha);
        int a = Mth.ceil(Mth.clamp(alpha, 0.02f, 1f) * 255f) << 24;
        g.pose().pushPose();
        g.pose().translate(x + w / 2f, y + h / 2f - 4f * scale, 0f);
        g.pose().scale(scale, scale, 1f);
        if (outline) {
            // contorno violeta muy oscuro (como las letras del menú principal) en lugar de la sombra
            int dark = Mth.ceil(Mth.clamp(alpha, 0.02f, 1f) * 230f) << 24 | 0x1A0E2E;
            for (int[] d : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) g.drawString(font, text, -textW / 2 + d[0], d[1], dark, false);
            g.drawString(font, text, -textW / 2, 0, rgb | a, false);
        } else {
            g.drawString(font, text, -textW / 2, 0, rgb | a, true);
        }
        g.pose().popPose();
    }
}
