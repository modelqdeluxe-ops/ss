package net.tierrasfantasticas.tfclient.pad;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * La portada: 20 apps en dos páginas de 5x2, fichas de 38 con su icono de 32 y el nombre debajo; los puntos de abajo
 * dicen en qué página estás. Se pasa de página con la rueda, las flechas, arrastrando o pulsando los puntos, y la
 * página entra deslizándose. Mismas posiciones que tools/pad/build_pad.py.
 */
final class PadHomePage extends PadPage {
    record App(String id, String name) {}

    static final List<App> APPS = List.of(
            new App("oficios", "OFICIOS"), new App("misiones", "MISIONES"), new App("cazas", "CAZAS"),
            new App("tienda", "TIENDA"), new App("gts", "GTS"),
            new App("monedero", "MONEDERO"), new App("clanes", "CLANES"), new App("viajes", "VIAJES"),
            new App("explorar", "EXPLORAR"), new App("kits", "KITS"),
            new App("comunidad", "COMUNIDAD"), new App("camara", "CÁMARA"), new App("jugadores", "JUGADORES"),
            new App("ranking", "RANKING"), new App("titulos", "TÍTULOS"),
            new App("armario", "ARMARIO"), new App("efectos", "EFECTOS"), new App("rango", "MI RANGO"),
            new App("protecciones", "PROTECCIÓN"), new App("ayuda", "AYUDA"));
    static final int PER_PAGE = 10;
    static final int PAGES = (APPS.size() + PER_PAGE - 1) / PER_PAGE;
    private static final int[] COLS = {84, 140, 196, 252, 308};
    private static final int[] ROWS = {82, 136};
    private static final int TILE = 38;
    private static final int SLIDE_MS = 220;

    /** Se recuerda la página entre aperturas del pad. */
    private static int page;
    private int from = -1;
    private long slideAt;
    private double dragX = Double.NaN;

    PadHomePage(TFPadScreen pad) {
        super(pad, "home");
    }

    @Override
    String title() {
        return "TF PAD";
    }

    private void go(int to) {
        to = Math.max(0, Math.min(PAGES - 1, to));
        if (to == page) return;
        from = page;
        page = to;
        slideAt = System.currentTimeMillis();
        pad.sound("page", 0.6F);
    }

    @Override
    void render(GuiGraphics g, double mx, double my, float partial) {
        float p = from < 0 ? 1 : Math.min(1F, (System.currentTimeMillis() - slideAt) / (float) SLIDE_MS);
        float ease = 1 - (1 - p) * (1 - p) * (1 - p);
        if (p >= 1) from = -1;
        int dir = from < 0 ? 0 : (page > from ? 1 : -1);
        int shift = Math.round((1 - ease) * 290) * dir;
        pad.scissor(g, 56, 80, 280, 108);
        if (from >= 0) drawPage(g, from, shift - 290 * dir, Double.NaN, Double.NaN);
        drawPage(g, page, shift, mx, my);
        g.disableScissor();
        // puntos de página
        for (int i = 0; i < PAGES; i++) {
            int x = 196 - (PAGES * 8 - 4) / 2 + i * 8;
            g.fill(x, 189, x + 4, 193, PadUi.NAVY);
            g.fill(x + 1, 190, x + 3, 192, i == page ? 0xFFFFE680 : 0xFF96CEF6);
        }
    }

    private void drawPage(GuiGraphics g, int index, int dx, double mx, double my) {
        for (int i = 0; i < PER_PAGE; i++) {
            int n = index * PER_PAGE + i;
            if (n >= APPS.size()) break;
            App app = APPS.get(n);
            int cx = COLS[i % 5] + dx, ty = ROWS[i / 5];
            boolean hover = from < 0 && PadUi.inside(mx, my, cx - TILE / 2, ty, TILE, TILE);
            if (hover) pad.hover(app.id);
            pad.blit(g, hover ? "tile_h" : "tile", cx - TILE / 2, ty + (hover ? -1 : 0));
            pad.blit(g, "icon_" + app.id, cx - 16, ty + 3 + (hover ? -1 : 0));
            PadFont.drawCentered(g, app.name, cx, ty + 41, 0xFFFFFF, true);
        }
    }

    private App at(double mx, double my) {
        for (int i = 0; i < PER_PAGE; i++) {
            int n = page * PER_PAGE + i;
            if (n >= APPS.size()) break;
            if (PadUi.inside(mx, my, COLS[i % 5] - TILE / 2, ROWS[i / 5], TILE, TILE)) return APPS.get(n);
        }
        return null;
    }

    @Override
    boolean click(double mx, double my, int button) {
        if (button != 0) return false;
        for (int i = 0; i < PAGES; i++) {
            int x = 196 - (PAGES * 8 - 4) / 2 + i * 8;
            if (PadUi.inside(mx, my, x - 2, 187, 8, 8)) {
                go(i);
                return true;
            }
        }
        App app = at(mx, my);
        if (app != null && from < 0) {
            pad.openApp(app.id);
            return true;
        }
        dragX = mx;
        return false;
    }

    /** Arrastrar a un lado pasa de página (como en un móvil). */
    void released(double mx) {
        if (!Double.isNaN(dragX)) {
            double d = mx - dragX;
            if (d < -40) go(page + 1);
            else if (d > 40) go(page - 1);
        }
        dragX = Double.NaN;
    }

    @Override
    boolean scroll(double mx, double my, double delta) {
        go(page + (delta < 0 ? 1 : -1));
        return true;
    }

    @Override
    boolean key(int key, int scan, int mods) {
        if (key == 262) { // derecha
            go(page + 1);
            return true;
        }
        if (key == 263) { // izquierda
            go(page - 1);
            return true;
        }
        return false;
    }

    static ResourceLocation icon(String app) {
        return new ResourceLocation(TFClient.MOD_ID, "textures/gui/pad/icon_" + app + ".png");
    }
}
