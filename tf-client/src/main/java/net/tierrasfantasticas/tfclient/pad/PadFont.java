package net.tierrasfantasticas.tfclient.pad;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.tierrasfantasticas.tfclient.TFClient;
import org.joml.Matrix4f;

/**
 * La fuente pixel del pad (4 px de ancho, mayúsculas, con tildes): font.png + font.json, generados por
 * tools/pad/build_pad.py. Cada letra ocupa una celda de 6x10: 2 filas para la tilde, 7 de letra y 1 libre.
 * Con contorno azul marino (1 px a los lados y arriba, 2 abajo) queda como los rótulos del marco.
 * <p>
 * 1.3.67 (fluidez): las letras ya no se pintan una a una (cada una era una llamada a la tarjeta gráfica, y con el
 * contorno, 12 por letra): todas, con su contorno, van en un lote de texto con el color en cada vértice, como la
 * fuente de Minecraft. Dentro del pad (que pinta en lote, ver TFPadScreen.render) se juntan con el resto.
 */
final class PadFont {
    static final int NAVY = 0x18265C;
    private static final ResourceLocation TEX = new ResourceLocation(TFClient.MOD_ID, "textures/gui/pad/font.png");
    private static final ResourceLocation META = new ResourceLocation(TFClient.MOD_ID, "textures/gui/pad/font.json");
    private static String chars = "";
    private static int[] widths = new int[0];
    private static int cols = 16, texW = 96, texH = 60;
    private static boolean loaded;

    private PadFont() {}

    private static void load() {
        if (loaded) return;
        loaded = true;
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(META);
        if (res.isEmpty()) return;
        try (Reader reader = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
            chars = o.get("chars").getAsString();
            JsonArray w = o.getAsJsonArray("widths");
            widths = new int[w.size()];
            for (int i = 0; i < widths.length; i++) widths[i] = w.get(i).getAsInt();
            cols = o.get("cols").getAsInt();
            texW = cols * 6;
            texH = ((chars.length() + cols - 1) / cols) * 10;
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Pad: no se pudo leer la fuente pixel", e);
        }
    }

    static String upper(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    static int width(String s) {
        load();
        int w = 0;
        for (char c : upper(s).toCharArray()) {
            int i = chars.indexOf(c);
            w += (i >= 0 ? widths[i] : 2) + 1;
        }
        return Math.max(0, w - 1);
    }

    /** Dibuja el texto; y es la parte de arriba de la celda (la letra empieza 2 más abajo). */
    static void draw(GuiGraphics g, String s, int x, int y, int rgb, boolean outline) {
        draw(g, s, x, y, rgb, outline ? NAVY : -1);
    }

    /** Con el contorno de otro color (RGB sin alfa; negativo: sin contorno): las pestañas elegidas, del color de su app. */
    static void draw(GuiGraphics g, String s, int x, int y, int rgb, int outline) {
        load();
        String text = upper(s);
        VertexConsumer vc = begin(g);
        Matrix4f m = front(g);
        if (outline >= 0) {
            int o = 0xFF000000 | outline;
            for (int[] d : OUTLINE) glyphs(vc, m, text, x + d[0], y + d[1], o);
        }
        glyphs(vc, m, text, x, y, 0xFF000000 | rgb);
        end(g);
    }

    /**
     * (1.3.103) Las letras, 1 por delante del recuadro sobre el que van: así ningún recuadro las tapa aunque se pinte
     * después. Con mods que juntan y reordenan lo que se pinta en las pantallas (los de optimización), los rectángulos
     * del pad salían al final y tapaban las etiquetas de las pestañas y los botones («el pad perdió sus letras»); el
     * título, sobre una textura, sí se veía. Las ventanas que se abren encima van 50-400 más arriba: no se transparentan.
     */
    private static Matrix4f front(GuiGraphics g) {
        // (1.3.106) en el pad, su escalón en el orden de pintado, como todo (TFPadScreen.nextOrder): 1 por delante
        // tapaba también lo que va encima de ellas
        return new Matrix4f(g.pose().last().pose()).translate(0F, 0F, TFPadScreen.liftOn() ? TFPadScreen.nextOrder() : 1F);
    }

    private static final int[][] OUTLINE = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {1, 1}, {-1, 2}, {0, 2}, {1, 2}, {0, 1}};

    /** Con una sombra de color 1 abajo (más ligera que el contorno): el texto de los botones y las pestañas elegidas. */
    static void drawShadowCentered(GuiGraphics g, String s, int cx, int y, int rgb, int shadow) {
        drawShadow(g, s, cx - width(s) / 2, y, rgb, shadow);
    }

    static void drawShadow(GuiGraphics g, String s, int x, int y, int rgb, int shadow) {
        load();
        String text = upper(s);
        VertexConsumer vc = begin(g);
        Matrix4f m = front(g);
        int sh = 0xFF000000 | shadow;
        glyphs(vc, m, text, x, y + 1, sh);
        glyphs(vc, m, text, x + 1, y + 1, sh);
        glyphs(vc, m, text, x, y, 0xFF000000 | rgb);
        end(g);
    }

    private static RenderType type;

    /**
     * El lote de texto de la fuente. Como antes, el color es solo el de las letras: si alguien dejó el pincel teñido o
     * transparente (setColor), se vuelve a blanco (lo de antes ya se pintó con el suyo).
     */
    private static VertexConsumer begin(GuiGraphics g) {
        float[] c = RenderSystem.getShaderColor();
        if (c[0] != 1F || c[1] != 1F || c[2] != 1F || c[3] != 1F) g.setColor(1F, 1F, 1F, 1F);
        if (type == null) type = PadRenderTypes.font(TEX);
        return g.bufferSource().getBuffer(type);
    }

    private static void end(GuiGraphics g) {
        if (!PadSkin.batching) g.flush();
    }

    /** ¿Tiene alguna letra que la fuente pixel sepa dibujar? (si no, saldría en blanco). */
    static boolean drawable(String s) {
        load();
        for (char c : upper(s).toCharArray()) if (c != ' ' && chars.indexOf(c) >= 0) return true;
        return false;
    }

    /** Recorta el texto (letra pixel) por el final para que quepa en w píxeles. */
    static String fit(String s, int w) {
        String t = upper(s);
        if (width(t) <= w) return t;
        while (t.length() > 1 && width(t + ".") > w) t = t.substring(0, t.length() - 1);
        return t.trim() + ".";
    }

    static void drawCentered(GuiGraphics g, String s, int cx, int y, int rgb, boolean outline) {
        draw(g, s, cx - width(s) / 2, y, rgb, outline);
    }

    static void drawCentered(GuiGraphics g, String s, int cx, int y, int rgb, int outline) {
        draw(g, s, cx - width(s) / 2, y, rgb, outline);
    }

    private static void glyphs(VertexConsumer vc, Matrix4f m, String text, int x, int y, int argb) {
        int cx = x;
        int r = argb >> 16 & 255, gr = argb >> 8 & 255, b = argb & 255, a = argb >>> 24;
        for (int k = 0; k < text.length(); k++) {
            char c = text.charAt(k);
            int i = chars.indexOf(c);
            if (i < 0) {
                cx += 3;
                continue;
            }
            int w = widths[i];
            if (c != ' ') {
                float u0 = (i % cols) * 6F / texW, v0 = (i / cols) * 10F / texH, u1 = u0 + w / (float) texW, v1 = v0 + 10F / texH;
                int light = LightTexture.FULL_BRIGHT;
                vc.vertex(m, cx, y, 0).color(r, gr, b, a).uv(u0, v0).uv2(light).endVertex();
                vc.vertex(m, cx, y + 10, 0).color(r, gr, b, a).uv(u0, v1).uv2(light).endVertex();
                vc.vertex(m, cx + w, y + 10, 0).color(r, gr, b, a).uv(u1, v1).uv2(light).endVertex();
                vc.vertex(m, cx + w, y, 0).color(r, gr, b, a).uv(u1, v0).uv2(light).endVertex();
            }
            cx += w + 1;
        }
    }
}
