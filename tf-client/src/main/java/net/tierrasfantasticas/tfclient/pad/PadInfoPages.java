/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
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
                "Oír los altavoces de otros", "Tema del pad", "Fondo del pad", "Teclas de revivir"};
        private static final String[] ABOUT = {"Clics, pestañas y avisos.", "Un tic suave al pasar el ratón por encima.",
                "Fondo animado, brillos y transiciones.", "El de los sonidos del pad.", "La música que ponen cerca de ti.",
                "El color del cristal.", "Una imagen o un color de fondo para todo el pad (o tus PNG).",
                "Rendirse y auto-revivir (también en Opciones › Controles)."};
        private static final ItemStack[] ICONS = {new ItemStack(Items.NOTE_BLOCK), new ItemStack(Items.LEVER),
                new ItemStack(Items.FIREWORK_ROCKET), new ItemStack(Items.JUKEBOX), new ItemStack(Items.BELL),
                new ItemStack(Items.PINK_DYE), new ItemStack(Items.PAINTING), new ItemStack(Items.TOTEM_OF_UNDYING)};
        /** (1.3.157) La 8.ª, las teclas de revivir, solo si el revivir del TF Client está encendido. */
        private static final int ROWS = 8;

        private static int rows() {
            return net.tierrasfantasticas.tfclient.revive.TFRevive.active() ? ROWS : ROWS - 1;
        }
        /** Las muestras de los temas: lado y hueco entre ellas. */
        private static final int SW = 13, SW_GAP = 3;
        private final int[][] swatches = new int[PadSettings.THEMES.length][];
        private static final int SLIDER_W = 96;
        /** Dónde está la bola de cada interruptor (0 a 1): va hacia el valor poco a poco. */
        private final float[] knob = new float[ROWS];
        private boolean first = true;
        private long lastNs = System.nanoTime();
        /** Zonas de clic: {x, y, w, h} por ajuste (el 3 es el deslizador). */
        private final int[][] rects = new int[ROWS][];
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
            int rows = rows();
            // (1.3.157) con la 8.ª fila no caben con la nota de abajo: se usa ese hueco y la nota se quita (medido en la
            // captura de la prueba: la fila de las teclas quedaba debajo de la nota y cortada)
            boolean note = (bottom - top) / rows >= 19;
            if (!note) bottom = Y + H - 5;
            int rowH = Math.max(note ? 19 : 16, Math.min(30, (bottom - top) / rows));
            for (int i = 0; i < rows; i++) {
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
                boolean themes = i == 5, wallpaper = i == 6 || i == 7;
                int swW = PadSettings.THEMES.length * (SW + SW_GAP) - SW_GAP;
                String wpName = i == 7 ? reviveKeys() : wallpaper ? wallpaperName() : "";
                int tx = x + 8 + ms + 7, textW = w - (tx - x) - (slider ? SLIDER_W + 50 : themes ? swW + 18 : wallpaper ? PadUi.chipWidth(wpName) + 30 : 44);
                boolean two = h >= 24;
                int ty = yy + (two ? (h - 20) / 2 + 1 : (h - 8) / 2);
                PadUi.title(g, NAMES[i], tx, ty, textW, PadUi.TEXT);
                if (two) PadUi.text(g, PadUi.fitEnd(ABOUT[i], textW), tx, ty + 10, PadUi.MUTED);
                if (slider) {
                    String pct = PadSettings.volume + "%";
                    PadUi.chip(g, sx - 6 - PadUi.chipWidth(pct), yy + (h - 12) / 2, pct, PadUi.TONE_BLUE, 12);
                    PadSkin.slider(g, sx, yy + (h - 14) / 2, SLIDER_W, PadSettings.volume / 100F, acc, hover);
                    rects[i] = new int[] {sx - 4, yy + (h - 14) / 2 - 2, SLIDER_W + 8, 18};
                } else if (themes) {
                    // las muestras de color: la elegida, más grande con borde blanco y su marca
                    int bx = x + w - 10 - swW, by = yy + (h - SW) / 2;
                    for (int k = 0; k < PadSettings.THEMES.length; k++) {
                        PadSettings.Theme th = PadSettings.THEMES[k];
                        int sx0 = bx + k * (SW + SW_GAP);
                        boolean sel = k == PadSettings.theme, sh = PadUi.inside(mx, my, sx0, by, SW, SW);
                        if (sh) pad.hover("§tema" + k);
                        int lift = sh && !sel ? 1 : 0;
                        g.fill(sx0 + 1, by + SW, sx0 + SW - 1, by + SW + 1, 0x3018265C);
                        PadUi.box(g, sx0, by - lift, SW, SW, sel ? 0xFFFFFFFF : PadSkin.darken(0xFF000000 | th.bottom(), 0.6F));
                        g.fillGradient(sx0 + 1, by + 1 - lift, sx0 + SW - 1, by + SW - 1 - lift, 0xFF000000 | th.top(), 0xFF000000 | th.bottom());
                        g.fill(sx0 + 2, by + 1 - lift, sx0 + SW - 2, by + 2 - lift, 0x70FFFFFF);
                        if (sel) {
                            PadUi.box(g, sx0 - 1, by - 1, SW + 2, SW + 2, PadUi.INK);
                            int cx = sx0 + 3, cy = by + 6;
                            for (int d = 0; d < 3; d++) g.fill(cx + d, cy + d, cx + d + 1, cy + d + 1, 0xFFFFFFFF);
                            for (int d = 0; d < 5; d++) g.fill(cx + 2 + d, cy + 2 - d, cx + 3 + d, cy + 3 - d, 0xFFFFFFFF);
                        }
                        swatches[k] = new int[] {sx0, by - 1, SW, SW + 2};
                    }
                    String name = PadSettings.theme().name();
                    PadUi.chip(g, bx - 6 - PadUi.chipWidth(name), yy + (h - 12) / 2, name, PadUi.TONE_BLUE, 12);
                    rects[i] = null;
                } else if (wallpaper) {
                    // (1.3.155) el fondo elegido y una flecha: toda la tarjeta abre el selector (1.3.157: o las teclas de revivir)
                    int ax = x + w - 14, ay = yy + h / 2;
                    PadUi.chip(g, ax - 8 - PadUi.chipWidth(wpName), yy + (h - 12) / 2, wpName, PadUi.TONE_BLUE, 12);
                    int c = hover ? PadUi.GOLD_LO : PadUi.INK;
                    for (int d = 0; d < 4; d++) g.fill(ax + d, ay - 3 + d, ax + d + 1, ay + 4 - d, c);
                    rects[i] = new int[] {x, y, w, h};
                } else {
                    PadSkin.toggle(g, x + w - 38, yy + (h - 14) / 2, knob[i], hover);
                    rects[i] = new int[] {x, y, w, h};
                }
            }
            first = false;
            // la nota de abajo, centrada (lejos de las esquinas de oro de la ventana)
            if (note) {
                String text = PadUi.fitEnd("Tecla del pad: se cambia en Opciones › Controles.", W - 40);
                PadUi.text(g, text, X + (W - PadUi.font().width(text)) / 2, Y + H - 12, PadUi.MUTED);
            }
        }

        /** (1.3.157) Las dos teclas de revivir, p. ej. «E · F». */
        private static String reviveKeys() {
            return net.tierrasfantasticas.tfclient.revive.client.RevivemodKeybinds.SURRENDER.getTranslatedKeyMessage().getString() + " · "
                    + net.tierrasfantasticas.tfclient.revive.client.RevivemodKeybinds.SELF_REVIVE.getTranslatedKeyMessage().getString();
        }

        private static String wallpaperName() {
            PadWallpaper.Option o = PadWallpaper.current();
            return o == null ? "El del tema" : o.name();
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
            for (int k = 0; k < swatches.length; k++) {
                int[] r = swatches[k];
                if (r == null || !PadUi.inside(mx, my, r[0], r[1], r[2], r[3])) continue;
                PadSettings.theme = k;
                PadSettings.save();
                pad.sound("select", 0.75F);
                return true;
            }
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
                    case 6 -> {
                        pad.sound("page", 0.55F);
                        pad.setPage(new PadWallpaperPage(pad));
                        return true;
                    }
                    case 7 -> {
                        // (1.3.157) la pantalla de teclas del Revive Mod; al cerrarla se vuelve al pad
                        pad.sound("page", 0.55F);
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                        mc.setScreen(new net.tierrasfantasticas.tfclient.revive.client.ReviveConfigScreen(mc.screen));
                        return true;
                    }
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
