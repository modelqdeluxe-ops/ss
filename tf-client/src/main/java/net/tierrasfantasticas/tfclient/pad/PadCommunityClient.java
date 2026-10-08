package net.tierrasfantasticas.tfclient.pad;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Comunidad en el cliente: la lista de publicaciones que manda el servidor, las fotos (se bajan una vez y se guardan en
 * .minecraft/tfclient/cache/comunidad) y la subida de una foto propia (recortada a 16:9 y reducida a 480x270).
 */
final class PadCommunityClient {
    static final int PHOTO_W = 480, PHOTO_H = 270;

    /** Lo que se ve: publicaciones cargadas de la pestaña actual (varias páginas seguidas). */
    static final List<PadCommunityNet.Post> POSTS = new ArrayList<>();
    static String tab = "recientes";
    static int loadedPage = -1, pages = 1, total;
    static boolean loading;

    private record Tex(ResourceLocation loc, DynamicTexture tex, int w, int h) {}

    private static final Map<String, Tex> TEXTURES = new LinkedHashMap<>(16, 0.75F, true);
    private static final Map<String, byte[][]> DOWNLOADS = new HashMap<>();
    private static final Set<String> ASKED = new HashSet<>();

    /** Subida en curso (para la barra de la Cámara). */
    static volatile String uploadStatus;
    static volatile float uploadProgress;

    private PadCommunityClient() {}

    static Path cacheDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("tfclient").resolve("cache").resolve("comunidad");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Lista
    // ---------------------------------------------------------------------------------------------------------------

    static void open(String newTab) {
        if (!newTab.equals(tab) || loadedPage < 0) {
            tab = newTab;
            POSTS.clear();
            loadedPage = -1;
            pages = 1;
        }
        request(0);
    }

    /** Vuelve a pedir lo cargado (tras un like o al borrar). */
    static void reload() {
        POSTS.clear();
        loadedPage = -1;
        request(0);
    }

    static void more() {
        if (!loading && loadedPage + 1 < pages) request(loadedPage + 1);
    }

    private static void request(int page) {
        loading = true;
        PadCommunityNet.toServer(new PadCommunityNet.FeedReq(tab, page));
    }

    static void feed(PadCommunityNet.Feed f) {
        if (!f.tab().equals(tab)) return;
        loading = false;
        pages = f.pages();
        total = f.total();
        if (f.page() == 0) POSTS.clear();
        if (f.page() == 0 || f.page() == loadedPage + 1) {
            // si vuelve la página 0 tras un like, se conserva la posición: se reemplaza lo cargado
            POSTS.addAll(f.posts());
            loadedPage = f.page();
        }
        if (Minecraft.getInstance().screen instanceof TFPadScreen pad && pad.page() instanceof PadCommunityPage p) p.updated();
    }

    static void act(String action, String id) {
        PadCommunityNet.toServer(new PadCommunityNet.Act(action, id, tab, 0));
        loadedPage = -1;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Fotos
    // ---------------------------------------------------------------------------------------------------------------

    /** La textura de una foto (null mientras se baja). */
    static ResourceLocation texture(String id) {
        Tex t = TEXTURES.get(id);
        if (t != null) return t.loc;
        if (!id.matches("[a-z0-9]{1,20}")) return null;
        Path cached = cacheDir().resolve(id + ".png");
        if (Files.exists(cached)) {
            try {
                return load(id, Files.readAllBytes(cached));
            } catch (Exception e) {
                TFClient.LOGGER.warn("Comunidad: foto en caché rota {}", id);
            }
        }
        if (ASKED.add(id)) PadCommunityNet.toServer(new PadCommunityNet.ImgReq(id));
        return null;
    }

    static int[] textureSize(String id) {
        Tex t = TEXTURES.get(id);
        return t == null ? new int[] {PHOTO_W, PHOTO_H} : new int[] {t.w, t.h};
    }

    static void image(PadCommunityNet.Img m) {
        if (m.total() <= 0 || m.total() > 20 || m.index() < 0 || m.index() >= m.total()) return;
        byte[][] parts = DOWNLOADS.computeIfAbsent(m.id(), k -> new byte[m.total()][]);
        if (parts.length != m.total()) return;
        parts[m.index()] = m.data();
        for (byte[] p : parts) if (p == null) return;
        DOWNLOADS.remove(m.id());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.writeBytes(p);
        byte[] png = out.toByteArray();
        try {
            Files.createDirectories(cacheDir());
            Files.write(cacheDir().resolve(m.id() + ".png"), png);
        } catch (Exception e) {
            TFClient.LOGGER.warn("Comunidad: no se pudo guardar una foto en la caché", e);
        }
        load(m.id(), png);
    }

    private static ResourceLocation load(String id, byte[] png) {
        try (InputStream in = new ByteArrayInputStream(png)) {
            NativeImage img = NativeImage.read(in);
            DynamicTexture tex = new DynamicTexture(img);
            ResourceLocation loc = new ResourceLocation(TFClient.MOD_ID, "comunidad/" + id);
            Minecraft.getInstance().getTextureManager().register(loc, tex);
            TEXTURES.put(id, new Tex(loc, tex, img.getWidth(), img.getHeight()));
            trim();
            return loc;
        } catch (Exception e) {
            TFClient.LOGGER.warn("Comunidad: foto ilegible {}", id);
            return null;
        }
    }

    /** Como mucho 24 fotos en memoria de vídeo. */
    private static void trim() {
        while (TEXTURES.size() > 24) {
            String oldest = TEXTURES.keySet().iterator().next();
            Tex t = TEXTURES.remove(oldest);
            Minecraft.getInstance().getTextureManager().release(t.loc);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Publicar
    // ---------------------------------------------------------------------------------------------------------------

    /** Prepara la foto (16:9, 480x270, PNG) en segundo plano y la manda a trozos. */
    static void publish(Path file, String caption) {
        uploadStatus = "Preparando la foto...";
        uploadProgress = 0;
        Util.backgroundExecutor().execute(() -> {
            byte[] png;
            try (InputStream in = Files.newInputStream(file); NativeImage src = NativeImage.read(in)) {
                png = encode(src, PHOTO_W, PHOTO_H);
                if (png.length > PadCommunityNet.MAX_PHOTO) png = encode(src, 384, 216);
                if (png.length > PadCommunityNet.MAX_PHOTO) png = encode(src, 320, 180);
            } catch (Exception e) {
                TFClient.LOGGER.warn("Comunidad: no se pudo preparar la foto", e);
                Minecraft.getInstance().execute(() -> result(new PadCommunityNet.Result(false, "No se pudo leer la foto.")));
                return;
            }
            byte[] data = png;
            Minecraft.getInstance().execute(() -> send(data, caption));
        });
    }

    private static byte[] encode(NativeImage src, int w, int h) throws java.io.IOException {
        // recorte centrado a 16:9
        int sw = src.getWidth(), sh = src.getHeight();
        int cw = sw, ch = sw * 9 / 16;
        if (ch > sh) {
            ch = sh;
            cw = sh * 16 / 9;
        }
        try (NativeImage out = new NativeImage(w, h, false)) {
            src.resizeSubRectTo((sw - cw) / 2, (sh - ch) / 2, cw, ch, out);
            return out.asByteArray();
        }
    }

    private static void send(byte[] png, String caption) {
        int total = (png.length + PadCommunityNet.UP_CHUNK - 1) / PadCommunityNet.UP_CHUNK;
        int upload = (int) (System.currentTimeMillis() & 0x7FFFFFFF);
        uploadStatus = "Subiendo...";
        for (int i = 0; i < total; i++) {
            byte[] part = java.util.Arrays.copyOfRange(png, i * PadCommunityNet.UP_CHUNK, Math.min(png.length, (i + 1) * PadCommunityNet.UP_CHUNK));
            PadCommunityNet.toServer(new PadCommunityNet.Upload(upload, i, total, i == 0 ? caption : "", part));
            uploadProgress = (i + 1) / (float) total;
        }
    }

    static void result(PadCommunityNet.Result r) {
        uploadStatus = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TFPadScreen pad) {
            pad.showNotice(r.message());
            if (r.ok()) {
                pad.sound("like", 0.9F);
                tab = "mias";
                loadedPage = -1;
                pad.openApp("comunidad");
            }
        } else if (mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(r.message()), true);
        }
    }

    /** Al salir del servidor: fuera la lista y las texturas. */
    static void clear() {
        POSTS.clear();
        loadedPage = -1;
        ASKED.clear();
        DOWNLOADS.clear();
        for (Tex t : TEXTURES.values()) Minecraft.getInstance().getTextureManager().release(t.loc);
        TEXTURES.clear();
    }
}
