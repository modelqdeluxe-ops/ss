package net.tierrasfantasticas.tfclient.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Iconos pequeños de los botones del menú principal. Se dibujan como formas (en un lienzo de 16x16 unidades) y se
 * rasterizan al tamaño exacto en píxeles reales de la escala de interfaz actual, con 4x4 muestras por píxel: nítidos y
 * suaves en cualquier escala, sin estirar ninguna imagen. Son blancos con opacidad y se tiñen al dibujarlos.
 * Las mismas formas están en el simulador (tools/menu_sim.py, icon_alpha).
 */
public final class TFMenuIcons {
    /** Tamaño de un icono en píxeles de interfaz. */
    public static final int SIZE = 9;
    private static final int SS = 4;

    public enum Icon { PLAY, WORLD, MODS, OPTIONS, QUIT, WEB, DISCORD }

    private static final Map<Integer, ResourceLocation> ATLAS = new HashMap<>();

    private TFMenuIcons() {}

    /** Dibuja el icono con su esquina en (x, y) en píxeles reales (pose ya escalada a 1/escala) y teñido de argb. */
    public static void draw(GuiGraphics g, Icon icon, int x, int y, int scale, int argb) {
        ResourceLocation id = atlas(scale);
        if (id == null) return;
        int n = SIZE * scale;
        int count = Icon.values().length;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f, ((argb >>> 24) & 255) / 255f);
        g.blit(id, x, y, n, n, (float) (icon.ordinal() * n), 0f, n, n, n * count, n);
        g.setColor(1f, 1f, 1f, 1f);
    }

    /** Todos los iconos en fila, rasterizados para esta escala (se hace una vez por escala). */
    private static ResourceLocation atlas(int scale) {
        if (ATLAS.containsKey(scale)) return ATLAS.get(scale);
        int n = SIZE * scale;
        Icon[] icons = Icon.values();
        try {
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, n * icons.length, n, true);
            for (Icon icon : icons) {
                for (int j = 0; j < n; j++) {
                    for (int i = 0; i < n; i++) {
                        float acc = 0f;
                        for (int sj = 0; sj < SS; sj++) {
                            for (int si = 0; si < SS; si++) {
                                float px = (i + (si + 0.5f) / SS) * 16f / n;
                                float py = (j + (sj + 0.5f) / SS) * 16f / n;
                                acc += alpha(icon, px, py);
                            }
                        }
                        int a = Math.round(acc / (SS * SS) * 255f);
                        image.setPixelRGBA(icon.ordinal() * n + i, j, (a << 24) | 0xFFFFFF);
                    }
                }
            }
            ResourceLocation id = new ResourceLocation(TFClient.MOD_ID, "dynamic/menu_icons_" + scale);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
            ATLAS.put(scale, id);
            return id;
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Client: no se pudieron crear los iconos del menú", t);
            ATLAS.put(scale, null);
            return null;
        }
    }

    // --------------------------------------------------------------------------------------------- formas

    private static float seg(float px, float py, float ax, float ay, float bx, float by) {
        float vx = bx - ax, vy = by - ay, wx = px - ax, wy = py - ay;
        float t = Math.max(0f, Math.min(1f, (wx * vx + wy * vy) / (vx * vx + vy * vy)));
        float dx = wx - vx * t, dy = wy - vy * t;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float box(float px, float py, float cx, float cy, float hx, float hy, float rr) {
        float qx = Math.abs(px - cx) - hx + rr, qy = Math.abs(py - cy) - hy + rr;
        float ox = Math.max(qx, 0f), oy = Math.max(qy, 0f);
        return (float) Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(qx, qy), 0f) - rr;
    }

    private static float circ(float px, float py, float cx, float cy, float r) {
        return (float) Math.hypot(px - cx, py - cy) - r;
    }

    /** Opacidad 0..1 del icono en el punto (px, py) del lienzo de 16x16. */
    static float alpha(Icon icon, float px, float py) {
        switch (icon) {
            case PLAY: { // triángulo de "jugar" con las puntas redondeadas
                float ax = 4.6f, ay = 3.2f, bx = 12.6f, by = 8f, cx = 4.6f, cy = 12.8f;
                boolean inside = (bx - ax) * (py - ay) - (by - ay) * (px - ax) >= 0
                        && (cx - bx) * (py - by) - (cy - by) * (px - bx) >= 0
                        && (ax - cx) * (py - cy) - (ay - cy) * (px - cx) >= 0;
                float d = Math.min(seg(px, py, ax, ay, bx, by), Math.min(seg(px, py, bx, by, cx, cy), seg(px, py, cx, cy, ax, ay)));
                return (inside ? -d : d) - 0.9f <= 0f ? 1f : 0f;
            }
            case WORLD: { // bloque en perspectiva: tapa entera, caras con dos tonos y una línea entre ellas
                float dx = Math.abs(px - 8f);
                if (dx / 6.2f + Math.abs(py - 5f) / 3.1f <= 1f) return 1f;
                if (dx <= 6f && py >= 5f && py <= 11f + (6f - dx) * 0.5f) {
                    if (dx < 0.55f) return 0f;
                    return px < 8f ? 0.62f : 0.36f;
                }
                return 0f;
            }
            case MODS: { // pieza de puzle
                float d = Math.min(box(px, py, 7f, 9f, 4.6f, 4.6f, 1f), Math.min(circ(px, py, 7f, 3.6f, 2f), circ(px, py, 12.9f, 9f, 2f)));
                d = Math.max(d, -circ(px, py, 2.4f, 9f, 1.7f));
                return d <= 0f ? 1f : 0f;
            }
            case OPTIONS: { // engranaje de 8 dientes
                float r = (float) Math.hypot(px - 8f, py - 8f);
                double ang = Math.atan2(py - 8f, px - 8f);
                double a = ang / (2 * Math.PI) * 8;
                a -= Math.floor(a);
                float outer = Math.abs(a - 0.5) < 0.22 ? 6.9f : 5.2f;
                return r >= 2.1f && r <= outer ? 1f : 0f;
            }
            case QUIT: { // botón de apagar
                float r = (float) Math.hypot(px - 8f, py - 8.6f);
                double ang = Math.toDegrees(Math.atan2(px - 8f, -(py - 8.6f))); // 0 = arriba
                boolean ring = Math.abs(r - 5.4f) <= 0.95f && Math.abs(ang) >= 38;
                boolean bar = seg(px, py, 8f, 1.6f, 8f, 7.6f) <= 0.95f;
                return ring || bar ? 1f : 0f;
            }
            case WEB: { // globo: círculo, meridiano y paralelos
                float r = (float) Math.hypot(px - 8f, py - 8f);
                boolean ring = Math.abs(r - 6f) <= 0.8f;
                boolean meridian = Math.abs(Math.hypot((px - 8f) / 2.6f, (py - 8f) / 6f) - 1f) * 2.6f <= 0.75f;
                boolean equator = Math.abs(py - 8f) <= 0.7f && r <= 6f;
                boolean lat = (Math.abs(py - 5f) <= 0.6f || Math.abs(py - 11f) <= 0.6f) && r <= 5.6f;
                return ring || meridian || equator || lat ? 1f : 0f;
            }
            case DISCORD: { // mando con dos ojos (silueta genérica de chat de voz)
                float d = Math.min(box(px, py, 8f, 8.4f, 6.6f, 4.4f, 3.2f), Math.min(circ(px, py, 4f, 4.6f, 1.8f), circ(px, py, 12f, 4.6f, 1.8f)));
                float eye = Math.min(box(px, py, 5.6f, 8.4f, 1.15f, 1.45f, 1.1f), box(px, py, 10.4f, 8.4f, 1.15f, 1.45f, 1.1f));
                d = Math.max(d, -eye);
                return d <= 0f ? 1f : 0f;
            }
            default:
                return 0f;
        }
    }
}
