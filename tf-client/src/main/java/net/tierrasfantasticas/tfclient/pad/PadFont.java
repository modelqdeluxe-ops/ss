package net.tierrasfantasticas.tfclient.pad;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * La fuente pixel del pad (4 px de ancho, mayúsculas, con tildes): font.png + font.json, generados por
 * tools/pad/build_pad.py. Cada letra ocupa una celda de 6x10: 2 filas para la tilde, 7 de letra y 1 libre.
 * Con contorno azul marino (1 px a los lados y arriba, 2 abajo) queda como los rótulos del marco.
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
        load();
        String text = upper(s);
        if (outline) {
            tint(NAVY);
            for (int[] d : new int[][] {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {1, 1}, {-1, 2}, {0, 2}, {1, 2}, {0, 1}}) {
                glyphs(g, text, x + d[0], y + d[1]);
            }
        }
        tint(rgb);
        glyphs(g, text, x, y);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    static void drawCentered(GuiGraphics g, String s, int cx, int y, int rgb, boolean outline) {
        draw(g, s, cx - width(s) / 2, y, rgb, outline);
    }

    private static void glyphs(GuiGraphics g, String text, int x, int y) {
        int cx = x;
        for (char c : text.toCharArray()) {
            int i = chars.indexOf(c);
            if (i < 0) {
                cx += 3;
                continue;
            }
            int w = widths[i];
            if (c != ' ') {
                RenderSystem.enableBlend();
                g.blit(TEX, cx, y, (i % cols) * 6, (i / cols) * 10, w, 10, texW, texH);
            }
            cx += w + 1;
        }
    }

    private static void tint(int rgb) {
        RenderSystem.setShaderColor((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F, 1F);
    }
}
