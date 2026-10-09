package net.tierrasfantasticas.tfclient.pad;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/**
 * La portada: las apps en una rejilla de fichas (icono de 32 y el nombre debajo) que baja con la rueda o arrastrando,
 * como en un móvil. Las apps que el staff apaga en el pad de administrador no salen. Con el pad de administrador, la
 * portada tiene las apps de configurar el servidor.
 */
final class PadHomePage extends PadPage {
    record App(String id, String name, String icon) {
        App(String id, String name) {
            this(id, name, id);
        }
    }

    static final List<App> APPS = List.of(
            new App("oficios", "OFICIOS"), new App("misiones", "MISIONES"), new App("cazas", "CAZAS"),
            new App("tienda", "TIENDA"), new App("gts", "GTS"), new App("monedero", "MONEDERO"),
            new App("viajes", "VIAJES"), new App("hogares", "HOGARES"), new App("kits", "KITS"),
            new App("protecciones", "PROTECCIÓN"), new App("clanes", "CLANES"), new App("jugadores", "JUGADORES"),
            new App("comunidad", "COMUNIDAD"), new App("camara", "CÁMARA"), new App("ranking", "RANKING"),
            new App("armario", "ARMARIO"), new App("efectos", "EFECTOS"), new App("rango", "MI RANGO"),
            new App("ayuda", "AYUDA"));

    /** Las del pad de administrador (las dibuja el servidor, solo para el staff). */
    static final List<App> ADMIN_APPS = List.of(
            new App("a_apps", "APPS", "admin"), new App("a_tienda", "TIENDA", "tienda"), new App("a_kits", "KITS", "kits"),
            new App("a_viajes", "VIAJES", "viajes"), new App("a_gts", "GTS", "gts"), new App("a_comunidad", "COMUNIDAD", "comunidad"),
            new App("a_oficios", "OFICIOS", "oficios"), new App("a_ajustes", "AJUSTES", "admin"));

    private static final int TILE = 38, CELL_W = 64, CELL_H = 62;
    /** Se recuerda dónde se quedó la rejilla entre aperturas del pad. */
    private static int scroll;
    private int contentH;

    PadHomePage(TFPadScreen pad) {
        super(pad, "home");
    }

    @Override
    String title() {
        return pad.admin ? "PAD ADMIN" : "TF PAD";
    }

    List<App> apps() {
        if (pad.admin) return ADMIN_APPS;
        TFPadNet.State s = TFPadClient.state;
        if (s == null || s.disabled().isEmpty()) return APPS;
        List<App> out = new ArrayList<>();
        for (App a : APPS) if (!s.disabled().contains(a.id)) out.add(a);
        return out;
    }

    static App find(String id) {
        for (App a : APPS) if (a.id.equals(id)) return a;
        for (App a : ADMIN_APPS) if (a.id.equals(id)) return a;
        return null;
    }

    private int cols() {
        return Math.max(3, Math.min(8, W / CELL_W));
    }

    /** Esquina de la ficha i (antes del desplazamiento). */
    private int[] at(int i) {
        int cols = cols();
        int gridW = cols * CELL_W;
        int x0 = X + (W - gridW) / 2;
        return new int[] {x0 + (i % cols) * CELL_W + (CELL_W - TILE) / 2, Y + 4 + (i / cols) * CELL_H};
    }

    @Override
    void render(GuiGraphics g, double mx, double my, float partial) {
        List<App> apps = apps();
        int rows = (apps.size() + cols() - 1) / cols();
        contentH = rows * CELL_H + 4;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - H)));
        pad.scissor(g, X, Y, W, H);
        boolean inside = PadUi.inside(mx, my, X, Y, W, H);
        for (int i = 0; i < apps.size(); i++) {
            App app = apps.get(i);
            int[] p = at(i);
            int tx = p[0], ty = p[1] - scroll;
            if (ty + CELL_H < Y || ty > Y + H) continue;
            boolean hover = inside && PadUi.inside(mx, my, tx, ty, TILE, TILE);
            if (hover) pad.hover(app.id);
            int lift = hover ? -1 : 0;
            pad.blit(g, hover ? "tile_h" : "tile", tx, ty + lift);
            pad.blit(g, "icon_" + app.icon, tx + 3, ty + 3 + lift);
            PadFont.drawCentered(g, app.name, tx + TILE / 2, ty + 43, 0xFFFFFF, true);
        }
        pad.noScissor(g);
        if (contentH > H) {
            int bx = X + W - 3, bh = H - 4;
            g.fill(bx, Y + 2, bx + 2, Y + 2 + bh, 0x5518265C);
            int th = Math.max(10, bh * H / contentH);
            int ty = Y + 2 + (bh - th) * scroll / Math.max(1, contentH - H);
            g.fill(bx, ty, bx + 2, ty + th, 0xFFF6B628);
        }
    }

    @Override
    boolean click(double mx, double my, int button) {
        if (button != 0 || !PadUi.inside(mx, my, X, Y, W, H)) return false;
        List<App> apps = apps();
        for (int i = 0; i < apps.size(); i++) {
            int[] p = at(i);
            if (PadUi.inside(mx, my, p[0], p[1] - scroll, TILE, TILE)) {
                pad.openApp(apps.get(i).id);
                return true;
            }
        }
        return false;
    }

    @Override
    boolean scroll(double mx, double my, double delta) {
        scroll -= (int) Math.signum(delta) * 24;
        return true;
    }

    @Override
    boolean drag(double mx, double my, double dy) {
        scroll -= (int) Math.round(dy);
        return true;
    }

    @Override
    boolean key(int key, int scan, int mods) {
        if (key == 264) { // abajo
            scroll += 24;
            return true;
        }
        if (key == 265) { // arriba
            scroll -= 24;
            return true;
        }
        return false;
    }
}
