/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * (1.3.156, el dueño: «que el tag del clan tenga una paleta extensa de colores, editable; quiero literal una paleta como
 * las de Paint») La paleta de colores del pad: el cuadro de saturación (de izquierda a derecha) y brillo (de arriba a
 * abajo), la barra de tono a su lado, los 28 colores básicos de Paint, la muestra de antes y de ahora con la etiqueta
 * pintada y el código hex que se puede escribir. GUARDAR manda «col:#RRGGBB» a la app que la abrió (los clanes, pestaña
 * ESTILO) y vuelve a ella; CANCELAR o atrás vuelven sin cambiar nada.
 * <p>
 * La abre el botón PALETA de una vista del servidor con la acción «§color:&lt;app&gt;:&lt;pestaña&gt;:&lt;RRGGBB&gt;:&lt;texto&gt;»
 * (PadViewPage.run).
 */
final class PadColorPicker extends PadPage {
    /** Los 28 colores de la paleta clásica de Paint (dos filas de 14). */
    static final int[] PAINT = {
            0x000000, 0x808080, 0x800000, 0x808000, 0x008000, 0x008080, 0x000080, 0x800080, 0x808040, 0x004040, 0x0080FF, 0x004080, 0x8000FF, 0x804000,
            0xFFFFFF, 0xC0C0C0, 0xFF0000, 0xFFFF00, 0x00FF00, 0x00FFFF, 0x0000FF, 0xFF00FF, 0xFFFF80, 0x00FF80, 0x80FFFF, 0x8080FF, 0xFF0080, 0xFF8040};

    private final String tab, label;
    private final int original;
    /** El color elegido en HSV (0-1). */
    private float hue, sat, val;
    private String hex = "";
    private boolean editing;
    /** 1: arrastrando en el cuadro; 2: en la barra de tono. */
    private int dragging;
    private int sqX, sqY, sqS, hueX;
    private final List<int[]> buttons = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    /** Para la prueba en el juego (BossTest «clanes»): pone este color (0xRRGGBB) y, si save, lo guarda. */
    static volatile int testColor = -1;
    static volatile boolean testSave;

    PadColorPicker(TFPadScreen pad, String app, String tab, int rgb, String label) {
        super(pad, app);
        this.tab = tab;
        this.label = label;
        this.original = rgb & 0xFFFFFF;
        setRgb(original);
    }

    /** «§color:clanes:estilo:FF8800:TAG» → la paleta (null si no se entiende). */
    static PadColorPicker parse(TFPadScreen pad, String action) {
        String[] p = action.substring("§color:".length()).split(":", 4);
        if (p.length < 3) return null;
        int rgb;
        try {
            rgb = Integer.parseInt(p[2].replace("#", ""), 16);
        } catch (NumberFormatException e) {
            rgb = 0xFFAA00;
        }
        return new PadColorPicker(pad, p[0], p[1], rgb, p.length > 3 ? p[3] : "");
    }

    @Override
    String title() {
        return "PALETA DE COLORES";
    }

    @Override
    PadPage backTo() {
        pad.openApp(app, tab);
        return pad.page();
    }

    // ---------------------------------------------------------------------------------------------------------------

    private int rgb() {
        return java.awt.Color.HSBtoRGB(hue, sat, val) & 0xFFFFFF;
    }

    private void setRgb(int rgb) {
        float[] hsb = java.awt.Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, null);
        // un gris no tiene tono: se queda el que había (si no, la barra saltaría al rojo)
        if (hsb[1] > 0.001F) hue = hsb[0];
        sat = hsb[1];
        val = hsb[2];
        hex = String.format("%06X", rgb & 0xFFFFFF);
    }

    private void fromHsv() {
        hex = String.format("%06X", rgb());
    }

    private void save() {
        pad.sound("select", 0.75F);
        TFPadNet.CHANNEL.sendToServer(new TFPadNet.Action(app, tab, "col:#" + String.format("%06X", rgb()), ""));
        pad.openApp(app, tab);
        pad.showNotice("Color guardado: #" + String.format("%06X", rgb()) + ".");
    }

    // ---------------------------------------------------------------------------------------------------------------

    @Override
    void render(GuiGraphics g, double mx, double my, float partial) {
        if (testColor >= 0) {
            setRgb(testColor);
            testColor = -1;
            if (testSave) {
                testSave = false;
                save();
                return;
            }
        }
        buttons.clear();
        actions.clear();
        int acc = PadSkin.accent(app);
        PadSkin.window(g, X, Y, W, H, acc);
        int top = Y + 6;
        // el cuadro de saturación y brillo
        sqS = Math.max(40, Math.min(H - 16, (W - 40) / 2));
        sqX = X + 8;
        sqY = top + Math.max(0, (H - 12 - sqS) / 2);
        PadUi.box(g, sqX - 1, sqY - 1, sqS + 2, sqS + 2, PadUi.INK);
        for (int i = 0; i < sqS; i++) {
            float s = i / (float) (sqS - 1);
            int c = java.awt.Color.HSBtoRGB(hue, s, 1F) | 0xFF000000;
            g.fillGradient(sqX + i, sqY, sqX + i + 1, sqY + sqS, c, 0xFF000000);
        }
        int cx = sqX + Math.round(sat * (sqS - 1)), cy = sqY + Math.round((1 - val) * (sqS - 1));
        ring(g, cx, cy, val > 0.55F && sat < 0.5F ? 0xFF18265C : 0xFFFFFFFF);
        // la barra de tono (vertical)
        hueX = sqX + sqS + 6;
        PadUi.box(g, hueX - 1, sqY - 1, 12, sqS + 2, PadUi.INK);
        for (int j = 0; j < sqS; j++) {
            int c = java.awt.Color.HSBtoRGB(j / (float) (sqS - 1), 1F, 1F) | 0xFF000000;
            g.fill(hueX, sqY + j, hueX + 10, sqY + j + 1, c);
        }
        int hy = sqY + Math.round(hue * (sqS - 1));
        g.fill(hueX - 3, hy - 1, hueX + 13, hy + 2, 0xFF18265C);
        g.fill(hueX - 2, hy, hueX + 12, hy + 1, 0xFFFFFFFF);
        // a la derecha: la muestra, los colores de Paint, el código y los botones
        int px = hueX + 20, pw = X + W - 8 - px;
        PadUi.title(g, "Color de la etiqueta", px, top + 2, pw, PadSkin.darken(acc, 0.7F));
        int y = top + 16;
        int half = (pw - 6) / 2;
        swatch(g, px, y, half, "Antes", original);
        swatch(g, px + half + 6, y, pw - half - 6, "Ahora", rgb());
        y += 32;
        // la etiqueta pintada, sobre oscuro (como en el chat) y sobre claro (como en el pad)
        int tagW = pw;
        g.fill(px, y, px + tagW / 2, y + 16, 0xFF20242C);
        g.fill(px + tagW / 2, y, px + tagW, y + 16, 0xFFF2F5FA);
        String tag = label.isEmpty() ? "[TAG]" : "[" + label + "]";
        int tw = PadUi.font().width(tag);
        g.drawString(PadUi.font(), tag, px + (tagW / 2 - tw) / 2, y + 4, 0xFF000000 | rgb(), true);
        PadUi.text(g, tag, px + tagW / 2 + (tagW / 2 - tw) / 2, y + 4, 0xFF000000 | rgb());
        y += 22;
        // los 28 colores básicos
        int sw = Math.max(6, Math.min(12, (pw - 13) / 14));
        for (int i = 0; i < PAINT.length; i++) {
            int sx = px + (i % 14) * (sw + 1), sy = y + (i / 14) * (sw + 1);
            int c = PAINT[i];
            boolean on = c == rgb();
            boolean hover = PadUi.inside(mx, my, sx, sy, sw, sw);
            if (hover) pad.hover("§paint" + i);
            g.fill(sx - 1, sy - 1, sx + sw + 1, sy + sw + 1, on ? 0xFFFFD650 : hover ? 0xFF18265C : 0xFF7C8AA8);
            g.fill(sx, sy, sx + sw, sy + sw, 0xFF000000 | c);
            buttons.add(new int[] {sx, sy, sw, sw});
            actions.add(() -> setRgb(c));
        }
        y += 2 * (sw + 1) + 6;
        // el código (se puede escribir)
        int fw = Math.min(pw, 110);
        PadSkin.field(g, px, y, fw, editing ? "#" + hex : "", editing ? "" : "#" + hex + " (escribe un código)", acc);
        buttons.add(new int[] {px, y, fw, 16});
        actions.add(() -> {
            editing = true;
            hex = "";
        });
        // los botones
        int by = Y + H - 22;
        int gw = PadUi.buttonWidth("GUARDAR"), cw = PadUi.buttonWidth("CANCELAR");
        button(g, X + W - 8 - gw, by, gw, "GUARDAR", PadView.GREEN, mx, my, this::save);
        button(g, X + W - 8 - gw - 6 - cw, by, cw, "CANCELAR", PadView.RED, mx, my, () -> pad.back());
    }

    private static void ring(GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx - 3, cy - 1, cx + 4, cy + 2, 0x8018265C);
        g.fill(cx - 1, cy - 3, cx + 2, cy + 4, 0x8018265C);
        g.fill(cx - 3, cy, cx - 1, cy + 1, color);
        g.fill(cx + 2, cy, cx + 4, cy + 1, color);
        g.fill(cx, cy - 3, cx + 1, cy - 1, color);
        g.fill(cx, cy + 2, cx + 1, cy + 4, color);
    }

    private static void swatch(GuiGraphics g, int x, int y, int w, String label, int rgb) {
        PadUi.text(g, label, x, y, PadUi.MUTED);
        PadUi.box(g, x, y + 10, w, 18, PadUi.INK);
        g.fill(x + 1, y + 11, x + w - 1, y + 27, 0xFF000000 | rgb);
        String code = String.format("#%06X", rgb);
        int lum = ((rgb >> 16 & 255) * 3 + (rgb >> 8 & 255) * 6 + (rgb & 255)) / 10;
        PadUi.text(g, code, x + (w - PadUi.font().width(code)) / 2, y + 15, lum > 140 ? 0xFF18265C : 0xFFFFFFFF);
    }

    private void button(GuiGraphics g, int x, int y, int w, String label, int style, double mx, double my, Runnable action) {
        boolean hover = PadUi.inside(mx, my, x, y, w, 16);
        if (hover) pad.hover("§" + label);
        PadSkin.button(g, x, y, w, label, style, hover, true, false);
        buttons.add(new int[] {x, y, w, 16});
        actions.add(action);
    }

    // ---------------------------------------------------------------------------------------------------------------

    private boolean pickAt(double mx, double my) {
        if (dragging == 1) {
            sat = (float) Math.max(0, Math.min(1, (mx - sqX) / (sqS - 1)));
            val = (float) Math.max(0, Math.min(1, 1 - (my - sqY) / (sqS - 1)));
            fromHsv();
            return true;
        }
        if (dragging == 2) {
            hue = (float) Math.max(0, Math.min(0.9999, (my - sqY) / (sqS - 1)));
            fromHsv();
            return true;
        }
        return false;
    }

    @Override
    boolean click(double mx, double my, int button) {
        if (button != 0) return false;
        if (PadUi.inside(mx, my, sqX - 2, sqY - 2, sqS + 4, sqS + 4)) {
            editing = false;
            dragging = 1;
            return pickAt(mx, my);
        }
        if (PadUi.inside(mx, my, hueX - 3, sqY - 2, 16, sqS + 4)) {
            editing = false;
            dragging = 2;
            return pickAt(mx, my);
        }
        for (int i = 0; i < buttons.size(); i++) {
            int[] b = buttons.get(i);
            if (PadUi.inside(mx, my, b[0], b[1], b[2], b[3])) {
                pad.sound("select", 0.6F);
                if (editing) closeHex();
                actions.get(i).run();
                return true;
            }
        }
        if (editing) closeHex();
        return false;
    }

    @Override
    boolean drag(double mx, double my, double dy) {
        return pickAt(mx, my);
    }

    @Override
    void release(double mx, double my, int button) {
        dragging = 0;
    }

    @Override
    boolean typing() {
        return editing;
    }

    /** Deja de escribir: si el código está completo, es el color; si no, vuelve al de antes. */
    private void closeHex() {
        editing = false;
        if (hex.length() == 6) setRgb(Integer.parseInt(hex, 16));
        else fromHsv();
    }

    @Override
    boolean key(int key, int scan, int mods) {
        if (!editing) return false;
        if (key == 259) { // borrar
            if (!hex.isEmpty()) hex = hex.substring(0, hex.length() - 1);
            return true;
        }
        if (key == 257 || key == 335) { // Enter
            closeHex();
            return true;
        }
        if (key == 256) { // Esc: deja de escribir
            editing = false;
            fromHsv();
            return true;
        }
        if (key == 86 && (mods & 2) != 0) { // Ctrl+V
            for (char c : Minecraft.getInstance().keyboardHandler.getClipboard().toCharArray()) chr(c);
            return true;
        }
        return false;
    }

    @Override
    boolean chr(char c) {
        if (!editing) return false;
        c = Character.toUpperCase(c);
        if ((c >= '0' && c <= '9' || c >= 'A' && c <= 'F') && hex.length() < 6) hex += c;
        if (hex.length() == 6) closeHex();
        return true;
    }
}
