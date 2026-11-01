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
 * (1.3.159, el dueño: «quiero un editor completo de efecto arcoíris y degradado en el tag del clan») El editor de la
 * etiqueta: SÓLIDO (un color), DEGRADADO (2 o 3 colores, letra a letra), ARCOÍRIS (todos, corriendo; con su velocidad) y
 * ONDA (el degradado, que va y viene); la casilla elegida se cambia con la paleta de abajo, y arriba a la derecha se ve
 * la etiqueta animada sobre oscuro y sobre claro. GUARDAR manda «col:#RRGGBB» (sólido) o «tagfx:modo|colores|velocidad».
 * <p>
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
    /** (1.3.159) Los efectos de la etiqueta y su nombre en los botones. */
    private static final String[] MODES = {net.tierrasfantasticas.tfclient.clans.ClanTagFx.SOLID, net.tierrasfantasticas.tfclient.clans.ClanTagFx.GRADIENT,
            net.tierrasfantasticas.tfclient.clans.ClanTagFx.RAINBOW, net.tierrasfantasticas.tfclient.clans.ClanTagFx.WAVE};
    private static final String[] MODE_NAMES = {"SÓLIDO", "DEGRADADO", "ARCOÍRIS", "ONDA"};

    private final String tab, label;
    private final int original;
    /** El color que se está editando en HSV (0-1): el de la casilla elegida. */
    private float hue, sat, val;
    private String hex = "";
    private boolean editing;
    /** 1: arrastrando en el cuadro; 2: en la barra de tono. */
    private int dragging;
    private int sqX, sqY, sqS, hueX;
    private final List<int[]> buttons = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    /** (1.3.159) El efecto: modo, sus colores (1 a 3, count los que se usan), la casilla elegida y la velocidad. */
    private String mode = MODES[0];
    private final int[] cols = new int[3];
    private int count = 1, slot, speed = 2;
    /** Para la prueba en el juego (BossTest «clanes»): pone este color (0xRRGGBB) y, si save, lo guarda. */
    static volatile int testColor = -1;
    static volatile boolean testSave;
    /** (1.3.159) Para la prueba: pone este efecto («modo|RRGGBB,RRGGBB|velocidad») y, si testSave, lo guarda. */
    static volatile String testFx;

    PadColorPicker(TFPadScreen pad, String app, String tab, int rgb, String label, String fx) {
        super(pad, app);
        this.tab = tab;
        this.label = label;
        this.original = rgb & 0xFFFFFF;
        cols[0] = cols[1] = cols[2] = original;
        cols[1] = 0xFFD24A;
        cols[2] = 0x55AAFF;
        setFx(fx);
        setRgb(cols[slot]);
    }

    private void setFx(String fx) {
        if (fx == null || fx.isEmpty()) return;
        net.tierrasfantasticas.tfclient.clans.ClanTagFx.Fx f = net.tierrasfantasticas.tfclient.clans.ClanTagFx.decode(fx, original);
        mode = f.mode();
        speed = f.speed();
        int[] c = f.colors();
        for (int i = 0; i < Math.min(3, c.length); i++) cols[i] = c[i];
        count = mode.equals(MODES[0]) || mode.equals(MODES[2]) ? 1 : Math.max(2, c.length);
        slot = 0;
    }

    /** «§color:clanes:estilo:FF8800:TAG[:modo|colores|velocidad]» → el editor (null si no se entiende). */
    static PadColorPicker parse(TFPadScreen pad, String action) {
        String[] p = action.substring("§color:".length()).split(":", 5);
        if (p.length < 3) return null;
        int rgb;
        try {
            rgb = Integer.parseInt(p[2].replace("#", ""), 16);
        } catch (NumberFormatException e) {
            rgb = 0xFFAA00;
        }
        return new PadColorPicker(pad, p[0], p[1], rgb, p.length > 3 ? p[3] : "", p.length > 4 ? p[4] : "");
    }

    @Override
    String title() {
        return "ETIQUETA DEL CLAN";
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
        cols[slot] = rgb & 0xFFFFFF;
    }

    private void fromHsv() {
        hex = String.format("%06X", rgb());
        cols[slot] = rgb();
    }

    private void pickSlot(int i) {
        slot = i;
        setRgb(cols[i]);
    }

    private void setMode(String m) {
        mode = m;
        if (m.equals(MODES[0]) || m.equals(MODES[2])) count = 1;
        else if (count < 2) count = 2;
        if (slot >= count) pickSlot(0);
    }

    /** El efecto tal como se ve ahora. */
    private net.tierrasfantasticas.tfclient.clans.ClanTagFx.Fx fx() {
        int[] c = new int[count];
        System.arraycopy(cols, 0, c, 0, count);
        return new net.tierrasfantasticas.tfclient.clans.ClanTagFx.Fx(mode, c, speed);
    }

    private void save() {
        pad.sound("select", 0.75F);
        if (mode.equals(MODES[0])) {
            TFPadNet.CHANNEL.sendToServer(new TFPadNet.Action(app, tab, "col:#" + String.format("%06X", cols[0]), ""));
            pad.openApp(app, tab);
            pad.showNotice("Color guardado: #" + String.format("%06X", cols[0]) + ".");
        } else {
            TFPadNet.CHANNEL.sendToServer(new TFPadNet.Action(app, tab, "tagfx:" + fx().encode(), ""));
            pad.openApp(app, tab);
            pad.showNotice("Etiqueta guardada: " + MODE_NAMES[java.util.Arrays.asList(MODES).indexOf(mode)].toLowerCase(java.util.Locale.ROOT) + ".");
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    @Override
    void render(GuiGraphics g, double mx, double my, float partial) {
        if (testFx != null) {
            setFx(testFx);
            setRgb(cols[slot]);
            testFx = null;
        }
        if (testColor >= 0) {
            setRgb(testColor);
            testColor = -1;
            if (testSave) {
                testSave = false;
                save();
                return;
            }
        }
        if (testSave && testColor < 0) {
            testSave = false;
            save();
            return;
        }
        buttons.clear();
        actions.clear();
        int acc = PadSkin.accent(app);
        PadSkin.window(g, X, Y, W, H, acc);
        int top = Y + 4;
        boolean rainbow = mode.equals(MODES[2]);
        // a la izquierda: el cuadro de saturación y brillo y la barra de tono (del color elegido); debajo, los botones
        sqS = Math.max(40, Math.min(H - 28, (int) ((W - 40) * 0.36F)));
        sqX = X + 8;
        sqY = top + 1;
        PadUi.box(g, sqX - 1, sqY - 1, sqS + 2, sqS + 2, PadUi.INK);
        for (int i = 0; i < sqS; i++) {
            float s = i / (float) (sqS - 1);
            int c = java.awt.Color.HSBtoRGB(hue, s, 1F) | 0xFF000000;
            g.fillGradient(sqX + i, sqY, sqX + i + 1, sqY + sqS, c, 0xFF000000);
        }
        int cx = sqX + Math.round(sat * (sqS - 1)), cy = sqY + Math.round((1 - val) * (sqS - 1));
        ring(g, cx, cy, val > 0.55F && sat < 0.5F ? 0xFF18265C : 0xFFFFFFFF);
        hueX = sqX + sqS + 5;
        PadUi.box(g, hueX - 1, sqY - 1, 12, sqS + 2, PadUi.INK);
        for (int j = 0; j < sqS; j++) {
            int c = java.awt.Color.HSBtoRGB(j / (float) (sqS - 1), 1F, 1F) | 0xFF000000;
            g.fill(hueX, sqY + j, hueX + 10, sqY + j + 1, c);
        }
        int hy = sqY + Math.round(hue * (sqS - 1));
        g.fill(hueX - 3, hy - 1, hueX + 13, hy + 2, 0xFF18265C);
        g.fill(hueX - 2, hy, hueX + 12, hy + 1, 0xFFFFFFFF);
        if (rainbow) {
            // el arcoíris no usa colores: el cuadro, apagado
            g.fill(sqX, sqY, hueX + 10, sqY + sqS, 0xA0E6ECF5);
            PadUi.text(g, "Sin colores", sqX + (sqS - PadUi.font().width("Sin colores")) / 2, sqY + sqS / 2 - 4, 0xFF7E8CA8);
        }
        int by = Math.min(Y + H - 18, sqY + sqS + 4);
        int gw = PadUi.buttonWidth("GUARDAR"), cw = PadUi.buttonWidth("CANCELAR");
        button(g, sqX, by, gw, "GUARDAR", PadView.GREEN, mx, my, this::save);
        button(g, sqX + gw + 4, by, cw, "CANCELAR", PadView.RED, mx, my, () -> pad.back());
        // a la derecha: el efecto, los colores, la vista previa, los colores de Paint y el código
        int px = hueX + 18, pw = X + W - 8 - px;
        int y = top;
        int pillW = (pw - 3) / 2;
        for (int i = 0; i < MODES.length; i++) {
            int bx = px + (i % 2) * (pillW + 3), byy = y + (i / 2) * 13;
            pill(g, bx, byy, pillW, 11, MODE_NAMES[i], mode.equals(MODES[i]), mx, my, acc);
            final String m = MODES[i];
            buttons.add(new int[] {bx, byy, pillW, 11});
            actions.add(() -> setMode(m));
        }
        y += 28;
        // las casillas de color (1 en sólido, 2 o 3 en degradado y onda) y la velocidad (arcoíris y onda)
        int x = px;
        if (!rainbow) {
            for (int i = 0; i < count; i++) {
                boolean on = i == slot;
                g.fill(x - 1, y - 1, x + 17, y + 13, on ? 0xFFFFD650 : 0xFF7C8AA8);
                g.fill(x, y, x + 16, y + 12, 0xFF000000 | cols[i]);
                if (count > 1) PadUi.text(g, String.valueOf(i + 1), x + 6, y + 2, lum(cols[i]) > 140 ? 0xFF18265C : 0xFFFFFFFF);
                final int k = i;
                buttons.add(new int[] {x - 1, y - 1, 18, 14});
                actions.add(() -> pickSlot(k));
                x += 20;
            }
            if (count > 1 && count < 3) {
                tiny(g, x, y, "+", mx, my, () -> {
                    count = 3;
                    pickSlot(2);
                });
                x += 14;
            } else if (count == 3) {
                tiny(g, x, y, "−", mx, my, () -> {
                    count = 2;
                    if (slot > 1) pickSlot(1);
                });
                x += 14;
            }
        }
        if (rainbow || mode.equals(MODES[3])) {
            int sx = Math.max(x + 4, px + pw - 58);
            PadUi.text(g, "Vel.", sx, y + 2, 0xFF7E8CA8);
            tiny(g, sx + 22, y, "−", mx, my, () -> speed = Math.max(1, speed - 1));
            PadUi.text(g, String.valueOf(speed), sx + 37, y + 2, PadUi.TEXT);
            tiny(g, sx + 45, y, "+", mx, my, () -> speed = Math.min(5, speed + 1));
        }
        y += 16;
        // la vista previa animada: sobre oscuro (como el chat y encima de las cabezas) y sobre claro (como el pad)
        String tag = label.isEmpty() ? "[TAG]" : "[" + label + "]";
        g.fill(px, y, px + pw / 2, y + 14, 0xFF20242C);
        g.fill(px + pw / 2, y, px + pw, y + 14, 0xFFF2F5FA);
        net.minecraft.network.chat.Component shown = net.tierrasfantasticas.tfclient.clans.ClanTagFx.component(tag, fx(),
                net.tierrasfantasticas.tfclient.clans.client.ClanClient.now(), false);
        int tw = PadUi.font().width(shown);
        g.drawString(PadUi.font(), shown, px + (pw / 2 - tw) / 2, y + 3, 0xFFFFFFFF, true);
        g.drawString(PadUi.font(), shown, px + pw / 2 + (pw / 2 - tw) / 2, y + 3, 0xFFFFFFFF, false);
        y += 17;
        // los 28 colores básicos
        if (!rainbow) {
            int sw = Math.max(5, Math.min(9, (pw - 13) / 14));
            for (int i = 0; i < PAINT.length; i++) {
                int sx = px + (i % 14) * (sw + 1), sy = y + (i / 14) * (sw + 1);
                int c = PAINT[i];
                boolean on = c == cols[slot];
                boolean hover = PadUi.inside(mx, my, sx, sy, sw, sw);
                if (hover) pad.hover("§paint" + i);
                g.fill(sx - 1, sy - 1, sx + sw + 1, sy + sw + 1, on ? 0xFFFFD650 : hover ? 0xFF18265C : 0xFF7C8AA8);
                g.fill(sx, sy, sx + sw, sy + sw, 0xFF000000 | c);
                buttons.add(new int[] {sx, sy, sw, sw});
                actions.add(() -> setRgb(c));
            }
            y += 2 * (sw + 1) + 3;
            // el código (se puede escribir)
            int fw = Math.min(pw, 100);
            PadSkin.field(g, px, y, fw, editing ? "#" + hex : "", editing ? "" : "#" + hex + " (escríbelo)", acc);
            buttons.add(new int[] {px, y, fw, 16});
            actions.add(() -> {
                editing = true;
                hex = "";
            });
        } else {
            PadUi.text(g, PadUi.fitEnd("Todos los colores, corriendo.", pw), px, y + 2, 0xFF7E8CA8);
            PadUi.text(g, PadUi.fitEnd("En el chat y el TAB sale quieto.", pw), px, y + 13, 0xFF7E8CA8);
        }
    }

    private static int lum(int rgb) {
        return ((rgb >> 16 & 255) * 3 + (rgb >> 8 & 255) * 6 + (rgb & 255)) / 10;
    }

    /** Una pastilla de modo (la elegida, llena del color de la app). */
    private void pill(GuiGraphics g, int x, int y, int w, int h, String text, boolean on, double mx, double my, int acc) {
        boolean hover = PadUi.inside(mx, my, x, y, w, h);
        if (hover) pad.hover("§modo" + text);
        int bg = on ? 0xFF000000 | PadSkin.darken(acc, 0.1F) : hover ? 0xFFDCE8F6 : 0xFFEEF3FA;
        g.fill(x, y, x + w, y + h, on ? 0xFF18265C : 0xFF9AA8C4);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        int tw = PadUi.font().width(text);
        float k = tw > w - 4 ? (w - 4) / (float) tw : 1F;
        g.pose().pushPose();
        g.pose().translate(x + (w - tw * k) / 2F, y + (h - 8 * k) / 2F + 0.5F, 0);
        g.pose().scale(k, k, 1);
        PadUi.text(g, text, 0, 0, on ? 0xFFFFFFFF : PadUi.TEXT);
        g.pose().popPose();
    }

    /** Un botoncito cuadrado («+», «−»). */
    private void tiny(GuiGraphics g, int x, int y, String text, double mx, double my, Runnable action) {
        boolean hover = PadUi.inside(mx, my, x, y, 12, 12);
        if (hover) pad.hover("§tiny" + text + x);
        g.fill(x, y, x + 12, y + 12, hover ? 0xFF18265C : 0xFF7C8AA8);
        g.fill(x + 1, y + 1, x + 11, y + 11, hover ? 0xFFDCE8F6 : 0xFFF4F7FC);
        PadUi.text(g, text, x + (12 - PadUi.font().width(text)) / 2, y + 2, PadUi.TEXT);
        buttons.add(new int[] {x, y, 12, 12});
        actions.add(action);
    }

    private static void ring(GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx - 3, cy - 1, cx + 4, cy + 2, 0x8018265C);
        g.fill(cx - 1, cy - 3, cx + 2, cy + 4, 0x8018265C);
        g.fill(cx - 3, cy, cx - 1, cy + 1, color);
        g.fill(cx + 2, cy, cx + 4, cy + 1, color);
        g.fill(cx, cy - 3, cx + 1, cy - 1, color);
        g.fill(cx, cy + 2, cx + 1, cy + 4, color);
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
