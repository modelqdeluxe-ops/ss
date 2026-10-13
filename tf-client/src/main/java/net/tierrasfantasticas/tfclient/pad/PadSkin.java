package net.tierrasfantasticas.tfclient.pad;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;

/**
 * El diseño de las apps del pad (1.4), sobre las piezas de PadUi (que siguen igual para Música y Comunidad). Cada app
 * tiene su color y su frase (THEME): su cabecera (banner) con el icono en su medallón, rayas que se mueven, una luz y el
 * icono grande de marca de agua; pestañas en pastilla (la elegida, de su color con una flechita hacia la cabecera);
 * tarjetas con vitrina, franja de
 * color, rayos de luz que giran detrás de lo elegido y un brillo que las cruza; botones de caramelo que suben al pasar
 * el ratón y se hunden al pulsarlos; barras con un reflejo que corre; y estados vacíos con el icono flotando en su luz.
 * <p>
 * Las luces y degradados a los lados se dibujan con vértices de color (sin texturas): {@link #glow}, {@link #hgrad},
 * {@link #disc}, {@link #beam}. Las animaciones usan {@link #time()} (quieto si en Ajustes se apagan). Lo mismo, en
 * Python, en tools/pad/sim/skin.py: si cambias medidas o colores aquí, cámbialos allí.
 */
final class PadSkin {
    private PadSkin() {}

    static final int WHITE = 0xFFFFFFFF;
    private static final long START = System.currentTimeMillis();

    // ---------------------------------------------------------------------------------------------------------------
    // El color y la frase de cada app
    // ---------------------------------------------------------------------------------------------------------------

    private record Theme(int color, String tagline) {}

    private static final Map<String, Theme> THEME = new HashMap<>();

    static {
        theme("oficios", 0xE0892E, "Trabaja, sube de nivel y cobra cada pago.");
        theme("misiones", 0x22A884, "Retos diarios y semanales con premio.");
        theme("cazas", 0xC8553D, "Caza criaturas y gana premios.");
        theme("recompensas", 0xE2466A, "Tu premio de cada día: no rompas la racha.");
        theme("tienda", 0xE6A019, "Compra y vende al momento.");
        theme("gts", 0x8B5CF0, "El mercado entre jugadores.");
        theme("monedero", 0xD49A12, "Tus monedas y tus movimientos.");
        theme("viajes", 0x1AA3C8, "Viaja por el mundo en un momento.");
        theme("explorar", 0x1AA3C8, "Descubre sitios nuevos al azar.");
        theme("hogares", 0xDB6440, "Tus casas, a un clic.");
        theme("kits", 0xB4783A, "Equipo listo para reclamar.");
        theme("protecciones", 0x3B7DDB, "Protege tus terrenos.");
        theme("clanes", 0xD43A50, "Tu clan, sus miembros y aliados.");
        theme("jugadores", 0x2E9BE6, "Quién está conectado ahora.");
        theme("ranking", 0xE3A52A, "Los mejores del servidor.");
        theme("armario", 0x9E5AE0, "Tus skins y tus sets, a tu estilo.");
        theme("efectos", 0xE0489E, "Efectos al matar y clases de skills.");
        theme("rango", 0xD9A21E, "Tu rango y sus ventajas.");
        theme("gachapon", 0xE0489E, "Gira y gana premios.");
        theme("pase", 0x2EAF5C, "Sube de nivel y cobra premios.");
        theme("camara", 0x3B9BE0, "Tus fotos para la comunidad.");
        theme("ajustes", 0x5B74A6, "Tu pad, a tu manera.");
        theme("admin", 0x5B74A6, "Configura el servidor.");
    }

    private static void theme(String app, int color, String tagline) {
        THEME.put(app, new Theme(color, tagline));
    }

    /** La app de la que se toma el tema: las del admin usan el de su app («a_tienda» → «tienda»). */
    private static String base(String app) {
        if (app.equals("a_apps") || app.equals("a_ajustes")) return "admin";
        return app.startsWith("a_") ? app.substring(2) : app;
    }

    /** El color de la app (ARGB opaco). */
    static int accent(String app) {
        Theme t = THEME.get(base(app));
        return 0xFF000000 | (t == null ? 0x3496FA : t.color);
    }

    static String tagline(String app) {
        Theme t = THEME.get(base(app));
        return t == null ? "" : t.tagline;
    }

    /** El icono de la app (icon_&lt;nombre&gt;.png). */
    static String icon(String app) {
        String b = base(app);
        return b.equals("explorar") ? "viajes" : b.equals("ajustes") ? "admin" : b;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Tiempo y colores
    // ---------------------------------------------------------------------------------------------------------------

    /** Segundos para las animaciones; 0 siempre si se apagaron en Ajustes (todo quieto). */
    static float time() {
        return PadSettings.animations ? (System.currentTimeMillis() - START) / 1000F : 0F;
    }

    /** 0 a 1: cuánto brilla un destello que parpadea (cada uno con su semilla). */
    static float twinkle(float seed) {
        float v = (float) Math.sin(time() * 2.1F + seed * 2.39F);
        return v <= 0 ? 0 : v * v;
    }

    /** a mezclado con b (k = 0: a; k = 1: b). Opaco. */
    static int mix(int a, int b, float k) {
        int r = Math.round((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * k);
        int g = Math.round((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * k);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * k);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    static int darken(int c, float k) {
        return 0xFF000000 | Math.round((c >> 16 & 255) * k) << 16 | Math.round((c >> 8 & 255) * k) << 8 | Math.round((c & 255) * k);
    }

    static int lighten(int c, int n) {
        return 0xFF000000 | PadUi.lighten(c, n) & 0xFFFFFF;
    }

    /** Para fondos con texto blanco encima: los colores muy claros (amarillos, celestes) se oscurecen un poco. */
    static int deep(int c) {
        float lum = (0.299F * (c >> 16 & 255) + 0.587F * (c >> 8 & 255) + 0.114F * (c & 255)) / 255F;
        return lum > 0.62F ? mix(c, PadUi.INK, Math.min(0.45F, (lum - 0.62F) * 1.6F + 0.12F)) : 0xFF000000 | c;
    }

    /** Para texto sobre blanco: los colores muy claros se oscurecen hacia el azul noche (se leen siempre). */
    static int ink(int c) {
        float lum = (0.299F * (c >> 16 & 255) + 0.587F * (c >> 8 & 255) + 0.114F * (c & 255)) / 255F;
        return lum > 0.55F ? mix(c, PadUi.INK, Math.min(0.7F, (lum - 0.55F) * 1.5F + 0.3F)) : 0xFF000000 | c;
    }

    /** El color c con alfa a (0-255). */
    static int alpha(int c, int a) {
        return Math.max(0, Math.min(255, a)) << 24 | c & 0xFFFFFF;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Formas con vértices de color (se juntan y se pintan de una vez con g.flush())
    // ---------------------------------------------------------------------------------------------------------------

    private static VertexConsumer vc(GuiGraphics g) {
        return g.bufferSource().getBuffer(RenderType.gui());
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, int c) {
        vc.vertex(m, x, y, 0).color(c >> 16 & 255, c >> 8 & 255, c & 255, c >>> 24).endVertex();
    }

    /**
     * Un cuadrilátero con un color en cada esquina, siempre en el mismo sentido que GuiGraphics.fill (arriba izquierda,
     * abajo izquierda, abajo derecha, arriba derecha): si llega al revés, se le da la vuelta (si no, no se vería).
     */
    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2,
                             int c2, float x3, float y3, int c3) {
        float area = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0);
        if (area == 0) area = (x2 - x0) * (y3 - y0) - (y2 - y0) * (x3 - x0);
        if (area > 0) {
            v(vc, m, x0, y0, c0);
            v(vc, m, x3, y3, c3);
            v(vc, m, x2, y2, c2);
            v(vc, m, x1, y1, c1);
        } else {
            v(vc, m, x0, y0, c0);
            v(vc, m, x1, y1, c1);
            v(vc, m, x2, y2, c2);
            v(vc, m, x3, y3, c3);
        }
    }

    /** Rectángulo de un color dentro de un lote (como fill, pero sin pintarlo aún: luego g.flush()). */
    static void rect(GuiGraphics g, float x0, float y0, float x1, float y1, int c) {
        if (x1 <= x0 || y1 <= y0) return;
        quad(vc(g), g.pose().last().pose(), x0, y0, c, x0, y1, c, x1, y1, c, x1, y0, c);
    }

    /** Degradado a lo ancho: cl a la izquierda, cr a la derecha (ARGB, con alfa). */
    static void hgrad(GuiGraphics g, float x0, float y0, float x1, float y1, int cl, int cr) {
        if (x1 <= x0 || y1 <= y0) return;
        quad(vc(g), g.pose().last().pose(), x0, y0, cl, x0, y1, cl, x1, y1, cr, x1, y0, cr);
        g.flush();
    }

    /** Degradado vertical con alfa (fillGradient, pero en float). */
    static void vgrad(GuiGraphics g, float x0, float y0, float x1, float y1, int ct, int cb) {
        if (x1 <= x0 || y1 <= y0) return;
        quad(vc(g), g.pose().last().pose(), x0, y0, ct, x0, y1, cb, x1, y1, cb, x1, y0, ct);
        g.flush();
    }

    private static final int SIDES = 24;

    /**
     * Luz redonda de radio r: alfa a en el centro, el 40 % a 0,45 r y nada en el borde (dos anillos de 24 lados con
     * degradado: se ve suave sin textura).
     */
    static void glow(GuiGraphics g, float cx, float cy, float r, int rgb, int a) {
        if (r <= 0 || a <= 0) return;
        VertexConsumer vc = vc(g);
        Matrix4f m = g.pose().last().pose();
        int c0 = alpha(rgb, a), c1 = alpha(rgb, Math.round(a * 0.4F)), c2 = alpha(rgb, 0);
        float ri = r * 0.45F;
        for (int i = 0; i < SIDES; i++) {
            double a0 = i * Math.PI * 2 / SIDES, a1 = (i + 1) * Math.PI * 2 / SIDES;
            float ix0 = cx + (float) Math.cos(a0) * ri, iy0 = cy + (float) Math.sin(a0) * ri;
            float ix1 = cx + (float) Math.cos(a1) * ri, iy1 = cy + (float) Math.sin(a1) * ri;
            float ox0 = cx + (float) Math.cos(a0) * r, oy0 = cy + (float) Math.sin(a0) * r;
            float ox1 = cx + (float) Math.cos(a1) * r, oy1 = cy + (float) Math.sin(a1) * r;
            quad(vc, m, cx, cy, c0, ix0, iy0, c1, ix1, iy1, c1, ix1, iy1, c1);
            quad(vc, m, ix0, iy0, c1, ox0, oy0, c2, ox1, oy1, c2, ix1, iy1, c1);
        }
        g.flush();
    }

    /** Círculo lleno de un color (24 lados). */
    static void disc(GuiGraphics g, float cx, float cy, float r, int argb) {
        VertexConsumer vc = vc(g);
        Matrix4f m = g.pose().last().pose();
        for (int i = 0; i < SIDES; i++) {
            double a0 = i * Math.PI * 2 / SIDES, a1 = (i + 1) * Math.PI * 2 / SIDES;
            float x0 = cx + (float) Math.cos(a0) * r, y0 = cy + (float) Math.sin(a0) * r;
            float x1 = cx + (float) Math.cos(a1) * r, y1 = cy + (float) Math.sin(a1) * r;
            quad(vc, m, cx, cy, argb, x0, y0, argb, x1, y1, argb, x1, y1, argb);
        }
        g.flush();
    }

    /**
     * Haz de luz inclinado (sube hacia la derecha) por la zona (0, 0, w, h): blanco con alfa a en el centro que se
     * apaga a los lados en half unidades. cx: dónde corta la mitad de la altura.
     */
    static void beam(GuiGraphics g, float cx, float half, float w, float h, float a) {
        VertexConsumer vc = vc(g);
        Matrix4f m = g.pose().last().pose();
        float slope = 0.6F;
        // en y la línea central está en x = cx - (y - h/2) * slope
        float topC = cx + (h / 2) * slope, botC = cx - (h / 2) * slope;
        int mid = alpha(0xFFFFFF, Math.round(255 * a)), none = 0x00FFFFFF;
        quad(vc, m, topC - half, 0, none, botC - half, h, none, botC, h, mid, topC, 0, mid);
        quad(vc, m, topC, 0, mid, botC, h, mid, botC + half, h, none, topC + half, 0, none);
        g.flush();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Texturas
    // ---------------------------------------------------------------------------------------------------------------

    /** Una textura (del pad o cualquiera) estirada a w x h, teñida (tint RGB) y con alfa; smooth: suavizada. */
    static void texture(GuiGraphics g, ResourceLocation loc, float x, float y, float w, float h, float alpha, int tint, boolean smooth) {
        int[] sz = TFPadScreen.size(loc);
        if (sz[0] == 0 || w <= 0 || h <= 0 || alpha <= 0.01F) return;
        RenderSystem.enableBlend();
        Minecraft.getInstance().getTextureManager().getTexture(loc).setFilter(smooth, false);
        g.setColor((tint >> 16 & 255) / 255F, (tint >> 8 & 255) / 255F, (tint & 255) / 255F, alpha);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(w / sz[0], h / sz[1], 1);
        g.blit(loc, 0, 0, 0, 0, sz[0], sz[1], sz[0], sz[1]);
        g.pose().popPose();
        g.setColor(1, 1, 1, 1);
    }

    /** Las rayas en diagonal (stripes.png) en mosaico, con alfa, desplazadas off unidades (se mueven solas). */
    static void stripes(GuiGraphics g, int x, int y, int w, int h, float alpha, float off) {
        if (w <= 0 || h <= 0) return;
        RenderSystem.enableBlend();
        g.setColor(1, 1, 1, alpha);
        g.blit(TFPadScreen.tex("stripes"), x, y, -(off % 16), 0, w, h, 16, 16);
        g.setColor(1, 1, 1, 1);
    }

    /** Rayos de luz (rays.png) de radio r que giran (angle en grados). */
    static void rays(GuiGraphics g, float cx, float cy, float r, float alpha, float angle) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(angle % 360));
        texture(g, TFPadScreen.tex("rays"), -r, -r, 2 * r, 2 * r, alpha, 0xFFFFFF, true);
        g.pose().popPose();
    }

    /**
     * Iconos que son el objeto de Minecraft de verdad, no un dibujo nuestro (1.4.1): el pico de Oficios es el pico de
     * diamante del juego, al doble (16 → 32) y nítido, con el mismo contorno azul noche que los demás iconos.
     */
    private static final Map<String, ItemStack> ITEM_ICONS = new HashMap<>();

    private static ItemStack itemIcon(String name) {
        if (!name.equals("oficios")) return null;
        return ITEM_ICONS.computeIfAbsent(name, n -> new ItemStack(Items.DIAMOND_PICKAXE));
    }

    /**
     * El icono de una app en un cuadrado de 32x32 con la esquina en (0, 0) de la pose actual: icon_&lt;nombre&gt;.png o,
     * si es un objeto (ITEM_ICONS), el objeto al doble con su contorno. alpha &lt; 1: transparente y sin contorno (la
     * marca de agua de la cabecera).
     */
    static void icon32(GuiGraphics g, TFPadScreen pad, String name, float alpha) {
        ItemStack stack = itemIcon(name);
        if (stack == null) {
            if (alpha >= 1F) pad.blit(g, "icon_" + name, 0, 0);
            else texture(g, TFPadScreen.tex("icon_" + name), 0, 0, 32, 32, alpha, 0xFFFFFF, false);
            return;
        }
        if (alpha >= 1F) glow(g, 16, 16, 17, 0xFFFFFF, 90); // una luz suave detrás: el objeto se despega del fondo
        g.flush();
        g.pose().pushPose();
        g.pose().scale(2, 2, 1);
        if (alpha >= 1F) {
            // el contorno: el objeto teñido de azul noche, medio píxel de objeto (1 del icono) hacia cada lado
            g.setColor(0x18 / 255F, 0x26 / 255F, 0x5C / 255F, 1F);
            float[][] d = {{-0.5F, 0}, {0.5F, 0}, {0, -0.5F}, {0, 0.5F}};
            for (float[] o : d) {
                g.pose().pushPose();
                g.pose().translate(o[0], o[1], 0);
                g.renderItem(stack, 0, 0);
                g.pose().popPose();
            }
        }
        g.setColor(1, 1, 1, alpha);
        g.renderItem(stack, 0, 0);
        g.setColor(1, 1, 1, 1);
        g.pose().popPose();
        // el objeto se pinta «por delante» (z 150): se borra su profundidad para que lo que venga después lo tape
        g.flush();
        RenderSystem.clear(256, Minecraft.ON_OSX);
    }

    /**
     * Un icono de app (32x32) nítido: al mayor número entero de píxeles reales por píxel que quepa en size unidades,
     * centrado en (cx, cy).
     */
    static void iconCrisp(GuiGraphics g, TFPadScreen pad, String name, float cx, float cy, int size) {
        int upx = Math.max(1, pad.unitPx());
        int n = Math.max(1, size * upx / 32);
        float k = n / (float) upx;
        g.pose().pushPose();
        g.pose().translate(cx - 16 * k, cy - 16 * k, 0);
        g.pose().scale(k, k, 1);
        icon32(g, pad, name, 1F);
        g.pose().popPose();
    }

    /**
     * La imagen de una fila o tarjeta (PadView: image, un ResourceLocation): las grandes (más de 64 px, renders como
     * los de los efectos de kill) suavizadas y llenando size; las de pixel art (iconos de clases y skills), nítidas a
     * un número entero de píxeles reales por píxel (o suavizadas si no caben ni a 1). Centrada en el cuadrado.
     * Devuelve false si la textura no existe (entonces se dibuja el objeto).
     */
    static boolean image(GuiGraphics g, TFPadScreen pad, String id, int x, int y, int size) {
        return image(g, pad, id, x + size / 2F, y + size / 2F, size, size);
    }

    /** Ancho / alto de una imagen del pad (0 si no existe). */
    static float aspect(String id) {
        ResourceLocation loc = id == null || id.isEmpty() ? null : ResourceLocation.tryParse(id);
        if (loc == null) return 0F;
        int[] sz = TFPadScreen.size(loc);
        return sz[0] == 0 || sz[1] == 0 ? 0F : sz[0] / (float) sz[1];
    }

    /** ¿Es una imagen ancha (un retrato, como los de los oficios) que se enseña como una foto? */
    static boolean wide(String id) {
        return aspect(id) > 1.25F;
    }

    /**
     * Una imagen ancha como una foto: de h de alto (y su ancho), centrada en cx, con marco azul noche y un brillo arriba;
     * en pixel art con el vecino más cercano aunque no sea a escala entera (así llena el sitio). Devuelve su ancho.
     */
    static int photo(GuiGraphics g, String id, float cx, int y, int h) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        float a = aspect(id);
        if (loc == null || a <= 0F) return 0;
        int w = Math.round(h * a);
        int x = Math.round(cx - w / 2F);
        g.fill(x - 1, y + h + 1, x + w + 1, y + h + 2, 0x3018265C);
        PadUi.box(g, x - 1, y - 1, w + 2, h + 2, PadUi.INK);
        texture(g, loc, x, y, w, h, 1F, 0xFFFFFF, false);
        g.fill(x, y, x + w, y + 1, 0x50FFFFFF);
        return w + 2;
    }

    /**
     * Lo mismo, centrada en (cx, cy) y cabiendo en maxW x maxH sin deformarse (los retratos de los oficios son más anchos
     * que altos: 64x39). Pixel art a un número entero de píxeles reales por píxel; si no cabe ni a 1, suavizada.
     */
    static boolean image(GuiGraphics g, TFPadScreen pad, String id, float cx, float cy, int maxW, int maxH) {
        ResourceLocation loc = id == null || id.isEmpty() ? null : ResourceLocation.tryParse(id);
        if (loc == null) return false;
        int[] sz = TFPadScreen.size(loc);
        if (sz[0] == 0) return false;
        int iw = sz[0], ih = Math.max(1, sz[1]);
        float fit = Math.min(maxW / (float) iw, maxH / (float) ih);
        int upx = Math.max(1, pad.unitPx());
        int n = (int) Math.floor(fit * upx);
        boolean smooth = Math.max(iw, ih) > 64 || n < 1;
        float k = smooth ? fit : n / (float) upx;
        float w = iw * k, h = ih * k;
        texture(g, loc, cx - w / 2, cy - h / 2, w, h, 1F, 0xFFFFFF, smooth);
        return true;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Detalles
    // ---------------------------------------------------------------------------------------------------------------

    /** Destello de 4 puntas: centro blanco y brazos de s que se apagan. a: 0 a 1. */
    static void sparkle(GuiGraphics g, int cx, int cy, int s, float a, int rgb) {
        if (a <= 0.02F) return;
        rect(g, cx, cy, cx + 1, cy + 1, alpha(0xFFFFFF, Math.round(255 * a)));
        for (int i = 1; i <= s; i++) {
            int c = alpha(rgb, Math.round(255 * a * (1 - (i - 1) / (float) s) * 0.9F));
            rect(g, cx + i, cy, cx + i + 1, cy + 1, c);
            rect(g, cx - i, cy, cx - i + 1, cy + 1, c);
            rect(g, cx, cy + i, cx + 1, cy + i + 1, c);
            rect(g, cx, cy - i, cx + 1, cy - i + 1, c);
        }
        g.flush();
    }

    /**
     * El brillo que cruza en diagonal (cada period segundos tarda 0,9 s): una banda ancha y otra fina, en escalera de
     * píxeles, recortadas al rectángulo.
     */
    static void sheen(GuiGraphics g, int x, int y, int w, int h, float period, float alpha) {
        if (!PadSettings.animations) return;
        sheenAt(g, x, y, w, h, (time() % period) / 0.9F, alpha);
    }

    /** El brillo en el punto k de su paso (0 entra por la izquierda, 1 ya salió por la derecha). */
    static void sheenAt(GuiGraphics g, int x, int y, int w, int h, float k, float alpha) {
        if (k < 0 || k >= 1) return;
        float pos = -h - 12 + k * (w + h + 24);
        int c1 = alpha(0xFFFFFF, Math.round(255 * alpha)), c2 = alpha(0xFFFFFF, Math.round(255 * alpha * 0.8F));
        for (int j = 0; j < h; j++) {
            int a0 = Math.max(x, (int) (x + pos - j)), a1 = Math.min(x + w, (int) (x + pos + 6 - j));
            if (a1 > a0) rect(g, a0, y + j, a1, y + j + 1, c1);
            int b0 = Math.max(x, (int) (x + pos + 9 - j)), b1 = Math.min(x + w, (int) (x + pos + 11 - j));
            if (b1 > b0) rect(g, b0, y + j, b1, y + j + 1, c2);
        }
        g.flush();
    }

    /** Texto (letra de Minecraft) con una sombra de color a 1 abajo a la derecha. */
    static void shadowText(GuiGraphics g, String s, int x, int y, int color, int shadow) {
        g.drawString(PadUi.font(), s, x + 1, y + 1, shadow, false);
        g.drawString(PadUi.font(), s, x, y, color, false);
    }

    /** Título en negrita con sombra de color (recortado a w como PadUi.title). */
    static void shadowTitle(GuiGraphics g, String s, int x, int y, int w, int color, int shadow) {
        PadUi.title(g, s, x + 1, y + 1, w, shadow);
        PadUi.title(g, s, x, y, w, color);
    }

    /** Esquina de oro (triángulo de 11) con una marca blanca: lo elegido. */
    static void ribbon(GuiGraphics g, int x, int y) {
        for (int i = 0; i < 11; i++) rect(g, x, y + i, x + 11 - i, y + i + 1, i < 10 ? PadUi.GOLD : PadUi.GOLD_LO);
        rect(g, x, y, x + 11, y + 1, PadUi.GOLD_HI);
        int[][] px = {{5, 1}, {4, 2}, {5, 2}, {1, 3}, {3, 3}, {4, 3}, {1, 4}, {2, 4}, {3, 4}, {2, 5}};
        for (int[] p : px) rect(g, x + p[0], y + p[1], x + p[0] + 1, y + p[1] + 1, WHITE);
        g.flush();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Piezas
    // ---------------------------------------------------------------------------------------------------------------

    /** La ventana de una app: borde azul noche, blanco que baja al color de la app, cristal tallado y oro. */
    static void window(GuiGraphics g, int x, int y, int w, int h, int acc) {
        g.fill(x + 2, y + h, x + w - 2, y + h + 1, 0x5518265C);
        g.fill(x + 4, y + h + 1, x + w - 4, y + h + 2, 0x2318265C);
        PadUi.box(g, x, y, w, h, PadUi.INK);
        if (w <= 4 || h <= 4) return;
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFFAFDFF, mix(0xFFDCECFB, PadUi.tint(acc, 0.3F), 0.35F));
        PadUi.texture(g, x + 1, y + 1, w - 2, h - 2);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, WHITE);
        g.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, mix(0xFFC6DAEE, acc, 0.15F));
        if (w >= 16 && h >= 16) PadUi.corners(g, x, y, w, h);
    }

    /**
     * Campo de texto (1.4.2): marco del color de la app, cristal blanco con sombra interior arriba, un lápiz en su
     * pastilla a la izquierda, la pista en el tono de la app y el cursor de su color que parpadea.
     */
    static void field(GuiGraphics g, int x, int y, int w, String text, String hint, int acc) {
        int c = deep(acc);
        g.fill(x + 1, y + 16, x + w - 1, y + 17, 0x2418265C);
        PadUi.box(g, x, y, w, 16, mix(c, PadUi.INK, 0.35F));
        g.fillGradient(x + 1, y + 1, x + w - 1, y + 15, mix(0xFFE9F1FA, PadUi.tint(c, 0.25F), 0.4F), WHITE);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, mix(0xFFC9D8EA, c, 0.18F));
        // el lápiz, en su pastilla del color de la app
        int px = x + 2, py = y + 2;
        g.fillGradient(px, py, px + 12, py + 12, mix(c, WHITE, 0.35F), c);
        g.fill(px + 12, py, px + 13, py + 12, mix(c, PadUi.INK, 0.3F));
        for (int i = 0; i < 6; i++) g.fill(px + 3 + i, py + 8 - i, px + 5 + i, py + 9 - i, WHITE);
        g.fill(px + 3, py + 9, px + 4, py + 10, mix(c, WHITE, 0.8F));
        int tx = x + 18, tw = w - 22;
        String shown = text.isEmpty() ? PadUi.fitEnd(hint, tw) : PadUi.fitStart(text, tw);
        PadUi.text(g, shown, tx, y + 4, text.isEmpty() ? mix(0xFF96AACC, c, 0.25F) : PadUi.TEXT);
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = tx + (text.isEmpty() ? 0 : PadUi.font().width(shown));
            g.fill(cx, y + 3, cx + 1, y + 13, c);
        }
    }

    /**
     * Debajo de una lista corta, en vez de un hueco blanco: el icono de la app grande y muy suave, con su luz y unos
     * destellos que parpadean (k: lo que queda de alto, para no dibujarlo si no hay sitio).
     */
    static void listFooterArt(GuiGraphics g, TFPadScreen pad, int cx, int cy, int room, int acc, String icon) {
        if (room < 46) return;
        float t = time();
        int s = Math.min(56, room - 12);
        glow(g, cx, cy, s * 1.2F, acc & 0xFFFFFF, 46);
        g.pose().pushPose();
        float bob = PadSettings.animations ? (float) Math.sin(t * 1.6F) * 1.5F : 0F;
        g.pose().translate(cx - s / 2F, cy - s / 2F + bob, 0);
        g.pose().scale(s / 32F, s / 32F, 1);
        icon32(g, pad, icon, 0.16F);
        g.pose().popPose();
        for (int i = 0; i < 4; i++) {
            float tw = PadSettings.animations ? (float) Math.max(0, Math.sin(t * 1.9F + i * 1.7F)) : 0.5F;
            int sx = cx + (int) ((i % 2 == 0 ? -1 : 1) * (s * 0.55F + i * 4)), sy = cy + (i < 2 ? -s / 4 : s / 5);
            sparkle(g, sx, sy, i % 2 + 1, tw * tw * 0.7F, mix(acc, WHITE, 0.5F) & 0xFFFFFF);
        }
    }

    /** Alto de la cabecera de una app con n líneas de texto. */
    static int bannerHeight(int lines) {
        return Math.max(24, lines * 10 + 10);
    }

    /**
     * La cabecera de una app: degradado de su color con rayas que se mueven, una luz a la derecha con el icono grande
     * de marca de agua, el icono en su medallón, el texto en blanco con sombra, destellos y un brillo que la cruza.
     */
    static void banner(GuiGraphics g, TFPadScreen pad, int x, int y, int w, int h, int acc, String icon, java.util.List<String> lines) {
        g.fillGradient(x, y, x + w, y + h, mix(acc, WHITE, 0.14F), darken(acc, 0.78F));
        stripes(g, x, y, w, h, 0.08F, time() * 3);
        glow(g, x + w - 34, y + h / 2F, 64, 0xFFFFFF, 70);
        // el icono en grande de marca de agua a la derecha, recortado por la cabecera
        pad.clipPush(g, x, y, w, h);
        int wm = 44;
        g.pose().pushPose();
        g.pose().translate(x + w - wm - 10, y + h / 2F - wm / 2F + 3, 0);
        g.pose().scale(wm / 32F, wm / 32F, 1);
        icon32(g, pad, icon, 0.22F);
        g.pose().popPose();
        pad.clipPop(g);
        g.fill(x, y, x + w, y + 1, lighten(acc, 70));
        g.fill(x, y + h - 1, x + w, y + h, darken(acc, 0.55F));
        g.fill(x, y + h, x + w, y + h + 1, 0x3218265C);
        // el medallón con el icono
        int ms = 20, mx = x + 5, my = y + (h - ms) / 2;
        PadUi.box(g, mx, my, ms, ms, darken(acc, 0.45F));
        g.fillGradient(mx + 1, my + 1, mx + ms - 1, my + ms - 1, mix(acc, WHITE, 0.62F), mix(acc, WHITE, 0.22F));
        g.fill(mx + 2, my + 1, mx + ms - 2, my + 2, WHITE);
        glow(g, mx + ms / 2F, my + ms / 2F, ms * 0.55F, 0xFFFFFF, 120);
        iconCrisp(g, pad, icon, mx + ms / 2F, my + ms / 2F, ms - 3);
        int tx = mx + ms + 7, shadow = darken(acc, 0.42F);
        int ty = y + (h - lines.size() * 10) / 2 + 1;
        for (int i = 0; i < lines.size(); i++) shadowText(g, lines.get(i), tx, ty + i * 10, WHITE, shadow);
        int[][] sp = {{w - 18, 5}, {w - 58, h - 6}, {w - 90, 6}};
        for (int i = 0; i < sp.length; i++) sparkle(g, x + sp[i][0], y + sp[i][1], 2, twinkle(i * 1.7F + 0.4F) * 0.9F, 0xFFFFFF);
        sheen(g, x, y, w, h - 1, 6F, 0.18F);
    }

    /** Alto de una pestaña, aire entre las pestañas y la ventana de debajo, y lo que ocupan las dos cosas encima de ella. */
    static final int TAB_H = 17, TAB_GAP = 3, TABS = TAB_H + TAB_GAP;
    /** Margen del texto a cada lado (se encoge a TAB_PAD_MIN si no caben), hueco entre pestañas y ancho mínimo. */
    private static final int TAB_PAD = 9, TAB_PAD_MIN = 6, TAB_SPACE = 3, TAB_MIN = 34;

    /**
     * Pestaña (1.4.1): una pastilla de 17 de alto con el texto centrado y aire arriba y abajo (nunca toca los bordes),
     * separada de la ventana por TAB_GAP. La elegida (state 2), del color de la app con su contorno, lleva una flechita
     * que baja hasta la ventana (se ve de qué es la cabecera sin pegar las letras a ella); las demás, de cristal claro,
     * y al pasar el ratón (state 1) suben 1, se aclaran y se marcan con una línea del color de la app.
     */
    static void tab(GuiGraphics g, int x, int y, int w, String label, int state, int acc) {
        int c = deep(acc);
        if (state == 2) {
            int edge = darken(c, 0.5F), low = darken(c, 0.78F);
            PadUi.box(g, x, y, w, TAB_H, edge);
            g.fillGradient(x + 1, y + 1, x + w - 1, y + TAB_H - 1, mix(c, WHITE, 0.30F), darken(c, 0.9F));
            g.fill(x + 2, y + 1, x + w - 2, y + 2, mix(c, WHITE, 0.62F));
            g.fill(x + 1, y + TAB_H - 2, x + w - 1, y + TAB_H - 1, low);
            // la flechita: abre el borde de abajo y baja en punta hasta la ventana
            int cx = x + w / 2;
            g.fill(cx - 1, y + TAB_H - 1, cx + 2, y + TAB_H, low);
            for (int i = 0; i < TAB_GAP; i++) {
                int hw = 2 - i;
                g.fill(cx - hw, y + TAB_H + i, cx + hw + 1, y + TAB_H + i + 1, edge);
                if (hw > 0) g.fill(cx - hw + 1, y + TAB_H + i, cx + hw, y + TAB_H + i + 1, low);
            }
            PadFont.drawShadowCentered(g, label, x + w / 2, y + 4, 0xFFFFFF, darken(c, 0.42F));
            return;
        }
        boolean hover = state == 1;
        int yy = hover ? y - 1 : y;
        g.fill(x + 1, y + TAB_H, x + w - 1, y + TAB_H + 1, hover ? 0x3C18265C : 0x2418265C);
        PadUi.box(g, x, yy, w, TAB_H, hover ? mix(c, PadUi.INK, 0.25F) : 0xFF7F9CC8);
        g.fillGradient(x + 1, yy + 1, x + w - 1, yy + TAB_H - 1, WHITE, hover ? mix(0xFFE4EEF9, PadUi.tint(c, 0.35F), 0.55F) : 0xFFD3E3F4);
        g.fill(x + 1, yy + TAB_H - 2, x + w - 1, yy + TAB_H - 1, hover ? mix(c, WHITE, 0.25F) : 0xFFBACFE6);
        PadFont.drawCentered(g, label, x + w / 2, yy + 4, hover ? ink(c) & 0xFFFFFF : 0x3A5888, false);
    }

    /**
     * La misma pestaña nueva en el sitio de las de carpeta (15 de alto, la ventana empieza en y + 14): Comunidad y
     * Música. Pastilla de 14 (2 por encima) con el texto centrado y aire; la elegida, del color de la app con una muesca que baja a la
     * ventana; las demás, de cristal claro, y con el ratón suben 1 y se marcan con el color de la app.
     */
    static void tabCompact(GuiGraphics g, int x, int y, int w, String label, int state, int acc) {
        int c = deep(acc), h = 14;
        y -= 2; // 2 más arriba: deja 2 de aire hasta la ventana (que empieza en y + 14)
        if (state == 2) {
            int edge = darken(c, 0.5F), low = darken(c, 0.78F);
            PadUi.box(g, x, y, w, h, edge);
            g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, mix(c, WHITE, 0.30F), darken(c, 0.9F));
            g.fill(x + 2, y + 1, x + w - 2, y + 2, mix(c, WHITE, 0.62F));
            int cx = x + w / 2;
            g.fill(cx - 2, y + h, cx + 3, y + h + 1, edge);
            g.fill(cx - 1, y + h + 1, cx + 2, y + h + 2, edge);
            g.fill(cx - 1, y + h - 1, cx + 2, y + h, low);
            if (!label.isEmpty()) PadFont.drawShadowCentered(g, label, cx, y + 3, 0xFFFFFF, darken(c, 0.42F));
            return;
        }
        boolean hover = state == 1;
        int yy = hover ? y - 1 : y;
        PadUi.box(g, x, yy, w, h, hover ? mix(c, PadUi.INK, 0.25F) : 0xFF7F9CC8);
        g.fillGradient(x + 1, yy + 1, x + w - 1, yy + h - 1, WHITE, hover ? mix(0xFFE4EEF9, PadUi.tint(c, 0.35F), 0.55F) : 0xFFD3E3F4);
        g.fill(x + 1, yy + h - 2, x + w - 1, yy + h - 1, hover ? mix(c, WHITE, 0.25F) : 0xFFBACFE6);
        if (!label.isEmpty()) PadFont.drawCentered(g, label, x + w / 2, yy + 3, hover ? ink(c) & 0xFFFFFF : 0x3A5888, false);
    }

    /**
     * Una fila de pestañas (las de PadViewPage y el Gachapón) desde (x, y) en room de ancho: cada una con TAB_PAD de
     * margen; si no caben, con menos margen y, si aún no, con el texto recortado. La elegida se dibuja la última.
     * Devuelve {x, ancho} de cada una, para los clics.
     */
    static int[][] tabRow(GuiGraphics g, TFPadScreen pad, int x, int y, int room, java.util.List<String> labels, int selected,
                          double mx, double my, int acc) {
        int n = labels.size();
        int pad2 = TAB_PAD * 2, total = tabsWidth(labels, pad2, Integer.MAX_VALUE);
        if (total > room) {
            pad2 = TAB_PAD_MIN * 2;
            total = tabsWidth(labels, pad2, Integer.MAX_VALUE);
        }
        int maxLabel = total > room ? Math.max(12, (room - TAB_SPACE * (n - 1)) / Math.max(1, n) - pad2) : Integer.MAX_VALUE;
        int[][] out = new int[n][];
        String[] shown = new String[n];
        int tx = x;
        for (int i = 0; i < n; i++) {
            shown[i] = PadFont.fit(labels.get(i), maxLabel);
            int w = Math.max(total > room ? 0 : TAB_MIN, PadFont.width(shown[i]) + pad2);
            out[i] = new int[] {tx, w};
            tx += w + TAB_SPACE;
        }
        for (int i = 0; i < n; i++) {
            if (i == selected) continue;
            boolean hover = PadUi.inside(mx, my, out[i][0], y, out[i][1], TAB_H);
            if (hover) pad.hover("§tab" + i + labels.get(i));
            tab(g, out[i][0], y, out[i][1], shown[i], hover ? 1 : 0, acc);
        }
        if (selected >= 0 && selected < n) tab(g, out[selected][0], y, out[selected][1], shown[selected], 2, acc);
        return out;
    }

    private static int tabsWidth(java.util.List<String> labels, int pad2, int maxLabel) {
        int total = 0;
        for (String l : labels) total += Math.max(TAB_MIN, PadFont.width(PadFont.fit(l, maxLabel)) + pad2);
        return total + TAB_SPACE * Math.max(0, labels.size() - 1);
    }

    /** Botones por estilo de PadView: cara arriba, cara abajo, luz, sombra (como PadUi). */
    private static final int[][] BTN = {
            {0xFF5AB4FF, 0xFF2C74E4, 0xFFB4E0FF, 0xFF1A4AA8},   // azul
            {0xFFFFD650, 0xFFE8961A, 0xFFFFF2B0, 0xFFA05E0E},   // oro
            {0xFFFF6274, 0xFFC82038, 0xFFFFB4BC, 0xFF861428},   // rojo
            {0xFF68E886, 0xFF22A84C, 0xFFC4FFD0, 0xFF147034},   // verde
            {0xFFE6EDF6, 0xFFC7D3E3, 0xFFF7FAFD, 0xFFAEBCD0},   // desactivado: cristal claro (no parece roto)
            {0xFF97A3BF, 0xFF66728F, 0xFFC9D0E2, 0xFF434D69},   // gris
    };

    /**
     * Botón de caramelo de 16 de alto: la mitad de arriba más clara (brillo), sombra abajo. Al pasar el ratón sube 1 y
     * se aclara; pulsado (pressed), se hunde y se oscurece un momento.
     */
    static void button(GuiGraphics g, int x, int y, int w, String label, int style, boolean hover, boolean enabled, boolean pressed) {
        int[] c = BTN[!enabled ? 4 : style == PadView.GRAY ? 5 : Math.max(0, Math.min(3, style))];
        int top = c[0], bot = c[1];
        if (pressed && enabled) {
            top = darken(top, 0.86F);
            bot = darken(bot, 0.86F);
        } else if (hover && enabled) {
            top = lighten(top, 26);
            bot = lighten(bot, 26);
        }
        int lift = hover && enabled && !pressed ? 1 : 0, sink = pressed && enabled ? 1 : 0;
        int yy = y - lift + sink;
        if (sink == 0) g.fill(x + 1, y + 16, x + w - 1, y + 17, lift > 0 ? 0x4618265C : 0x2D18265C);
        PadUi.box(g, x, yy, w, 16 - sink, PadUi.INK);
        g.fillGradient(x + 1, yy + 1, x + w - 1, yy + 8, mix(top, WHITE, 0.22F), top);
        g.fillGradient(x + 1, yy + 8, x + w - 1, yy + 13 - sink, mix(top, bot, 0.55F), bot);
        g.fill(x + 2, yy + 1, x + w - 2, yy + 2, c[2]);
        g.fill(x + 1, yy + 13 - sink, x + w - 1, yy + 15 - sink, c[3]);
        int text = buttonText(style, enabled), shade = buttonShade(style, enabled);
        if (PadUi.isBack(label)) {
            int ax = x + 6, ay = yy + 7;
            for (int k = 1; k >= 0; k--) { // la flecha, con su sombra como el texto
                int col = k == 1 ? shade : 0xFF000000 | text;
                for (int i = 0; i < 4; i++) g.fill(ax + i, ay - i + k, ax + i + 1, ay + i + 1 + k, col);
                g.fill(ax + 1, ay + k, ax + 7, ay + 1 + k, col);
            }
            PadFont.drawShadowCentered(g, label, x + w / 2 + 4, yy + 2, text, shade);
        } else if (!label.isEmpty()) {
            PadFont.drawShadowCentered(g, label, x + w / 2, yy + 2, text, shade);
        }
    }

    /**
     * La sombra del texto de un botón (1.4.1: sombra de su color en vez del contorno azul noche, que se comía la cara
     * del botón y pegaba las tildes al borde).
     */
    static int buttonShade(int style, boolean enabled) {
        if (enabled && style == PadView.GOLD) return 0xFFFFF0B0; // en el de oro, letra oscura con luz debajo (grabada)
        int[] c = BTN[!enabled ? 4 : style == PadView.GRAY ? 5 : Math.max(0, Math.min(3, style))];
        return enabled ? darken(c[3], 0.78F) : 0xFFF7FAFD;
    }

    /** El color del texto de un botón (RGB): blanco; en el de oro, marrón oscuro (el blanco no se lee sobre el oro). */
    static int buttonText(int style, boolean enabled) {
        return !enabled ? 0x8696B4 : style == PadView.GOLD ? 0x5A3304 : 0xFFFFFF;
    }

    /**
     * Barra de progreso de 6: carril hundido, relleno verde (oro si está completa) con brillo arriba y un reflejo que
     * la recorre.
     */
    static void progress(GuiGraphics g, int x, int y, int w, float f) {
        f = Math.max(0, Math.min(1, f));
        PadUi.box(g, x, y, w, 6, 0xFF8FAACB);
        g.fill(x + 1, y + 1, x + w - 1, y + 5, 0xFFDCE7F3);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFBFD1E6);
        int fw = Math.round((w - 2) * f);
        if (fw <= 0) return;
        boolean full = f >= 1;
        g.fillGradient(x + 1, y + 1, x + 1 + fw, y + 5, full ? 0xFFFFE47A : 0xFF86F0A4, full ? 0xFFE08E14 : 0xFF1E9E46);
        g.fill(x + 1, y + 1, x + 1 + fw, y + 2, full ? 0xFFFFF6C8 : 0xFFDAFFE4);
        if (!PadSettings.animations) return;
        float p = (time() % 2.6F) / 2.6F;
        int sx = x + 1 + (int) (-12 + p * (fw + 24));
        // el reflejo: dos mitades con degradado (sube y baja), recortadas al relleno
        for (int half = 0; half < 2; half++) {
            int x0 = sx + half * 6, x1 = x0 + 6;
            int c0 = Math.max(x + 1, x0), c1 = Math.min(x + 1 + fw, x1);
            if (c1 <= c0) continue;
            float k0 = (c0 - x0) / 6F, k1 = (c1 - x0) / 6F;
            int a0 = half == 0 ? Math.round(110 * k0) : Math.round(110 * (1 - k0));
            int a1 = half == 0 ? Math.round(110 * k1) : Math.round(110 * (1 - k1));
            hgrad(g, c0, y + 2, c1, y + 5, alpha(0xFFFFFF, a0), alpha(0xFFFFFF, a1));
        }
    }

    /** El hueco de un icono, del color de su fila (color != 0) o azul hielo, con una luz detrás. */
    static void medallion(GuiGraphics g, int x, int y, int s, int color) {
        int edge = color != 0 ? PadUi.tint(color, 0.62F) : 0xFF9DBCE0;
        int top = color != 0 ? PadUi.tint(color, 0.10F) : 0xFFF4F9FE, bot = color != 0 ? PadUi.tint(color, 0.30F) : 0xFFD6E6F6;
        PadUi.box(g, x, y, s, s, edge);
        g.fillGradient(x + 1, y + 1, x + s - 1, y + s - 1, top, bot);
        g.fill(x + 2, y + 1, x + s - 2, y + 2, WHITE);
        glow(g, x + s / 2F, y + s / 2F, s * 0.55F, 0xFFFFFF, 150);
    }

    /**
     * Tarjeta blanca en relieve con su sombra. state: 0 normal, 1 ratón encima (sube 1, borde del color de la app),
     * 2 marcada (oro y cálida). edge: el borde normal (0: azul claro). Devuelve la y a la que quedó (subida o no).
     */
    static int card(GuiGraphics g, int x, int y, int w, int h, int state, int acc, int edge) {
        int lift = state == 1 ? 1 : 0, yy = y - lift;
        g.fill(x + 1, y + h, x + w - 1, y + h + 1, state == 1 ? 0x4B18265C : 0x3018265C);
        if (state == 1) g.fill(x + 3, y + h + 1, x + w - 3, y + h + 2, 0x1C18265C);
        int e = state == 2 ? 0xFFE0A030 : state == 1 ? mix(acc, PadUi.INK, 0.15F) : edge != 0 ? edge : 0xFFC2D6EC;
        int top = state == 2 ? 0xFFFFFCEE : WHITE;
        int bot = state == 2 ? 0xFFFFEDBE : mix(0xFFEFF5FC, PadUi.tint(acc, 0.25F), state == 1 ? 0.25F : 0F);
        PadUi.box(g, x, yy, w, h, e);
        if (w > 2 && h > 2) g.fillGradient(x + 1, yy + 1, x + w - 1, yy + h - 1, top, bot);
        g.fill(x + 2, yy + 1, x + w - 2, yy + 2, WHITE);
        return yy;
    }

    /** Título de sección: barrita del color, texto en negrita y una línea que se apaga hacia la derecha. */
    static void section(GuiGraphics g, int x, int y, int w, String title, int color) {
        g.fill(x, y + 1, x + 3, y + 10, color);
        g.fill(x, y + 1, x + 3, y + 2, lighten(color, 80));
        g.drawString(PadUi.font(), PadUi.bold(title), x + 6, y + 2, darken(color, 0.8F), false);
        int lx = x + 6 + PadUi.boldWidth(title) + 5;
        if (lx < x + w - 8) {
            int line = PadUi.tint(color, 0.6F);
            hgrad(g, lx, y + 6, x + w - 4, y + 7, line, alpha(line, 0));
            hgrad(g, lx, y + 7, x + w - 4, y + 8, WHITE, 0x00FFFFFF);
        }
    }

    /** La barra de acciones abajo de la ventana. */
    static void dock(GuiGraphics g, int x, int y, int w, int h, int acc) {
        g.fill(x, y - 2, x + w, y - 1, mix(0xFFC6DAEE, acc, 0.25F));
        g.fill(x, y - 1, x + w, y, WHITE);
        g.fillGradient(x, y, x + w, y + h, 0xFFF1F7FD, mix(0xFFDCE9F6, PadUi.tint(acc, 0.3F), 0.3F));
    }

    /** Barra de desplazamiento de 4: carril claro y asa del color de la app. */
    static void scrollbar(GuiGraphics g, int x, int y, int h, int visible, int content, int scroll, int acc) {
        if (content <= visible || h <= 4) return;
        PadUi.box(g, x, y, 4, h, 0xFFD6E4F2);
        g.fill(x + 1, y + 1, x + 3, y + h - 1, 0xFFE8F0F8);
        int th = Math.max(10, h * visible / content);
        int ty = y + (h - th) * scroll / Math.max(1, content - visible);
        PadUi.box(g, x, ty, 4, th, darken(acc, 0.7F));
        g.fillGradient(x + 1, ty + 1, x + 3, ty + th - 1, mix(acc, WHITE, 0.45F), acc);
    }

    /**
     * Interruptor de 28x14: carril verde (encendido) o gris (apagado) con «SÍ» o «NO» y la bola blanca, que va de un
     * lado a otro (k: 0 apagado, 1 encendido; entre medias mientras se mueve).
     */
    static void toggle(GuiGraphics g, int x, int y, float k, boolean hover) {
        int top = mix(0xFFC9D5E6, 0xFF7CEC98, k), bot = mix(0xFFA8B7CC, 0xFF22A84C, k);
        if (hover) {
            top = lighten(top, 18);
            bot = lighten(bot, 18);
        }
        g.fill(x + 1, y + 14, x + 27, y + 15, 0x3218265C);
        PadUi.box(g, x, y, 28, 14, mix(0xFF6E80A0, 0xFF147034, k));
        g.fillGradient(x + 1, y + 1, x + 27, y + 13, bot, top);
        g.fill(x + 2, y + 1, x + 26, y + 2, 0x30000000);
        if (k > 0.5F) PadFont.drawShadow(g, "SÍ", x + 4, y + 2, 0xFFFFFF, 0xFF0F5A26);
        else PadFont.drawShadow(g, "NO", x + 27 - 4 - PadFont.width("NO"), y + 2, 0xFFFFFF, 0xFF56637E);
        int kx = x + 1 + Math.round(k * 14);
        g.fill(kx + 1, y + 13, kx + 12, y + 14, 0x4018265C);
        PadUi.box(g, kx, y + 1, 13, 12, mix(0xFF8090B0, 0xFF1E7C3A, k));
        g.fillGradient(kx + 1, y + 2, kx + 12, y + 12, WHITE, 0xFFDCE6F2);
        g.fill(kx + 2, y + 2, kx + 11, y + 3, WHITE);
    }

    /** Deslizador de ancho w y 14 de alto: carril hundido, relleno del color, y el tirador (f de 0 a 1). */
    static void slider(GuiGraphics g, int x, int y, int w, float f, int acc, boolean hover) {
        f = Math.max(0, Math.min(1, f));
        int ty = y + 4;
        PadUi.box(g, x, ty, w, 6, 0xFF8FAACB);
        g.fill(x + 1, ty + 1, x + w - 1, ty + 5, 0xFFDCE7F3);
        g.fill(x + 1, ty + 1, x + w - 1, ty + 2, 0xFFBFD1E6);
        int fw = Math.round((w - 2) * f);
        if (fw > 0) {
            g.fillGradient(x + 1, ty + 1, x + 1 + fw, ty + 5, mix(acc, WHITE, 0.45F), acc);
            g.fill(x + 1, ty + 1, x + 1 + fw, ty + 2, mix(acc, WHITE, 0.7F));
        }
        // marcas cada 20 %
        for (int i = 1; i < 5; i++) g.fill(x + (w - 1) * i / 5, ty + 6, x + (w - 1) * i / 5 + 1, ty + 8, 0x5018265C);
        int kx = x + Math.round((w - 9) * f);
        if (hover) glow(g, kx + 4.5F, y + 7, 10, mix(acc, WHITE, 0.4F) & 0xFFFFFF, 140);
        g.fill(kx + 1, y + 14, kx + 8, y + 15, 0x4018265C);
        PadUi.box(g, kx, y, 9, 14, darken(acc, 0.6F));
        g.fillGradient(kx + 1, y + 1, kx + 8, y + 13, WHITE, mix(acc, WHITE, 0.7F));
        g.fill(kx + 3, y + 4, kx + 4, y + 10, mix(acc, WHITE, 0.3F));
        g.fill(kx + 5, y + 4, kx + 6, y + 10, mix(acc, WHITE, 0.3F));
    }

    /** «Cargando»: cuatro puntos blancos en rueda (sobre la cabecera de color). */
    static void spinner(GuiGraphics g, int cx, int y) {
        int step = (int) (System.currentTimeMillis() / 140 % 4);
        int[][] at = {{0, -3}, {3, 0}, {0, 3}, {-3, 0}};
        for (int i = 0; i < 4; i++) {
            int c = i == step ? WHITE : 0x6EFFFFFF;
            g.fill(cx + at[i][0] - 1, y + 4 + at[i][1] - 1, cx + at[i][0] + 1, y + 4 + at[i][1] + 1, c);
        }
    }

    /**
     * Nada que enseñar: el icono de la app flotando en su luz con destellos y el texto debajo, centrado en (cx, mid).
     * room: el alto que hay (sin sitio, solo el texto).
     */
    static void emptyState(GuiGraphics g, TFPadScreen pad, int cx, int mid, int w, String text, int acc, String icon, int room) {
        java.util.List<net.minecraft.util.FormattedCharSequence> lines = PadUi.font().split(net.minecraft.network.chat.Component.literal(text), w);
        int nl = Math.min(6, lines.size());
        if (icon != null && room > 60) {
            boolean big = room > 110;
            int r = big ? 27 : 19;
            int iy = mid - (big ? 26 : 18) - nl * 5;
            int bob = Math.round((float) Math.sin(time() * 2.2F) * 1.5F);
            glow(g, cx, iy, r * 1.9F, PadUi.tint(acc, 0.55F) & 0xFFFFFF, 160);
            disc(g, cx, iy, r, PadUi.tint(acc, 0.22F));
            disc(g, cx, iy, r - 2, mix(acc, WHITE, 0.86F));
            glow(g, cx, iy - 3, r * 0.8F, 0xFFFFFF, 200);
            iconCrisp(g, pad, icon, cx, iy + bob, big ? 44 : 30);
            float k = r / 19F;
            int[][] sp = {{-26, -10}, {24, -14}, {20, 12}, {-22, 13}};
            for (int i = 0; i < sp.length; i++) {
                sparkle(g, (int) (cx + sp[i][0] * k), (int) (iy + sp[i][1] * k), 2, twinkle(i * 1.3F), PadUi.tint(acc, 0.6F) & 0xFFFFFF);
            }
            mid += big ? 22 : 14;
        }
        for (int i = 0; i < nl; i++) {
            int lw = PadUi.font().width(lines.get(i));
            g.drawString(PadUi.font(), lines.get(i), cx - lw / 2, mid - nl * 5 + i * 10, PadUi.MUTED, false);
        }
    }
}
