package net.tierrasfantasticas.tfclient.pad;

import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import net.tierrasfantasticas.tfclient.pad.music.MusicPlayer;

/**
 * La portada: las apps en una rejilla de fichas que baja con la rueda o arrastrando, como en un móvil. Todo va a la
 * escala grande del pad ({@link TFPadScreen#bs}: 1,5 a 1080p, fichas de 60 unidades). Cada ficha tiene su animación
 * RPG al pasar el ratón (el pico pica y saltan chispas, la moneda gira, sale humo de la chimenea…), con un halo y
 * partículas; de vez en cuando un destello cruza alguna ficha. Las apps que el staff apaga no salen; con el pad de
 * administrador, la portada tiene las apps de configurar el servidor.
 */
final class PadHomePage extends PadPage {
    record App(String id, String name, String icon) {
        App(String id, String name) {
            this(id, name, id);
        }
    }

    static final List<App> APPS = List.of(
            new App("musica", "MÚSICA"), new App("oficios", "OFICIOS"), new App("misiones", "MISIONES"),
            new App("cazas", "CAZAS"), new App("tienda", "TIENDA"), new App("gts", "GTS"), new App("monedero", "MONEDERO"),
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

    /** Ficha (textura de 40x40 + 2 de sombra) e icono (32x32, a 4 del borde). */
    private static final int TILE_TEX = 40, ICON_OFF = 4;
    /** Se recuerda dónde se quedó la rejilla entre aperturas del pad. */
    private static int scroll;
    private int contentH;
    private String hovered;
    private long hoverStart;
    private long lastFrame = System.nanoTime();
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();
    /** Destello que cruza una ficha al azar cada pocos segundos. */
    private int glintIndex = -1;
    private long glintStart, nextGlint = System.currentTimeMillis() + 1500;

    /** Partícula en unidades de pantalla (x, y relativos a la esquina de su ficha para que sigan al desplazar). */
    private static final class Particle {
        int app;
        float x, y, vx, vy, gravity, life, max, size;
        int color;
        boolean sparkle;
    }

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

    // ---------------------------------------------------------------------------------------------------------------
    // Geometría (unidades)
    // ---------------------------------------------------------------------------------------------------------------

    private int tile() {
        return pad.big(TILE_TEX);
    }

    private int cols() {
        int t = tile();
        return Math.max(3, Math.min(8, W / (t + pad.big(10))));
    }

    private int cellW() {
        return W / cols();
    }

    /** Ficha + rótulo (que sube un poco: sus dos primeras filas son para las tildes) + aire. */
    private int cellH() {
        return tile() + pad.big(11) + pad.big(2);
    }

    private int top() {
        return Y + pad.big(1);
    }

    /** Esquina de la ficha i (antes del desplazamiento). */
    private int[] at(int i) {
        int cols = cols(), cw = cellW();
        int x0 = X + (W - cols * cw) / 2;
        return new int[] {x0 + (i % cols) * cw + (cw - tile()) / 2, top() + (i / cols) * cellH()};
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Dibujo
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    void render(GuiGraphics g, double mx, double my, float partial) {
        long nowNs = System.nanoTime();
        float dt = Math.min(0.1F, (nowNs - lastFrame) / 1e9F);
        lastFrame = nowNs;
        long now = System.currentTimeMillis();
        List<App> apps = apps();
        int rows = (apps.size() + cols() - 1) / cols();
        contentH = rows * cellH() + pad.big(2);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - H)));
        int t = tile();
        boolean inside = PadUi.inside(mx, my, X, Y, W, H);
        String hoverNow = null;
        int hoverIndex = -1;
        for (int i = 0; i < apps.size(); i++) {
            int[] p = at(i);
            if (inside && PadUi.inside(mx, my, p[0], p[1] - scroll, t, t)) {
                hoverNow = apps.get(i).id;
                hoverIndex = i;
            }
        }
        if (hoverNow != null && !hoverNow.equals(hovered)) hoverStart = now;
        hovered = hoverNow;
        if (hoverNow != null) pad.hover(hoverNow);
        if (now > nextGlint && !apps.isEmpty()) {
            glintIndex = random.nextInt(apps.size());
            glintStart = now;
            nextGlint = now + 2500 + random.nextInt(3500);
        }

        pad.scissor(g, X, Y, W, H);
        for (int i = 0; i < apps.size(); i++) {
            App app = apps.get(i);
            int[] p = at(i);
            int tx = p[0], ty = p[1] - scroll;
            if (ty + cellH() < Y || ty > Y + H) continue;
            boolean hover = i == hoverIndex;
            float ht = hover ? (now - hoverStart) / 1000F : 0F;
            if (hover) {
                drawGlow(g, tx, ty, t, ht);
                spawn(app.icon, i, ht, dt);
            }
            boolean playing = app.id.equals("musica") && MusicPlayer.playing();
            int lift = hover ? -pad.big(1) : 0;
            pad.blitBig(g, hover ? "tile_h" : "tile", tx, ty + lift);
            drawIcon(g, app.icon, tx, ty + lift, hover ? ht : playing ? (now % 100000) / 1000F : -1F, hover || playing);
            if (i == glintIndex) drawGlint(g, tx, ty + lift, t, (now - glintStart) / 600F);
            pad.textBig(g, app.name, tx + t / 2F, ty + t - pad.big(1), hover ? 0xFFE680 : 0xFFFFFF);
        }
        drawParticles(g, dt);
        pad.noScissor(g);
        if (contentH > H) {
            int bx = X + W - 3, bh = H - 4;
            g.fill(bx, Y + 2, bx + 2, Y + 2 + bh, 0x5518265C);
            int th = Math.max(10, bh * H / contentH);
            int ty = Y + 2 + (bh - th) * scroll / Math.max(1, contentH - H);
            g.fill(bx, ty, bx + 2, ty + th, 0xFFF6B628);
        }
    }

    /** Halo detrás de la ficha: tres marcos translúcidos que laten. */
    private void drawGlow(GuiGraphics g, int x, int y, int t, float ht) {
        float pulse = 0.65F + 0.35F * (float) Math.sin(ht * 5);
        for (int k = 3; k >= 1; k--) {
            int pad = this.pad.big(k * 2);
            int a = (int) (pulse * (70 - k * 18));
            PadUi.box(g, x - pad, y - pad, t + pad * 2, t + pad * 2, a << 24 | 0xFFF2A0);
        }
    }

    /** Destello: una franja de luz que cruza la cara de la ficha en diagonal (p de 0 a 1). */
    private void drawGlint(GuiGraphics g, int x, int y, int t, float p) {
        if (p < 0 || p > 1) return;
        int inset = pad.big(4), face = t - inset * 2;
        float center = -face * 0.5F + p * face * 2.5F;
        int band = pad.big(5);
        for (int row = 0; row < face; row++) {
            int from = Math.round(center - row * 0.8F), to = from + band;
            int a = Math.max(0, from), b = Math.min(face, to);
            if (b > a) g.fill(x + inset + a, y + inset + row, x + inset + b, y + inset + row + 1, 0x55FFFFFF);
        }
    }

    /**
     * El icono con su animación. t: segundos desde que empezó (negativo: quieto). Todo gira o se escala alrededor de
     * un punto del icono (en píxeles de textura) para que parezca que se mueve de verdad.
     */
    private void drawIcon(GuiGraphics g, String icon, int x, int y, float t, boolean active) {
        float bs = pad.bs;
        float ox = x + ICON_OFF * bs, oy = y + ICON_OFF * bs;
        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        g.pose().scale(bs, bs, 1);
        if (t >= 0 && active) animate(g, icon, t);
        pad.blit(g, "icon_" + icon, 0, 0);
        if (t >= 0 && icon.equals("camara") && (t % 1.6F) < 0.12F) g.fill(2, 2, 30, 30, 0x99FFFFFF); // flash
        g.pose().popPose();
    }

    /** Transformaciones por app (coordenadas del icono de 32x32). */
    private void animate(GuiGraphics g, String icon, float t) {
        var pose = g.pose();
        switch (icon) {
            case "oficios" -> { // el pico pica: golpe rápido y vuelta lenta, girando por el mango
                float c = (t * 1.6F) % 1F;
                float ang = c < 0.25F ? -35 * (c / 0.25F) : -35 * (1 - (c - 0.25F) / 0.75F);
                rotate(g, 6, 27, -ang);
            }
            case "monedero" -> { // la moneda gira sobre sí misma
                float sx = (float) Math.cos(t * 6);
                pose.translate(16, 16, 0);
                pose.scale(Math.max(0.12F, Math.abs(sx)), 1, 1);
                pose.translate(-16, -16, 0);
            }
            case "tienda", "kits" -> { // salto con aplastado
                float c = (t * 2.2F) % 1F;
                float jump = (float) Math.sin(c * Math.PI);
                float squash = c < 0.12F ? 1 - (0.12F - c) * 1.2F : 1;
                pose.translate(16, 30, 0);
                pose.scale(2 - squash, squash, 1);
                pose.translate(-16, -30 - jump * 4, 0);
            }
            case "viajes" -> rotate(g, 16, 16, (float) Math.sin(t * 3) * 7);
            case "clanes" -> rotate(g, 16, 4, (float) Math.sin(t * 4) * 8); // el estandarte ondea desde la barra
            case "armario" -> rotate(g, 16, 3, (float) Math.sin(t * 3.5) * 10); // la percha se balancea
            case "comunidad" -> rotate(g, 16, 16, (float) Math.sin(t * 2.5) * 5);
            case "protecciones" -> { // el escudo late
                float s = 1 + 0.08F * (float) Math.abs(Math.sin(t * 4));
                scale(g, 16, 16, s);
            }
            case "efectos" -> { // las chispas giran y laten
                rotate(g, 16, 16, t * 90);
                scale(g, 16, 16, 1 + 0.1F * (float) Math.sin(t * 6));
            }
            case "admin" -> rotate(g, 16, 16, t * 120); // el engranaje gira
            case "cazas" -> { // retroceso de la ballesta al disparar
                float c = (t * 1.3F) % 1F;
                pose.translate(c < 0.1F ? -3 * (1 - c / 0.1F) : 0, 0, 0);
            }
            case "camara" -> scale(g, 16, 16, 1 + ((t % 1.6F) < 0.12F ? 0.08F : 0));
            case "musica" -> { // al ritmo: la canción que suena (o un latido)
                float lv = MusicPlayer.playing() ? MusicPlayer.levelAt(MusicPlayer.position()) : 0.5F + 0.5F * (float) Math.sin(t * 7);
                scale(g, 16, 18, 1 + 0.09F * lv);
                rotate(g, 16, 18, (float) Math.sin(t * 2.2) * 4);
            }
            default -> pose.translate(0, (float) Math.sin(t * 4) * -1.5F, 0); // flota (misiones, ranking, rango, ayuda…)
        }
    }

    private static void rotate(GuiGraphics g, float cx, float cy, float degrees) {
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(degrees));
        g.pose().translate(-cx, -cy, 0);
    }

    private static void scale(GuiGraphics g, float cx, float cy, float s) {
        g.pose().translate(cx, cy, 0);
        g.pose().scale(s, s, 1);
        g.pose().translate(-cx, -cy, 0);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Partículas
    // ---------------------------------------------------------------------------------------------------------------

    private float spawnDebt;

    /** Echa partículas según la app (en píxeles de textura del icono, convertidos a unidades). */
    private void spawn(String icon, int index, float t, float dt) {
        spawnDebt += dt * 14;
        while (spawnDebt >= 1) {
            spawnDebt -= 1;
            Particle p = new Particle();
            p.app = index;
            p.life = p.max = 0.7F + random.nextFloat() * 0.6F;
            p.size = 1 + random.nextInt(2);
            float rx = random.nextFloat(), ry = random.nextFloat();
            switch (icon) {
                case "oficios" -> { // chispas de piedra donde golpea la punta
                    if ((t * 1.6F) % 1F > 0.35F) continue;
                    set(p, 22 + rx * 4, 9 + ry * 4, (rx - 0.3F) * 40, -20 - ry * 30, 90, rx < 0.5F ? 0xFFFFF4C0 : 0xFFB8C4D8);
                }
                case "hogares" -> { // humo de la chimenea
                    set(p, 20 + rx * 2, 3, (rx - 0.5F) * 6, -14 - ry * 8, -2, 0xCCD8DEE8);
                    p.size = 2 + random.nextInt(2);
                    p.life = p.max = 1.4F;
                }
                case "tienda", "monedero", "ranking", "rango" -> set(p, 4 + rx * 24, 4 + ry * 20, (rx - 0.5F) * 10, -16 - ry * 14, 0, 0xFFFFD84A);
                case "gts" -> set(p, 4 + rx * 24, 6 + ry * 20, (rx - 0.5F) * 10, -14 - ry * 10, 0, 0xFF5CF08A);
                case "comunidad" -> set(p, 8 + rx * 16, 10 + ry * 10, (rx - 0.5F) * 12, -18 - ry * 10, 0, 0xFFFF7EC4);
                case "efectos" -> set(p, 16 + (rx - 0.5F) * 30, 16 + (ry - 0.5F) * 30, 0, -4, 0, rx < 0.5F ? 0xFFE0A0FF : 0xFFFFFFFF);
                case "protecciones" -> set(p, 16 + (rx - 0.5F) * 30, 30, 0, -22 - ry * 10, 0, 0xFF7AE8FF);
                case "musica" -> set(p, 18 + rx * 12, 6 + ry * 8, 6 + rx * 8, -16 - ry * 8, 0, rx < 0.5F ? 0xFFFFD84A : 0xFF7CF0B0);
                case "viajes" -> set(p, 4 + rx * 24, 4 + ry * 24, (rx - 0.5F) * 8, -10, 0, 0xFF96D6FF);
                case "cazas" -> { // flecha que sale disparada
                    if ((t * 1.3F) % 1F > 0.12F) continue;
                    set(p, 26, 8, 70, -30, 0, 0xFFFFFFFF);
                }
                case "camara" -> {
                    if ((t % 1.6F) > 0.15F) continue;
                    set(p, 16 + (rx - 0.5F) * 34, 16 + (ry - 0.5F) * 34, (rx - 0.5F) * 30, (ry - 0.5F) * 30, 0, 0xFFFFFFFF);
                }
                default -> set(p, 4 + rx * 24, 4 + ry * 24, (rx - 0.5F) * 6, -12 - ry * 6, 0, 0xFFFFF2A0);
            }
            p.sparkle = random.nextFloat() < 0.35F;
            if (particles.size() < 160) particles.add(p);
        }
    }

    private void set(Particle p, float x, float y, float vx, float vy, float gravity, int color) {
        p.x = x;
        p.y = y;
        p.vx = vx;
        p.vy = vy;
        p.gravity = gravity;
        p.color = color;
    }

    private void drawParticles(GuiGraphics g, float dt) {
        float bs = pad.bs;
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.life -= dt;
            if (p.life <= 0) {
                it.remove();
                continue;
            }
            p.vy += p.gravity * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            int[] at = at(p.app);
            float px = at[0] + (ICON_OFF + p.x) * bs, py = at[1] - scroll + (ICON_OFF + p.y) * bs;
            int alpha = (int) (255 * Math.min(1F, p.life / p.max * 1.6F));
            int c = (alpha << 24) | (p.color & 0xFFFFFF);
            int s = Math.max(1, Math.round(p.size * bs));
            int x0 = Math.round(px), y0 = Math.round(py);
            if (p.sparkle) { // estrellita en cruz
                g.fill(x0 - s, y0, x0 + s + 1, y0 + 1, c);
                g.fill(x0, y0 - s, x0 + 1, y0 + s + 1, c);
            } else {
                g.fill(x0, y0, x0 + s, y0 + s, c);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Ratón y teclado
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    boolean click(double mx, double my, int button) {
        if (button != 0 || !PadUi.inside(mx, my, X, Y, W, H)) return false;
        List<App> apps = apps();
        int t = tile();
        for (int i = 0; i < apps.size(); i++) {
            int[] p = at(i);
            if (PadUi.inside(mx, my, p[0], p[1] - scroll, t, t)) {
                pad.openApp(apps.get(i).id);
                return true;
            }
        }
        return false;
    }

    @Override
    boolean scroll(double mx, double my, double delta) {
        scroll -= (int) Math.signum(delta) * cellH() / 2;
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
