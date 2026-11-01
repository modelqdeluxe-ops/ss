/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;

/**
 * (1.3.155) Ajustes › Fondo del menú: las miniaturas de todos los fondos (el del tema, los tuyos de
 * .minecraft/tfclient/fondos, los dibujados y los colores); al pulsar una se pone en el menú principal. Arriba, CARPETA
 * (la abre para meter PNG) y RECARGAR. Atrás vuelve a Ajustes.
 */
final class PadWallpaperPage extends PadPage {
    private static final int COLS = 4, GAP = 5, LABEL = 10;
    private float scroll;
    private int contentH;
    private final List<int[]> buttons = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    /** Para la prueba en el juego: elige este fondo al abrir. */
    static String testPick;

    PadWallpaperPage(TFPadScreen pad) {
        super(pad, "ajustes");
        PadWallpaper.files(true);
    }

    @Override
    String title() {
        return "FONDO DEL PAD";
    }

    @Override
    PadPage backTo() {
        return new PadInfoPages.Ajustes(pad);
    }

    @Override
    void render(GuiGraphics g, double mx, double my, float partial) {
        buttons.clear();
        actions.clear();
        if (testPick != null) {
            pick(testPick);
            testPick = null;
        }
        int acc = PadSkin.accent(app);
        PadSkin.window(g, X, Y, W, H, acc);
        // la barra de arriba: qué hay y los dos botones
        int top = Y + 5;
        String cur = name(PadSettings.wallpaper);
        PadUi.title(g, "Fondo: " + cur, X + 8, top + 4, W - 140, PadSkin.darken(acc, 0.7F));
        int rw = PadUi.buttonWidth("RECARGAR"), fw = PadUi.buttonWidth("CARPETA");
        button(g, X + W - 8 - rw, top, rw, "RECARGAR", PadView.BLUE, mx, my, () -> {
            PadWallpaper.reload();
            pad.showNotice("Fondos recargados.");
        });
        button(g, X + W - 8 - rw - 4 - fw, top, fw, "CARPETA", PadView.GREEN, mx, my, () -> {
            try {
                Files.createDirectories(PadWallpaper.dir());
            } catch (Exception ignored) {
                // se abre igual
            }
            Util.getPlatform().openFile(PadWallpaper.dir().toFile());
        });
        // la rejilla de miniaturas (16:9 como el cristal, algo más ancha)
        // (abajo, el aviso de la carpeta si aún no hay fondos tuyos: la rejilla acaba antes para no taparlo)
        boolean hint = PadWallpaper.files(false).isEmpty();
        int gx = X + 8, gy = top + 22, gw = W - 16, gh = Y + H - (hint ? 16 : 6) - gy;
        int tw = (gw - GAP * (COLS - 1)) / COLS, th = Math.round(tw * 0.46F);
        List<PadWallpaper.Option> all = PadWallpaper.all();
        int rows = (all.size() + COLS - 1) / COLS;
        contentH = rows * (th + LABEL + GAP);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - gh)));
        pad.scissor(g, gx - 2, gy, gw + 4, gh);
        for (int i = 0; i < all.size(); i++) {
            PadWallpaper.Option o = all.get(i);
            int x = gx + (i % COLS) * (tw + GAP), y = gy + (i / COLS) * (th + LABEL + GAP) - Math.round(scroll);
            if (y + th + LABEL < gy || y > gy + gh) continue;
            boolean sel = o.id().equals(PadSettings.wallpaper) || o.theme() && !PadWallpaper.active();
            boolean hover = PadUi.inside(mx, my, x, y, tw, th + LABEL) && PadUi.inside(mx, my, gx, gy, gw, gh);
            if (hover) pad.hover("§fondo" + i);
            // los recuadros primero (PadUi.box rellena): luego la miniatura encima
            if (sel) PadUi.box(g, x - 2, y - 2, tw + 4, th + 4, PadUi.GOLD_LO);
            PadUi.box(g, x - 1, y - 1, tw + 2, th + 2, sel ? PadUi.GOLD_LO : hover ? PadUi.INK : 0xFF9AA6C4);
            if (o.theme()) {
                PadSettings.Theme t = PadSettings.theme();
                g.fillGradient(x, y, x + tw, y + th, 0xFF000000 | t.top(), 0xFF000000 | t.bottom());
                PadUi.texture(g, x, y, tw, th);
            } else if (!PadWallpaper.draw(g, o, x, y, x + tw, y + th)) {
                g.fill(x, y, x + tw, y + th, 0xFF2A3554);
                if (o.file() != null && PadWallpaper.broken(o.file())) {
                    PadUi.text(g, PadUi.fitEnd("No es PNG", tw - 4), x + 3, y + th / 2 - 4, 0xFFFF9090);
                } else {
                    PadUi.spinner(g, x + tw / 2, y + th / 2 - 4);
                }
            }
            if (sel) {
                int cx = x + tw - 9, cy = y + 3;
                g.fill(cx - 1, cy - 1, cx + 8, cy + 8, 0xFF2EAF5C);
                for (int d = 0; d < 2; d++) g.fill(cx + 1 + d, cy + 3 + d, cx + 2 + d, cy + 4 + d, 0xFFFFFFFF);
                for (int d = 0; d < 4; d++) g.fill(cx + 3 + d, cy + 4 - d, cx + 4 + d, cy + 5 - d, 0xFFFFFFFF);
            }
            String label = (o.file() != null ? "Tuyo: " : "") + o.name();
            PadUi.text(g, PadUi.fitEnd(label, tw), x, y + th + 2, sel ? PadUi.TEXT : PadUi.MUTED);
            int[] r = {x, Math.max(y, gy), tw, Math.min(y + th + LABEL, gy + gh) - Math.max(y, gy)};
            if (r[3] > 0) {
                buttons.add(r);
                String id = o.id();
                actions.add(() -> pick(id));
            }
        }
        pad.noScissor(g);
        if (hint) {
            String note = PadUi.fitEnd("Mete tus PNG en .minecraft/tfclient/fondos y pulsa RECARGAR.", W - 20);
            PadUi.text(g, note, X + (W - PadUi.font().width(note)) / 2, Y + H - 12, PadUi.MUTED);
        }
    }

    private static String name(String id) {
        for (PadWallpaper.Option o : PadWallpaper.all()) if (o.id().equals(id)) return o.name();
        return "El del tema";
    }

    private void pick(String id) {
        PadSettings.wallpaper = id;
        PadSettings.save();
    }

    private void button(GuiGraphics g, int x, int y, int w, String label, int style, double mx, double my, Runnable action) {
        boolean hover = PadUi.inside(mx, my, x, y, w, 16);
        if (hover) pad.hover("§" + label);
        PadSkin.button(g, x, y, w, label, style, hover, true, false);
        buttons.add(new int[] {x, y, w, 16});
        actions.add(action);
    }

    @Override
    boolean click(double mx, double my, int button) {
        if (button != 0) return false;
        for (int i = 0; i < buttons.size(); i++) {
            int[] b = buttons.get(i);
            if (PadUi.inside(mx, my, b[0], b[1], b[2], b[3])) {
                pad.sound("select", 0.75F);
                actions.get(i).run();
                return true;
            }
        }
        return false;
    }

    @Override
    boolean scroll(double mx, double my, double delta) {
        scroll -= (float) delta * 24;
        return true;
    }

    @Override
    boolean drag(double mx, double my, double dy) {
        scroll -= (float) dy;
        return true;
    }
}
