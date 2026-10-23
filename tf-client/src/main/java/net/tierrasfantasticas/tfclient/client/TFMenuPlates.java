/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Placas del menú principal (textures/gui/menu_main.png + menu_main.json, de tools/gen_menu_buttons.py a partir de la
 * lámina del dueño). Cada placa se dibuja en el rectángulo de su cuerpo (el marco dorado, lo que se pulsa): la altura
 * fija la escala y, si el botón es más ancho que la placa, solo se estiran sus zonas lisas (madera, ladrillo y marco sin
 * tréboles ni gemas); faroles, gemas y tréboles quedan con su forma. Las piezas se juntan en píxeles reales enteros
 * para que no se vean costuras.
 */
public final class TFMenuPlates {
    private TFMenuPlates() {}

    public static final class Plate {
        /** Dónde está en la textura (x, y, ancho, alto). */
        final int u, v, w, h;
        /** Cuerpo y hueco del texto relativos a la placa (x0, y0, x1, y1), en píxeles de la textura. */
        final float[] body, text;
        /** Sitio del cuerpo en la lámina (x0, y0, x1, y1): la composición del menú. */
        public final float[] layout;
        /** Zonas lisas que se pueden estirar a lo ancho (x0, x1). */
        final float[][] fills;
        /** Gemas (x, y, radio) y faroles (x, y, radio), relativos a la placa. */
        final float[][] sparks, lights;
        /** El color de la placa (RGB): oscurecido, el contorno de sus letras blancas. */
        final int tint;
        /** Arriba y abajo de todo lo que se ve de la placa (gemas y tréboles incluidos), en y de la placa. */
        final float[] vis;

        Plate(JsonObject o) {
            int[] uv = ints(o.getAsJsonArray("uv"));
            u = uv[0]; v = uv[1]; w = uv[2]; h = uv[3];
            body = floats(o.getAsJsonArray("body"));
            text = floats(o.getAsJsonArray("text"));
            layout = floats(o.getAsJsonArray("layout"));
            fills = floats2(o.getAsJsonArray("fills"));
            sparks = floats2(o.getAsJsonArray("sparks"));
            lights = floats2(o.getAsJsonArray("lights"));
            tint = o.has("tint") ? Integer.parseInt(o.get("tint").getAsString(), 16) : 0x806040;
            vis = o.has("vis") ? floats(o.getAsJsonArray("vis")) : new float[] {0f, h};
        }

        public float bodyW() { return body[2] - body[0]; }

        /** La x (en la placa) que debe quedar en el centro del botón: la gema del centro si la hay, si no el centro. */
        float anchor() {
            float mid = (body[0] + body[2]) / 2f, sum = 0f;
            int n = 0;
            for (float[] g : sparks) {
                if (Math.abs(g[0] - mid) < bodyW() * 0.1f) {
                    sum += g[0];
                    n++;
                }
            }
            return n > 0 ? sum / n : mid;
        }
        public float bodyH() { return body[3] - body[1]; }
    }

    /** Dónde cae una placa en pantalla, en píxeles de interfaz con decimales. */
    public static final class Placed {
        final Plate plate;
        /** Escala vertical (interfaz por píxel de textura) y borde izquierdo y superior de la placa entera. */
        final float k, left, top;
        /** Bordes de las piezas en x de la textura y en x de la interfaz (redondeados a píxel real). */
        final float[] srcX, guiX;

        Placed(Plate p, float bx, float by, float bw, float bh, int s) {
            this.plate = p;
            this.k = bh / p.bodyH();
            // Las zonas lisas de cada lado de la gema del centro (o del centro del cuerpo) se estiran cada una lo suyo para
            // que esa gema caiga justo en el centro del botón: así las gemas de todas las filas quedan en el mismo eje
            float anchor = p.anchor();
            float fillL = 0f, fillR = 0f;
            for (float[] f : p.fills) {
                if (f[1] <= anchor) fillL += f[1] - f[0];
                else fillR += f[1] - f[0];
            }
            float fixedL = anchor - p.body[0] - fillL, fixedR = p.body[2] - anchor - fillR;
            float fillKL, fillKR;
            if (fillL > 0f && fillR > 0f) {
                fillKL = Math.max(k * 0.25f, (bw / 2f - fixedL * k) / fillL);
                fillKR = Math.max(k * 0.25f, (bw / 2f - fixedR * k) / fillR);
            } else {
                float fillTotal = fillL + fillR, extra = bw - p.bodyW() * k;
                fillKL = fillKR = fillTotal > 0f ? Math.max(k * 0.25f, k + extra / fillTotal) : k;
            }
            int n = p.fills.length * 2 + 2;
            srcX = new float[n];
            float[] scale = new float[n - 1];
            srcX[0] = 0f;
            int i = 1;
            for (float[] f : p.fills) {
                srcX[i] = f[0];
                scale[i - 1] = k;
                srcX[i + 1] = f[1];
                scale[i] = f[1] <= anchor ? fillKL : fillKR;
                i += 2;
            }
            srcX[n - 1] = p.w;
            scale[n - 2] = k;
            // Posición de cada borde respecto al borde izquierdo de la placa
            float[] rel = new float[n];
            for (int j = 1; j < n; j++) rel[j] = rel[j - 1] + (srcX[j] - srcX[j - 1]) * scale[j - 1];
            float bodyRel = at(rel, srcX, p.body[0]);
            this.left = bx - bodyRel;
            this.top = by - p.body[1] * k;
            guiX = new float[n];
            for (int j = 0; j < n; j++) guiX[j] = snap(left + rel[j], s);
        }

        /** x en pantalla de una x de la textura (relativa a la placa). */
        float x(float sx) {
            return at(guiX, srcX, sx);
        }

        float y(float sy) {
            return top + sy * k;
        }

        private static float at(float[] out, float[] in, float sx) {
            for (int j = 1; j < in.length; j++) {
                if (sx <= in[j] || j == in.length - 1) {
                    float t = (sx - in[j - 1]) / Math.max(1e-3f, in[j] - in[j - 1]);
                    return out[j - 1] + (out[j] - out[j - 1]) * t;
                }
            }
            return out[0];
        }
    }

    private static Map<String, Plate> plates;
    private static float[] composition; // top, bottom, center (en la lámina)
    private static int atlasW = 1, atlasH = 1;
    private static boolean failed;

    /** Lee menu_main.json la primera vez. */
    public static boolean ready() {
        if (plates != null) return TFTextures.MENU_MAIN.ready();
        if (failed) return false;
        try (InputStream in = TFResources.open("assets/" + TFClient.MOD_ID + "/textures/gui/menu_main.json")) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            int[] atlas = ints(root.getAsJsonArray("atlas"));
            atlasW = atlas[0];
            atlasH = atlas[1];
            JsonObject comp = root.getAsJsonObject("composition");
            composition = new float[] {comp.get("top").getAsFloat(), comp.get("bottom").getAsFloat(), comp.get("center").getAsFloat()};
            Map<String, Plate> map = new HashMap<>();
            for (Map.Entry<String, com.google.gson.JsonElement> e : root.getAsJsonObject("plates").entrySet()) {
                map.put(e.getKey(), new Plate(e.getValue().getAsJsonObject()));
            }
            plates = map;
        } catch (Throwable t) {
            failed = true;
            TFClient.LOGGER.error("TF Client: no se pudo leer menu_main.json", t);
            return false;
        }
        return TFTextures.MENU_MAIN.ready();
    }

    public static Plate get(String name) {
        return ready() ? plates.get(name) : null;
    }

    /** Arriba, abajo y centro de la composición en la lámina. */
    public static float[] composition() {
        return ready() ? composition : null;
    }

    /** Dibuja la placa entera, pieza a pieza, con la opacidad dada (el color lo pone quien llama con setColor). */
    public static void draw(GuiGraphics g, Placed at) {
        Plate p = at.plate;
        float y0 = at.top;
        float hh = p.h * at.k;
        for (int j = 1; j < at.srcX.length; j++) {
            float sx0 = at.srcX[j - 1], sx1 = at.srcX[j];
            float gx0 = at.guiX[j - 1], gx1 = at.guiX[j];
            if (sx1 - sx0 < 0.5f || gx1 - gx0 <= 0f) continue;
            int u0 = Math.round(sx0), uw = Math.max(1, Math.round(sx1) - u0);
            g.pose().pushPose();
            g.pose().translate(gx0, y0, 0f);
            g.pose().scale((gx1 - gx0) / uw, hh / p.h, 1f);
            g.blit(TFTextures.MENU_MAIN.id(), 0, 0, p.u + u0, p.v, uw, p.h, atlasW, atlasH);
            g.pose().popPose();
        }
    }

    static float snap(float gui, int s) {
        return Math.round(gui * s) / (float) s;
    }

    private static int[] ints(JsonArray a) {
        int[] out = new int[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsInt();
        return out;
    }

    private static float[] floats(JsonArray a) {
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsFloat();
        return out;
    }

    private static float[][] floats2(JsonArray a) {
        float[][] out = new float[a.size()][];
        for (int i = 0; i < out.length; i++) out[i] = floats(a.get(i).getAsJsonArray());
        return out;
    }
}
