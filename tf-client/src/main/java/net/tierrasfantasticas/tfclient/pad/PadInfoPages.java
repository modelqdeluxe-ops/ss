package net.tierrasfantasticas.tfclient.pad;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Páginas del pad que se dibujan en el cliente (las demás apps las manda el servidor): Ajustes. */
final class PadInfoPages {
    private PadInfoPages() {}

    /**
     * Ajustes del pad (1.4): con la cabecera de la app, cada ajuste en su tarjeta (icono, nombre y qué hace) con un
     * interruptor que se desliza (toda la tarjeta se pulsa) y el volumen con un deslizador que se arrastra o se pulsa.
     */
    static final class Ajustes extends PadPage {
        private static final String[] NAMES = {"Sonidos del pad", "Tic al pasar por encima", "Animaciones", "Volumen",
                "Oír los altavoces de otros"};
        private static final String[] ABOUT = {"Clics, pestañas y avisos.", "Un tic suave al pasar el ratón por encima.",
                "Fondo animado, brillos y transiciones.", "El de los sonidos del pad.", "La música que ponen cerca de ti."};
        private static final ItemStack[] ICONS = {new ItemStack(Items.NOTE_BLOCK), new ItemStack(Items.LEVER),
                new ItemStack(Items.FIREWORK_ROCKET), new ItemStack(Items.JUKEBOX), new ItemStack(Items.BELL)};
        private static final int SLIDER_W = 96;
        /** Dónde está la bola de cada interruptor (0 a 1): va hacia el valor poco a poco. */
        private final float[] knob = new float[5];
        private boolean first = true;
        private long lastNs = System.nanoTime();
        /** Zonas de clic: {x, y, w, h} por ajuste (el 3 es el deslizador). */
        private final int[][] rects = new int[5][];
        private boolean dragging;

        Ajustes(TFPadScreen pad) {
            super(pad, "ajustes");
        }

        @Override
        String title() {
            return "AJUSTES";
        }

        private static boolean value(int i) {
            return switch (i) {
                case 0 -> PadSettings.sounds;
                case 1 -> PadSettings.hoverTick;
                case 2 -> PadSettings.animations;
                case 4 -> net.tierrasfantasticas.tfclient.pad.music.MusicLibrary.hearOthers();
                default -> true;
            };
        }

        @Override
        void render(GuiGraphics g, double mx, double my, float partial) {
            int acc = PadSkin.accent(app);
            long ns = System.nanoTime();
            float dt = Math.min(0.1F, (ns - lastNs) / 1e9F);
            lastNs = ns;
            PadSkin.window(g, X, Y, W, H, acc);
            int bh = PadSkin.bannerHeight(1);
            PadSkin.banner(g, pad, X + 1, Y + 1, W - 2, bh, acc, PadSkin.icon(app), List.of(PadSkin.tagline(app)));
            int top = Y + bh + 6, bottom = Y + H - 14;
            int rowH = Math.max(22, Math.min(30, (bottom - top) / 5));
            for (int i = 0; i < 5; i++) {
                float target = value(i) ? 1F : 0F;
                if (first || !PadSettings.animations) knob[i] = target;
                else knob[i] += (target - knob[i]) * Math.min(1F, dt * 14F);
                int y = top + i * rowH, h = rowH - 4, x = X + 6, w = W - 12;
                boolean slider = i == 3;
                int sx = x + w - SLIDER_W - 10, sy = y + (h - 14) / 2;
                boolean hover = slider ? PadUi.inside(mx, my, sx - 4, sy - 2, SLIDER_W + 8, 18) || dragging
                        : PadUi.inside(mx, my, x, y, w, h);
                if (hover) pad.hover("§ajuste" + i);
                int yy = PadSkin.card(g, x, y, w, h, hover && !slider ? 1 : 0, acc, 0);
                g.fillGradient(x + 1, yy + 1, x + 4, yy + h - 1, PadSkin.lighten(acc, 40), acc);
                int ms = Math.min(20, h - 4), iy = yy + (h - ms) / 2;
                PadSkin.medallion(g, x + 8, iy, ms, 0);
                PadUi.item(g, ICONS[i], x + 8 + (ms - 16) / 2, iy + (ms - 16) / 2, 1);
                int tx = x + 8 + ms + 7, textW = w - (tx - x) - (slider ? SLIDER_W + 50 : 44);
                boolean two = h >= 24;
                int ty = yy + (two ? (h - 20) / 2 + 1 : (h - 8) / 2);
                PadUi.title(g, NAMES[i], tx, ty, textW, PadUi.TEXT);
                if (two) PadUi.text(g, PadUi.fitEnd(ABOUT[i], textW), tx, ty + 10, PadUi.MUTED);
                if (slider) {
                    String pct = PadSettings.volume + "%";
                    PadUi.chip(g, sx - 6 - PadUi.chipWidth(pct), yy + (h - 12) / 2, pct, PadUi.TONE_BLUE, 12);
                    PadSkin.slider(g, sx, yy + (h - 14) / 2, SLIDER_W, PadSettings.volume / 100F, acc, hover);
                    rects[i] = new int[] {sx - 4, yy + (h - 14) / 2 - 2, SLIDER_W + 8, 18};
                } else {
                    PadSkin.toggle(g, x + w - 38, yy + (h - 14) / 2, knob[i], hover);
                    rects[i] = new int[] {x, y, w, h};
                }
            }
            first = false;
            // la nota de abajo, centrada (lejos de las esquinas de oro de la ventana)
            String note = PadUi.fitEnd("Tecla del pad: se cambia en Opciones › Controles.", W - 40);
            PadUi.text(g, note, X + (W - PadUi.font().width(note)) / 2, Y + H - 12, PadUi.MUTED);
        }

        /** El volumen según dónde está el ratón en el deslizador (de 5 en 5). */
        private void setVolume(double mx) {
            int[] r = rects[3];
            if (r == null) return;
            float f = (float) ((mx - (r[0] + 4 + 4.5)) / (SLIDER_W - 9));
            int v = Math.max(0, Math.min(100, Math.round(Math.max(0F, Math.min(1F, f)) * 20) * 5));
            if (v != PadSettings.volume) {
                PadSettings.volume = v;
                pad.sound("hover", 0.5F);
            }
        }

        @Override
        boolean click(double mx, double my, int button) {
            if (button != 0) return false;
            for (int i = 0; i < rects.length; i++) {
                int[] r = rects[i];
                if (r == null || !PadUi.inside(mx, my, r[0], r[1], r[2], r[3])) continue;
                switch (i) {
                    case 0 -> PadSettings.sounds = !PadSettings.sounds;
                    case 1 -> PadSettings.hoverTick = !PadSettings.hoverTick;
                    case 2 -> PadSettings.animations = !PadSettings.animations;
                    case 3 -> {
                        dragging = true;
                        setVolume(mx);
                        return true;
                    }
                    case 4 -> net.tierrasfantasticas.tfclient.pad.music.MusicSpeaker.setHearOthers(
                            !net.tierrasfantasticas.tfclient.pad.music.MusicLibrary.hearOthers());
                    default -> {
                    }
                }
                PadSettings.save();
                pad.sound("select", 0.75F);
                return true;
            }
            return false;
        }

        @Override
        boolean drag(double mx, double my, double dy) {
            if (!dragging) return false;
            setVolume(mx);
            return true;
        }

        @Override
        void release(double mx, double my, int button) {
            if (!dragging) return;
            dragging = false;
            PadSettings.save();
            pad.sound("select", 0.75F);
        }
    }
}
