package net.tierrasfantasticas.tfclient.pad;

import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.tierrasfantasticas.tfclient.pad.music.MusicPlayer;

/**
 * La portada: las apps en una rejilla simétrica de fichas (23 apps: 8 + 8 + 7 a 1080p, ver {@link #layout}) que baja
 * con la rueda o arrastrando si no cabe, como en un móvil. Las fichas van a la escala grande del pad ({@link TFPadScreen#bs}) y los nombres a la
 * escala normal, para que haya aire entre ellas. Al pasar el ratón, la ficha se eleva y su icono da un salto con
 * aplastado y estirado (como en los juegos de rol); algunas tienen su gesto: el pico golpea, la ballesta retrocede,
 * el estandarte y la percha se mecen, la cámara hace clic y Música late con la canción. Sin partículas ni destellos.
 * Las apps que el staff apaga no salen; con el pad de administrador, la portada tiene las apps de configurar.
 */
final class PadHomePage extends PadPage {
    record App(String id, String name, String icon) {
        App(String id, String name) {
            this(id, name, id);
        }
    }

    static final List<App> APPS = List.of(
            new App("musica", "MÚSICA"), new App("oficios", "OFICIOS"), new App("misiones", "MISIONES"),
            new App("pase", "TF PASS"), new App("gachapon", "GACHAPÓN"), new App("cazas", "CAZAS"),
            new App("recompensas", "RECOMPENSAS"), new App("tienda", "TIENDA"), new App("gts", "GTS"),
            new App("monedero", "MONEDERO"), new App("viajes", "VIAJES"), new App("hogares", "HOGARES"), new App("kits", "KITS"),
            new App("protecciones", "PROTECCIÓN"), new App("clanes", "CLANES"), new App("jugadores", "JUGADORES"),
            new App("comunidad", "COMUNIDAD"), new App("camara", "CÁMARA"), new App("ranking", "RANKING"),
            new App("armario", "ARMARIO"), new App("efectos", "EFECTOS"), new App("rango", "MI RANGO"),
            new App("web", "WEB"));

    /** Las del pad de administrador (las dibuja el servidor, solo para el staff). */
    static final List<App> ADMIN_APPS = List.of(
            new App("a_apps", "APPS", "admin"), new App("a_tienda", "TIENDA", "tienda"), new App("a_kits", "KITS", "kits"),
            new App("a_recompensas", "RECOMPENSAS", "recompensas"), new App("a_viajes", "VIAJES", "viajes"),
            new App("a_gts", "GTS", "gts"), new App("a_comunidad", "COMUNIDAD", "comunidad"),
            new App("a_oficios", "OFICIOS", "oficios"), new App("a_cazas", "CAZAS", "cazas"), new App("a_ajustes", "AJUSTES", "admin"));

    /** Ficha (textura de 40x40 + 2 de sombra) e icono (32x32, a 4 del borde). */
    private static final int TILE_TEX = 40, ICON_OFF = 4;
    /** Alto del nombre (letra pixel a escala normal) y aire mínimo entre filas. */
    private static final int LABEL_H = 11, MIN_GAP = 4;
    /** Se recuerda dónde se quedó la rejilla entre aperturas del pad. */
    private static int scroll;
    private int contentH;
    private String hovered;
    private long hoverStart;

    PadHomePage(TFPadScreen pad) {
        super(pad, "home");
    }

    @Override
    String title() {
        return pad.admin ? "PAD ADMIN" : "TF PAD";
    }

    @Override
    boolean scaled() {
        return false;
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

    // ---------------------------------------------------------------------------------------------------------------
    // Geometría (unidades)
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * La rejilla (1.3.38), simétrica: todas las fichas iguales, el mismo hueco entre ellas a lo ancho y a lo alto,
     * centrada en todo el cristal y con las filas repartidas (23 apps en 8 + 8 + 7, no 7 + 7 + 7 + 2); la última fila,
     * si tiene menos, va centrada. Las fichas van a la escala grande; si así no caben todas las filas, a una escala
     * entera menor (siempre nítidas). ts: unidades por píxel de la textura de la ficha.
     */
    private record Grid(float ts, int tile, int cols, int rows, int gap, int x0, int top, int pitchX, int pitchY, int contentH) {}

    private Grid grid;

    private Grid layout(int n) {
        int maxLabel = 0;
        for (App a : apps()) maxLabel = Math.max(maxLabel, PadFont.width(a.name));
        int room = SW - 8;
        Grid best = null;
        // de la escala grande hacia abajo, en pasos enteros de píxeles reales por píxel de textura
        int k0 = pad.bigPx(), kMin = Math.max(1, pad.cs());
        for (int k = k0; k >= kMin; k--) {
            float ts = k / (float) pad.cs();
            int t = Math.round(TILE_TEX * ts);
            int minGap = Math.max(Math.round(8 * ts), maxLabel - t + 6); // los nombres nunca se tocan
            int maxCols = Math.max(1, Math.min(9, (room + minGap) / (t + minGap)));
            int rows = Math.max(1, (n + maxCols - 1) / maxCols);
            int cols = Math.max(1, (n + rows - 1) / rows); // filas repartidas
            int cellH = t + LABEL_H;
            int gapV = (H - rows * cellH) / (rows + 1);
            int gapH = (room - cols * t) / (cols + 1);
            boolean fits = gapV >= MIN_GAP;
            // el mismo hueco a lo ancho y a lo alto; si a lo alto no hay tanto sitio, las filas se juntan un poco más
            int gapX = Math.max(minGap, Math.min(gapH, Math.round(26 * ts)));
            int gapY = fits ? Math.min(gapX, gapV) : MIN_GAP;
            gapX = Math.max(minGap, Math.min(gapX, gapY)); // a lo ancho, como mucho lo que haya a lo alto
            int gap = gapX;
            int pitchX = t + gapX, pitchY = cellH + gapY;
            int gridW = cols * t + (cols - 1) * gapX;
            int contentH = rows * cellH + (rows - 1) * gapY;
            int x0 = (SW - gridW) / 2;
            int top = fits ? Y + (H - contentH) / 2 : Y + 2;
            // el logo de arriba a la izquierda: si la primera fila lo fuera a pisar, la rejilla baja un poco
            while (pad.logoRight(top) + 2 > x0 && top + contentH < Y + H) top++;
            Grid gr = new Grid(ts, t, cols, rows, gap, x0, top, pitchX, pitchY, contentH + 4);
            if (fits || k == kMin) return gr;
            if (best == null) best = gr;
        }
        return best;
    }

    private int tile() {
        return grid.tile;
    }

    /** Esquina de la ficha i (antes del desplazamiento); la última fila, si está incompleta, centrada. */
    private int[] at(int i, int n) {
        Grid gr = grid;
        int row = i / gr.cols, col = i % gr.cols;
        int inRow = Math.min(gr.cols, n - row * gr.cols);
        int x = gr.x0 + (gr.cols - inRow) * gr.pitchX / 2 + col * gr.pitchX;
        return new int[] {x, gr.top + row * gr.pitchY};
    }

    private int cellH() {
        return grid.pitchY;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Dibujo
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    void render(GuiGraphics g, double mx, double my, float partial) {
        long now = System.currentTimeMillis();
        List<App> apps = apps();
        grid = layout(apps.size());
        contentH = grid.contentH;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - H)));
        int t = tile();
        float ts = grid.ts;
        // la rejilla va centrada en todo el cristal: recorta y recibe el ratón en todo su ancho
        boolean inside = PadUi.inside(mx, my, 2, Y, SW - 4, H);
        String hoverNow = null;
        int hoverIndex = -1;
        for (int i = 0; i < apps.size(); i++) {
            int[] p = at(i, apps.size());
            if (inside && PadUi.inside(mx, my, p[0], p[1] - scroll, t, t + LABEL_H)) {
                hoverNow = apps.get(i).id;
                hoverIndex = i;
            }
        }
        if (hoverNow != null && !hoverNow.equals(hovered)) hoverStart = now;
        hovered = hoverNow;
        if (hoverNow != null) pad.hover(hoverNow);

        pad.scissor(g, 2, Y, SW - 4, H);
        for (int i = 0; i < apps.size(); i++) {
            App app = apps.get(i);
            int[] p = at(i, apps.size());
            int tx = p[0], ty = p[1] - scroll;
            if (ty + cellH() < Y || ty > Y + H) continue;
            boolean hover = i == hoverIndex;
            float ht = hover ? (now - hoverStart) / 1000F : -1F;
            boolean playing = app.id.equals("musica") && MusicPlayer.playing();
            // al pasar el ratón la ficha sube (con una ease rápida) y deja su sombra debajo
            float rise = hover && PadSettings.animations ? Math.min(1F, ht / 0.12F) : 0F;
            int lift = -Math.round(ts * 2 * ease(rise));
            if (lift != 0) g.fill(tx + Math.round(3 * ts), ty + t, tx + t - Math.round(3 * ts), ty + t + Math.max(1, Math.round(ts)), 0x5518265C);
            g.pose().pushPose();
            g.pose().translate(tx, ty + lift, 0);
            g.pose().scale(ts, ts, 1);
            pad.blit(g, hover ? "tile_h" : "tile", 0, 0);
            g.pose().popPose();
            float anim = !PadSettings.animations ? -1F : hover ? ht : playing ? (now % 100000) / 1000F : -1F;
            drawIcon(g, app.icon, tx, ty + lift, anim, hover, ts);
            PadFont.drawCentered(g, app.name, tx + t / 2, ty + t + 2, hover ? 0xFFE680 : 0xFFFFFF, true);
        }
        pad.noScissor(g);
        if (contentH > H) {
            int bx = SW - 5, bh = H - 4;
            g.fill(bx, Y + 2, bx + 2, Y + 2 + bh, 0x5518265C);
            int th = Math.max(10, bh * H / contentH);
            int ty = Y + 2 + (bh - th) * scroll / Math.max(1, contentH - H);
            g.fill(bx, ty, bx + 2, ty + th, 0xFFF6B628);
        }
    }

    private static float ease(float x) {
        x = Math.max(0, Math.min(1, x));
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }

    /**
     * El icono con su animación. t: segundos desde que el ratón entró (negativo: quieto). Primero un salto con
     * aplastado y estirado; luego un respirar suave, o el gesto propio de la app cada poco.
     */
    private void drawIcon(GuiGraphics g, String icon, int x, int y, float t, boolean hover, float bs) {
        g.pose().pushPose();
        g.pose().translate(x + ICON_OFF * bs, y + ICON_OFF * bs, 0);
        g.pose().scale(bs, bs, 1);
        if (t >= 0) animate(g, icon, t, hover);
        pad.blit(g, "icon_" + icon, 0, 0);
        g.pose().popPose();
    }

    /** Transformaciones por app (coordenadas del icono de 32x32; el «suelo» es y = 30). */
    private void animate(GuiGraphics g, String icon, float t, boolean hover) {
        if (icon.equals("musica") && !hover) { // sonando (sin ratón): late con la canción
            beat(g, t);
            return;
        }
        if (t < 0.5F) { // el salto de bienvenida
            hop(g, t / 0.5F);
            return;
        }
        float c = t - 0.5F;
        switch (icon) {
            case "oficios" -> { // el pico toma impulso y golpea, cada 1,4 s
                float k = (c % 1.4F) / 1.4F;
                float ang = k < 0.45F ? -22 * smooth(k / 0.45F) : k < 0.52F ? -22 + 34 * ((k - 0.45F) / 0.07F)
                        : k < 0.62F ? 12 : 12 * (1 - smooth((k - 0.62F) / 0.38F));
                rotate(g, 7, 27, ang);
            }
            case "cazas" -> { // la ballesta dispara y retrocede
                float k = (c % 1.6F) / 1.6F;
                float back = k < 0.06F ? k / 0.06F : k < 0.5F ? 1 - smooth((k - 0.06F) / 0.44F) : 0;
                g.pose().translate(-2.5F * back, 0.8F * back, 0);
            }
            case "clanes" -> rotate(g, 16, 3, (float) Math.sin(c * 2.6) * 6); // el estandarte se mece desde la barra
            case "armario" -> rotate(g, 16, 2, (float) Math.sin(c * 2.8) * 6); // la percha se mece del gancho
            case "camara" -> { // clic del disparador cada 1,8 s
                float k = (c % 1.8F) / 1.8F;
                if (k < 0.08F) scale(g, 16, 16, 0.94F + 0.06F * (k / 0.08F), 0.94F + 0.06F * (k / 0.08F));
            }
            case "musica" -> beat(g, c);
            default -> g.pose().translate(0, -(float) (0.5 + 0.5 * Math.sin(c * 4)) * 1.2F, 0); // respira
        }
    }

    /** Salto con anticipación: se aplasta, sube estirado, cae y se asienta (p de 0 a 1). */
    private static void hop(GuiGraphics g, float p) {
        float sx = 1, sy = 1, dy = 0;
        if (p < 0.18F) { // se agacha
            float k = smooth(p / 0.18F);
            sx = 1 + 0.10F * k;
            sy = 1 - 0.12F * k;
        } else if (p < 0.62F) { // sube y baja estirado
            float k = (p - 0.18F) / 0.44F;
            dy = -4.5F * (float) Math.sin(k * Math.PI);
            sx = 0.94F + 0.06F * Math.abs(2 * k - 1);
            sy = 1.08F - 0.08F * Math.abs(2 * k - 1);
        } else if (p < 0.8F) { // aterriza aplastado
            float k = (float) Math.sin((p - 0.62F) / 0.18F * Math.PI);
            sx = 1 + 0.08F * k;
            sy = 1 - 0.09F * k;
        }
        g.pose().translate(0, dy, 0);
        scale(g, 16, 30, sx, sy);
    }

    private static void beat(GuiGraphics g, float t) {
        float lv = MusicPlayer.playing() ? MusicPlayer.levelAt(MusicPlayer.position()) : 0.5F + 0.5F * (float) Math.sin(t * 7);
        float k = 1 + 0.07F * lv;
        scale(g, 16, 30, k, k);
    }

    private static float smooth(float x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    private static void rotate(GuiGraphics g, float cx, float cy, float degrees) {
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(degrees));
        g.pose().translate(-cx, -cy, 0);
    }

    private static void scale(GuiGraphics g, float cx, float cy, float sx, float sy) {
        g.pose().translate(cx, cy, 0);
        g.pose().scale(sx, sy, 1);
        g.pose().translate(-cx, -cy, 0);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Ratón y teclado
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    boolean click(double mx, double my, int button) {
        if (button != 0 || !PadUi.inside(mx, my, 2, Y, SW - 4, H)) return false;
        List<App> apps = apps();
        if (grid == null) return false;
        int t = tile();
        for (int i = 0; i < apps.size(); i++) {
            int[] p = at(i, apps.size());
            if (PadUi.inside(mx, my, p[0], p[1] - scroll, t, t + LABEL_H)) {
                pad.openApp(apps.get(i).id);
                return true;
            }
        }
        return false;
    }

    @Override
    boolean scroll(double mx, double my, double delta) {
        if (grid != null) scroll -= (int) Math.signum(delta) * cellH() / 2;
        return true;
    }

    @Override
    boolean drag(double mx, double my, double dy) {
        scroll -= (int) Math.round(dy);
        return true;
    }

    @Override
    boolean key(int key, int scan, int mods) {
        if (grid == null) return false;
        if (key == 264) { // abajo
            scroll += cellH() / 2;
            return true;
        }
        if (key == 265) { // arriba
            scroll -= cellH() / 2;
            return true;
        }
        return false;
    }
}
