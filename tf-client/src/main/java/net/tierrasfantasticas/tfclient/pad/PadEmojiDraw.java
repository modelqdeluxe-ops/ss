/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Dibuja los emojis de {@link PadEmojis} nítidos a cualquier escala, como TFPadScreen.blitSharp pero por hojas de
 * atlas: la primera vez que hace falta una hoja a un tamaño (u unidades x píxeles reales por unidad), se reduce entera
 * en segundo plano con media por área (las celdas caen en píxeles exactos: no se mezclan) y luego se pinta 1 píxel de
 * textura = 1 píxel real. Si se pinta más grande que el original (pantallas 4K), la hoja original con filtro suave.
 * Los animados eligen el fotograma por la hora. También los textos con emojis (:fuego:, o 🔥 escrito tal cual).
 */
final class PadEmojiDraw {
    /** Una hoja lista: celdas de cell píxeles, w x h la textura. */
    private record Sheet(ResourceLocation loc, int cell, int cols, int w, int h) {}

    /** «s3_36» (hoja 3 de los estáticos a 36 px por celda; 0 = la original) → hoja lista. */
    private static final Map<String, Sheet> READY = new HashMap<>();
    /** Hojas que se están preparando o que no se pudieron leer (no se reintenta en esta sesión). */
    private static final Set<String> PENDING = new HashSet<>();
    private static final Set<String> BROKEN = new HashSet<>();

    private PadEmojiDraw() {}

    /** Píxeles reales por unidad de una página del pad a escala (las apps: Comunidad). */
    static int px(TFPadScreen pad) {
        return Math.max(1, pad.bigPx());
    }

    /** Pinta el emoji en u x u unidades. false si su hoja aún se está preparando (sale al fotograma siguiente). */
    static boolean draw(GuiGraphics g, PadEmojis.Emoji e, int x, int y, int u, int pxPerUnit) {
        if (e == null || u <= 0) return false;
        PadEmojis.Page page = PadEmojis.page(e);
        if (page == null) return false;
        int want = u * pxPerUnit;
        int size = want < page.cell() ? want : 0;
        String key = (e.animated() ? "a" : "s") + e.page() + "_" + size;
        Sheet s = READY.get(key);
        if (s == null) {
            prepare(key, page, size);
            return false;
        }
        int cell = e.cell() + (e.animated() ? frame(e) : 0);
        int sx = cell % s.cols * s.cell, sy = cell / s.cols * s.cell;
        RenderSystem.enableBlend();
        g.blit(s.loc, x, y, u, u, sx, sy, s.cell, s.cell, s.w, s.h);
        return true;
    }

    /** El fotograma que toca ahora (todos los iguales van a la par, como en Discord). */
    static int frame(PadEmojis.Emoji e) {
        if (!e.animated() || e.frames() <= 1) return 0;
        return (int) (Util.getMillis() / Math.max(16, e.frameMs()) % e.frames());
    }

    private static void prepare(String key, PadEmojis.Page page, int size) {
        if (PENDING.contains(key) || BROKEN.contains(key)) return;
        PENDING.add(key);
        ResourceLocation src = new ResourceLocation(TFClient.MOD_ID, "textures/gui/emoji/" + page.file() + ".png");
        Util.backgroundExecutor().execute(() -> {
            NativeImage out = null;
            try {
                Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(src);
                if (res.isPresent()) {
                    try (InputStream in = res.get().open()) {
                        NativeImage img = NativeImage.read(in);
                        if (size == 0) {
                            out = img;
                        } else {
                            try (img) {
                                out = reduce(img, page.cell(), size);
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                TFClient.LOGGER.warn("Emojis: no se pudo leer {}", src, ex);
            }
            NativeImage done = out;
            Minecraft.getInstance().execute(() -> {
                PENDING.remove(key);
                if (done == null) {
                    BROKEN.add(key);
                    return;
                }
                ResourceLocation loc = new ResourceLocation(TFClient.MOD_ID, "pad_emoji/" + key);
                DynamicTexture tex = new DynamicTexture(done);
                // la original solo se usa más grande que sus celdas: suavizada; las reducidas, píxel a píxel
                if (size == 0) tex.setFilter(true, false);
                Minecraft.getInstance().getTextureManager().register(loc, tex);
                int cell = size == 0 ? page.cell() : size;
                READY.put(key, new Sheet(loc, cell, page.cols(), done.getWidth(), done.getHeight()));
            });
        });
    }

    /** La hoja con cada celda de cell px reducida a px (media por área con el color pesado por su alfa). */
    private static NativeImage reduce(NativeImage src, int cell, int px) {
        int cols = src.getWidth() / cell, rows = src.getHeight() / cell;
        int ow = cols * px, oh = rows * px;
        NativeImage out = new NativeImage(ow, oh, true);
        double k = cell / (double) px;
        int sw = src.getWidth(), sh = src.getHeight();
        for (int oy = 0; oy < oh; oy++) {
            double y0 = oy * k, y1 = y0 + k;
            for (int ox = 0; ox < ow; ox++) {
                double x0 = ox * k, x1 = x0 + k;
                double area = 0, a = 0, c0 = 0, c1 = 0, c2 = 0;
                for (int sy = (int) y0; sy < Math.min(sh, Math.ceil(y1)); sy++) {
                    double wy = Math.min(y1, sy + 1) - Math.max(y0, sy);
                    for (int sx = (int) x0; sx < Math.min(sw, Math.ceil(x1)); sx++) {
                        double w = wy * (Math.min(x1, sx + 1) - Math.max(x0, sx));
                        if (w <= 0) continue;
                        int p = src.getPixelRGBA(sx, sy);
                        double al = (p >>> 24) * w;
                        area += w;
                        a += al;
                        c0 += (p & 255) * al;
                        c1 += (p >> 8 & 255) * al;
                        c2 += (p >> 16 & 255) * al;
                    }
                }
                int pa = area > 0 ? (int) Math.round(a / area) : 0;
                int r0 = a > 0 ? (int) Math.round(c0 / a) : 0, r1 = a > 0 ? (int) Math.round(c1 / a) : 0,
                        r2 = a > 0 ? (int) Math.round(c2 / a) : 0;
                out.setPixelRGBA(ox, oy, Math.min(255, pa) << 24 | Math.min(255, r2) << 16 | Math.min(255, r1) << 8 | Math.min(255, r0));
            }
        }
        return out;
    }

    /** Al salir del servidor: fuera las hojas (se vuelven a preparar si hacen falta). */
    static void clear() {
        for (Sheet s : READY.values()) Minecraft.getInstance().getTextureManager().release(s.loc);
        READY.clear();
        BROKEN.clear();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Textos con emojis (el texto de las fotos)
    // ---------------------------------------------------------------------------------------------------------------

    /** Lado de un emoji dentro de una línea de texto (las líneas miden 10). */
    static final int INLINE = 9;

    /** Un trozo: texto o un emoji. */
    private record Seg(String text, PadEmojis.Emoji emoji) {
        int width(Font f) {
            return emoji != null ? INLINE + 1 : f.width(text);
        }
    }

    /** Parte el texto en trozos: :atajo: conocido o emoji escrito tal cual → emoji; lo demás, texto. */
    private static List<Seg> parse(String s) {
        List<Seg> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        int[] len = new int[1];
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ':') {
                int j = s.indexOf(':', i + 1);
                if (j > i + 1 && j - i - 1 <= PadEmojis.ID_MAX && s.substring(i + 1, j).indexOf(' ') < 0) {
                    PadEmojis.Emoji e = PadEmojis.byName(s.substring(i + 1, j));
                    if (e != null) {
                        flush(out, buf);
                        out.add(new Seg(null, e));
                        i = j + 1;
                        continue;
                    }
                }
            } else if (c > 127 || (i + 1 < s.length() && (s.charAt(i + 1) == '️' || s.charAt(i + 1) == '⃣'))) {
                PadEmojis.Emoji e = PadEmojis.unicodeAt(s, i, len);
                if (e != null) {
                    flush(out, buf);
                    out.add(new Seg(null, e));
                    i += len[0];
                    continue;
                }
            }
            buf.append(c);
            i++;
        }
        flush(out, buf);
        return out;
    }

    private static void flush(List<Seg> out, StringBuilder buf) {
        if (buf.length() > 0) out.add(new Seg(buf.toString(), null));
        buf.setLength(0);
    }

    /** ¿Tiene algún emoji? (si no, el texto se pinta como siempre). */
    static boolean hasEmoji(String s) {
        for (Seg g : parse(s)) if (g.emoji != null) return true;
        return false;
    }

    /**
     * Texto con emojis partido en líneas de ancho w (de 10 de alto), como PadUi.wrapEllipsis: si no cabe en maxLines,
     * la última acaba en «…». Devuelve cuántas líneas ocupó (g null = solo medir).
     */
    static int wrap(GuiGraphics g, String s, int x, int y, int w, int color, int maxLines, int pxPerUnit) {
        Font f = PadUi.font();
        int space = f.width(" ");
        // palabras (separadas por espacios); una palabra más ancha que la línea se parte letra a letra
        List<List<Seg>> lines = new ArrayList<>();
        List<Seg> line = new ArrayList<>();
        int lw = 0;
        for (List<Seg> word : words(parse(s))) {
            int ww = 0;
            for (Seg sg : word) ww += sg.width(f);
            if (!line.isEmpty() && lw + space + ww > w) {
                lines.add(line);
                line = new ArrayList<>();
                lw = 0;
            }
            if (ww > w) {
                for (Seg piece : pieces(word)) {
                    int pw = piece.width(f);
                    if (!line.isEmpty() && lw + pw > w) {
                        lines.add(line);
                        line = new ArrayList<>();
                        lw = 0;
                    }
                    line.add(piece);
                    lw += pw;
                }
                continue;
            }
            if (!line.isEmpty()) {
                line.add(new Seg(" ", null));
                lw += space;
            }
            line.addAll(word);
            lw += ww;
        }
        if (!line.isEmpty()) lines.add(line);
        int n = Math.min(lines.size(), maxLines);
        if (g == null) return n;
        for (int i = 0; i < n; i++) {
            List<Seg> l = lines.get(i);
            if (i == n - 1 && lines.size() > maxLines) l = ellipsis(l, w, f);
            int cx = x;
            for (Seg sg : l) {
                if (sg.emoji != null) {
                    draw(g, sg.emoji, cx, y + i * 10 - 1, INLINE, pxPerUnit);
                } else {
                    PadUi.text(g, sg.text, cx, y + i * 10, color);
                }
                cx += sg.width(f);
            }
        }
        return n;
    }

    private static List<List<Seg>> words(List<Seg> segs) {
        List<List<Seg>> out = new ArrayList<>();
        List<Seg> cur = new ArrayList<>();
        for (Seg sg : segs) {
            if (sg.emoji != null) {
                cur.add(sg);
                continue;
            }
            String t = sg.text;
            int start = 0;
            for (int i = 0; i <= t.length(); i++) {
                if (i == t.length() || t.charAt(i) == ' ') {
                    if (i > start) cur.add(new Seg(t.substring(start, i), null));
                    if (i < t.length() && !cur.isEmpty()) {
                        out.add(cur);
                        cur = new ArrayList<>();
                    }
                    start = i + 1;
                }
            }
        }
        if (!cur.isEmpty()) out.add(cur);
        return out;
    }

    private static List<Seg> pieces(List<Seg> word) {
        List<Seg> out = new ArrayList<>();
        for (Seg sg : word) {
            if (sg.emoji != null) {
                out.add(sg);
            } else {
                sg.text.codePoints().forEach(cp -> out.add(new Seg(new String(Character.toChars(cp)), null)));
            }
        }
        return out;
    }

    /** La línea recortada para que quepa con «…» al final. */
    private static List<Seg> ellipsis(List<Seg> l, int w, Font f) {
        List<Seg> out = new ArrayList<>(pieces(l));
        int dots = f.width("…");
        while (!out.isEmpty()) {
            int tw = 0;
            for (Seg sg : out) tw += sg.width(f);
            if (tw + dots <= w) break;
            out.remove(out.size() - 1);
        }
        while (!out.isEmpty() && out.get(out.size() - 1).emoji == null && out.get(out.size() - 1).text.equals(" ")) out.remove(out.size() - 1);
        out.add(new Seg("…", null));
        return out;
    }
}
