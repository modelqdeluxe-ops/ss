/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * El fondo de pantalla del menú principal del pad (1.3.155, el dueño: «que pueda poner un fondo de pantalla cualquiera,
 * un png, que se autoajuste al tamaño de la pantalla del pad, bien dibujado y bien cuadrado; solo del menú principal; se
 * carga en su carpeta como las fotos o las canciones y lo puedo cambiar cuando quiera en ajustes; agrega más fondos, o
 * colores, los default»).
 * <ul>
 *   <li>Ajustes › Fondo del menú abre el selector ({@link PadWallpaperPage}): el del tema (el de siempre), los colores
 *   ({@link #COLORS}), los dibujados que trae el pad (tools/pad/fondos.py) y los PNG que el jugador mete en
 *   .minecraft/tfclient/fondos.</li>
 *   <li>Se dibuja «cubriendo» todo el cristal (como un fondo de escritorio): se escala hasta llenarlo sin deformarse y se
 *   recorta lo que sobra por igual a cada lado; el marco del pad, que va encima, le da su forma.</li>
 *   <li>Los PNG del jugador se leen una vez (en segundo plano) y, si son muy grandes, se reducen a 1920 de ancho.</li>
 * </ul>
 */
public final class PadWallpaper {
    /** Un fondo: id guardado en los ajustes, nombre, y su color (degradado arriba/abajo) o su imagen. */
    record Option(String id, String name, int top, int bottom, ResourceLocation image, Path file) {
        boolean theme() {
            return id.equals(THEME);
        }
    }

    static final String THEME = "tema";
    /** Colores de serie: degradados suaves de arriba abajo. */
    static final Option[] COLORS = {
            color("medianoche", "Medianoche", 0x141A46, 0x3A1F64),
            color("lavanda", "Lavanda", 0xC9B6FF, 0x8C6FE0),
            color("menta", "Menta", 0xB8F5DD, 0x4FC9A0),
            color("coral", "Coral", 0xFFB59E, 0xF0647C),
            color("oceano", "Océano", 0x5EC8FF, 0x1E4FA8),
            color("bosque", "Bosque", 0x7CC48A, 0x1F5A3A),
            color("grafito", "Grafito", 0x5A6070, 0x1E2128),
            color("chicle", "Rosa chicle", 0xFFC2E6, 0xFF6FBF),
            color("limon", "Limón", 0xFFF6A8, 0xF2C94C),
            color("cielo", "Cielo claro", 0xE0F4FF, 0x8FD0FF),
            color("fuego", "Fuego", 0xFFC24A, 0xD23A2A),
            color("hielo", "Hielo", 0xF2FBFF, 0xA9D8F2),
    };

    private static Option color(String id, String name, int top, int bottom) {
        return new Option("c:" + id, name, top, bottom, null, null);
    }

    private static List<Option> builtIn;
    private static List<Option> files = List.of();
    private static long scannedAt;
    /** Las texturas de los PNG del jugador ya cargadas: archivo → textura y su tamaño. */
    private static final Map<Path, Loaded> LOADED = new HashMap<>();
    private static final java.util.Set<Path> LOADING = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<Path> BROKEN = java.util.concurrent.ConcurrentHashMap.newKeySet();

    record Loaded(ResourceLocation loc, int w, int h) {}

    private PadWallpaper() {}

    /** .minecraft/tfclient/fondos: aquí se meten los PNG. */
    static Path dir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("tfclient").resolve("fondos");
    }

    /** Los dibujados que trae el pad (assets/tfclient/pad/fondos.json). */
    static List<Option> builtIn() {
        if (builtIn != null) return builtIn;
        List<Option> out = new ArrayList<>();
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation(TFClient.MOD_ID, "pad/fondos.json"));
        if (res.isPresent()) {
            try (InputStream in = res.get().open()) {
                JsonArray a = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
                for (JsonElement e : a) {
                    String id = e.getAsJsonObject().get("id").getAsString();
                    out.add(new Option("i:" + id, e.getAsJsonObject().get("nombre").getAsString(), 0, 0,
                            new ResourceLocation(TFClient.MOD_ID, "textures/gui/pad/fondos/" + id + ".png"), null));
                }
            } catch (Exception e) {
                TFClient.LOGGER.warn("TF Pad: no se pudo leer la lista de fondos", e);
            }
        }
        builtIn = out;
        return out;
    }

    /** Los PNG de la carpeta (se vuelven a mirar como mucho cada 2 s, o al pulsar RECARGAR). */
    static List<Option> files(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - scannedAt < 2000) return files;
        scannedAt = now;
        List<Option> out = new ArrayList<>();
        Path d = dir();
        if (Files.isDirectory(d)) {
            try (Stream<Path> s = Files.list(d)) {
                s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png") && Files.isRegularFile(p))
                        .sorted()
                        .limit(200)
                        .forEach(p -> {
                            String n = p.getFileName().toString();
                            out.add(new Option("f:" + n, n.substring(0, n.length() - 4), 0, 0, null, p));
                        });
            } catch (Exception e) {
                TFClient.LOGGER.warn("TF Pad: no se pudo mirar la carpeta de fondos", e);
            }
        }
        files = out;
        return out;
    }

    /** Todas las opciones, en el orden del selector: el del tema, tus fondos, los dibujados y los colores. */
    static List<Option> all() {
        List<Option> out = new ArrayList<>();
        out.add(new Option(THEME, "El del tema", 0, 0, null, null));
        out.addAll(files(false));
        out.addAll(builtIn());
        out.addAll(List.of(COLORS));
        return out;
    }

    /** El fondo elegido (el del tema si el elegido ya no existe, por ejemplo un PNG borrado). */
    static Option current() {
        String id = PadSettings.wallpaper;
        if (id == null || id.isEmpty() || id.equals(THEME)) return null;
        for (Option o : all()) if (o.id().equals(id)) return o;
        return null;
    }

    /** ¿Hay un fondo propio en el menú principal (no el del tema)? */
    static boolean active() {
        return current() != null;
    }

    /**
     * Dibuja el fondo cubriendo el rectángulo (x0, y0)-(x1, y1) (unidades del cristal). Devuelve false si aún no está
     * listo (un PNG que se está leyendo): entonces se dibuja el del tema.
     */
    static boolean draw(GuiGraphics g, Option o, float x0, float y0, float x1, float y1) {
        if (o == null || o.theme()) return false;
        if (o.image() == null && o.file() == null) {
            g.fillGradient((int) Math.floor(x0), (int) Math.floor(y0), (int) Math.ceil(x1), (int) Math.ceil(y1), 0xFF000000 | o.top(),
                    0xFF000000 | o.bottom());
            return true;
        }
        ResourceLocation loc;
        int iw, ih;
        if (o.image() != null) {
            int[] sz = TFPadScreen.size(o.image());
            if (sz[0] == 0) return false;
            loc = o.image();
            iw = sz[0];
            ih = sz[1];
        } else {
            Loaded l = load(o.file());
            if (l == null) return false;
            loc = l.loc();
            iw = l.w();
            ih = l.h();
        }
        cover(g, loc, iw, ih, x0, y0, x1 - x0, y1 - y0, 1F);
        return true;
    }

    /** Una imagen «cubriendo» el rectángulo: escalada hasta llenarlo sin deformarse, recortada por igual a cada lado. */
    static void cover(GuiGraphics g, ResourceLocation loc, int iw, int ih, float x, float y, float w, float h, float alpha) {
        if (w <= 0 || h <= 0 || iw <= 0 || ih <= 0) return;
        float scale = Math.max(w / iw, h / ih);
        int uw = Math.max(1, Math.round(w / scale)), vh = Math.max(1, Math.round(h / scale));
        int u = (iw - uw) / 2, v = (ih - vh) / 2;
        RenderSystem.enableBlend();
        Minecraft.getInstance().getTextureManager().getTexture(loc).setFilter(true, false);
        g.setColor(1, 1, 1, alpha);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(w / uw, h / vh, 1);
        g.blit(loc, 0, 0, u, v, uw, vh, iw, ih);
        g.pose().popPose();
        g.setColor(1, 1, 1, 1);
    }

    /** La textura de un PNG del jugador: la primera vez se lee en segundo plano (null mientras tanto o si no se puede). */
    static Loaded load(Path file) {
        Loaded l = LOADED.get(file);
        if (l != null || BROKEN.contains(file) || !LOADING.add(file)) return l;
        net.minecraft.Util.backgroundExecutor().execute(() -> {
            NativeImage img = null;
            try (InputStream in = Files.newInputStream(file)) {
                NativeImage src = NativeImage.read(in);
                img = src;
                // los muy grandes, a 1920 de ancho (una foto 4K ocuparía mucha memoria de vídeo sin verse mejor)
                if (src.getWidth() > 1920) {
                    int w = 1920, h = Math.max(1, Math.round(src.getHeight() * 1920F / src.getWidth()));
                    NativeImage small = new NativeImage(w, h, false);
                    src.resizeSubRectTo(0, 0, src.getWidth(), src.getHeight(), small);
                    src.close();
                    img = small;
                }
                NativeImage ready = img;
                Minecraft.getInstance().execute(() -> {
                    DynamicTexture tex = PadCameraPage.smooth(new DynamicTexture(ready));
                    String key = Integer.toHexString(file.toString().hashCode()) + "_" + Integer.toHexString((int) (System.nanoTime() & 0xFFFFFF));
                    ResourceLocation loc = new ResourceLocation(TFClient.MOD_ID, "fondo_" + key);
                    Minecraft.getInstance().getTextureManager().register(loc, tex);
                    LOADED.put(file, new Loaded(loc, ready.getWidth(), ready.getHeight()));
                    LOADING.remove(file);
                });
            } catch (Exception e) {
                if (img != null) img.close();
                BROKEN.add(file);
                LOADING.remove(file);
                TFClient.LOGGER.warn("TF Pad: el fondo {} no es un PNG válido", file.getFileName(), e);
            }
        });
        return null;
    }

    /** ¿Ese PNG no se pudo abrir? */
    static boolean broken(Path file) {
        return BROKEN.contains(file);
    }

    /** Recargar: vuelve a mirar la carpeta y olvida los que fallaron (por si se cambiaron). */
    static void reload() {
        BROKEN.clear();
        for (Loaded l : LOADED.values()) Minecraft.getInstance().getTextureManager().release(l.loc());
        LOADED.clear();
        files(true);
    }
}
