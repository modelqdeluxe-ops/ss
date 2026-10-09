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
    static final int PHOTO_W = 1280, PHOTO_H = 720;

    /** Lo que se ve: publicaciones cargadas de la pestaña actual (varias páginas seguidas). */
    static final List<PadCommunityNet.Post> POSTS = new ArrayList<>();
    static String tab = "recientes";
    static int loadedPage = -1, pages = 1, total;
    static boolean loading;

    private record Tex(ResourceLocation loc, DynamicTexture tex, int w, int h) {}

    private static final Map<String, Tex> TEXTURES = new LinkedHashMap<>(16, 0.75F, true);
    private static final Map<String, byte[][]> DOWNLOADS = new HashMap<>();
    private static final Set<String> ASKED = new HashSet<>();
    private static final Set<String> BROKEN = new HashSet<>();
    /** Fotos que se están leyendo en segundo plano (para no leer dos veces la misma). */
    private static final Set<String> DECODING = new HashSet<>();
    /** Los trozos de la foto que se está subiendo: uno por tick, para no atascar la conexión. */
    private static final java.util.ArrayDeque<PadCommunityNet.Upload> OUTBOX = new java.util.ArrayDeque<>();
    private static int outboxTotal;

    /** Subida en curso (para la barra de la Cámara). */
    static volatile String uploadStatus;
    static volatile float uploadProgress;
    /** Cuándo se mandó el último trozo: si en 30 s el servidor no contesta, se avisa (nunca se queda «Subiendo…»). */
    private static long uploadSentAt;

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

    /**
     * like / borrar / denunciar. Se cambia aquí mismo lo que se ve (sin volver a pedir la lista, para no saltar a otra
     * foto ni reordenar POPULARES); página -1 = el servidor no manda la lista.
     */
    static void act(String action, String id) {
        PadCommunityNet.toServer(new PadCommunityNet.Act(action, id, tab, -1));
        for (int i = 0; i < POSTS.size(); i++) {
            PadCommunityNet.Post p = POSTS.get(i);
            if (!p.id().equals(id)) continue;
            if (action.equals("like")) {
                POSTS.set(i, new PadCommunityNet.Post(p.id(), p.author(), p.name(), p.caption(), p.time(),
                        Math.max(0, p.likes() + (p.liked() ? -1 : 1)), !p.liked(), p.mine(), p.canDelete(), p.reactions(), p.myReaction()));
            } else if (action.startsWith("react:")) {
                // al momento, como en el servidor: la misma otra vez la quita; otra, la cambia
                int k = PadCommunityNet.REACTIONS.indexOf(action.substring(6));
                if (k < 0) break;
                int[] r = p.reactions().clone();
                if (p.myReaction() >= 0 && p.myReaction() < r.length) r[p.myReaction()] = Math.max(0, r[p.myReaction()] - 1);
                int mineNow = p.myReaction() == k ? -1 : k;
                if (mineNow >= 0) r[mineNow]++;
                POSTS.set(i, new PadCommunityNet.Post(p.id(), p.author(), p.name(), p.caption(), p.time(), p.likes(), p.liked(),
                        p.mine(), p.canDelete(), r, mineNow));
            } else if (action.equals("borrar")) {
                POSTS.remove(i);
                total = Math.max(0, total - 1);
            }
            break;
        }
        if (Minecraft.getInstance().screen instanceof TFPadScreen pad && pad.page() instanceof PadCommunityPage p) p.updated();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Fotos
    // ---------------------------------------------------------------------------------------------------------------

    /** La textura de una foto (null mientras se baja o si no se pudo leer). */
    static ResourceLocation texture(String id) {
        Tex t = TEXTURES.get(id);
        if (t != null) return t.loc;
        if (!id.matches("[a-z0-9]{1,20}") || BROKEN.contains(id)) return null;
        if (DECODING.contains(id)) return null;
        Path cached = cacheDir().resolve(id + ".png");
        if (Files.exists(cached)) {
            // se lee en segundo plano (una foto de 1280x720 tarda): mientras, la rueda de carga
            DECODING.add(id);
            Util.backgroundExecutor().execute(() -> {
                NativeImage img = null;
                try {
                    img = decode(Files.readAllBytes(cached));
                } catch (Exception e) {
                    // se trata abajo
                }
                NativeImage done = img;
                Minecraft.getInstance().execute(() -> {
                    DECODING.remove(id);
                    if (done != null) {
                        register(id, done);
                        return;
                    }
                    // la copia de la caché está rota: se borra y se pide otra vez (una sola)
                    TFClient.LOGGER.warn("Comunidad: foto en caché rota {}", id);
                    try {
                        Files.deleteIfExists(cached);
                    } catch (Exception ignored) {
                        // nada
                    }
                    if (ASKED.add(id)) PadCommunityNet.toServer(new PadCommunityNet.ImgReq(id));
                });
            });
            return null;
        }
        if (ASKED.add(id)) PadCommunityNet.toServer(new PadCommunityNet.ImgReq(id));
        return null;
    }

    static int[] textureSize(String id) {
        Tex t = TEXTURES.get(id);
        return t == null ? new int[] {PHOTO_W, PHOTO_H} : new int[] {t.w, t.h};
    }

    static void image(PadCommunityNet.Img m) {
        if (!m.id().matches("[a-z0-9]{1,20}")) return;
        if (m.total() <= 0 || m.total() > PadCommunityServer.MAX_PHOTO_PARTS * 2 || m.index() < 0 || m.index() >= m.total()) return;
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
        String id = m.id();
        DECODING.add(id);
        Util.backgroundExecutor().execute(() -> {
            NativeImage img = decode(png);
            Minecraft.getInstance().execute(() -> {
                DECODING.remove(id);
                if (img != null) {
                    register(id, img);
                    return;
                }
                // ilegible: no se vuelve a intentar en esta sesión
                BROKEN.add(id);
                try {
                    Files.deleteIfExists(cacheDir().resolve(id + ".png"));
                } catch (Exception ignored) {
                    // nada
                }
            });
        });
    }

    /** Lee el PNG (en segundo plano); null si no es una imagen. */
    private static NativeImage decode(byte[] png) {
        try (InputStream in = new ByteArrayInputStream(png)) {
            return NativeImage.read(in);
        } catch (Exception e) {
            return null;
        }
    }

    /** Sube la foto ya leída a la tarjeta gráfica (en el hilo del juego). */
    private static void register(String id, NativeImage img) {
        ResourceLocation loc = new ResourceLocation(TFClient.MOD_ID, "comunidad/" + id);
        DynamicTexture tex = PadCameraPage.smooth(new DynamicTexture(img));
        Minecraft.getInstance().getTextureManager().register(loc, tex);
        TEXTURES.put(id, new Tex(loc, tex, img.getWidth(), img.getHeight()));
        trim();
    }

    /** Como mucho 12 fotos en memoria de vídeo (a 1280x720, unos 44 MB). */
    private static void trim() {
        while (TEXTURES.size() > 12) {
            String oldest = TEXTURES.keySet().iterator().next();
            Tex t = TEXTURES.remove(oldest);
            Minecraft.getInstance().getTextureManager().release(t.loc);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Publicar
    // ---------------------------------------------------------------------------------------------------------------

    /** Prepara la foto (16:9, 1280x720 o, si pesa demasiado, 960x540, PNG) en segundo plano y la manda a trozos. */
    static void publish(Path file, String caption) {
        uploadStatus = "Preparando la foto...";
        uploadProgress = 0;
        Util.backgroundExecutor().execute(() -> {
            byte[] png;
            try (InputStream in = Files.newInputStream(file); NativeImage src = NativeImage.read(in)) {
                png = encode(src, PHOTO_W, PHOTO_H);
                if (png.length > PadCommunityNet.MAX_PHOTO) png = encode(src, 960, 540);
                if (png.length > PadCommunityNet.MAX_PHOTO) png = encode(src, 640, 360);
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

    /** Si la subida lleva 30 s sin respuesta, se da por perdida. */
    static void checkUpload() {
        if (uploadStatus != null && uploadSentAt > 0 && System.currentTimeMillis() - uploadSentAt > 30_000) {
            uploadSentAt = 0;
            result(new PadCommunityNet.Result(false, "Sin respuesta del servidor."));
        }
    }

    private static void send(byte[] png, String caption) {
        if (png.length > PadCommunityNet.MAX_PHOTO) {
            result(new PadCommunityNet.Result(false, "Foto demasiado grande."));
            return;
        }
        if (Minecraft.getInstance().getConnection() == null) {
            result(new PadCommunityNet.Result(false, "No estás conectado al servidor."));
            return;
        }
        int total = (png.length + PadCommunityNet.UP_CHUNK - 1) / PadCommunityNet.UP_CHUNK;
        int upload = (int) (System.currentTimeMillis() & 0x7FFFFFFF);
        uploadStatus = "Subiendo...";
        uploadProgress = 0;
        OUTBOX.clear();
        for (int i = 0; i < total; i++) {
            byte[] part = java.util.Arrays.copyOfRange(png, i * PadCommunityNet.UP_CHUNK, Math.min(png.length, (i + 1) * PadCommunityNet.UP_CHUNK));
            OUTBOX.add(new PadCommunityNet.Upload(upload, i, total, i == 0 ? caption : "", part));
        }
        outboxTotal = total;
    }

    /**
     * Cada tick del juego: manda el siguiente trozo de la foto (28 KB; unos 560 KB por segundo, para no atascar la
     * conexión ni que el servidor te eche) y, al acabar, empieza a contar la espera de la respuesta.
     */
    static void tick() {
        if (OUTBOX.isEmpty()) {
            checkUpload();
            return;
        }
        if (Minecraft.getInstance().getConnection() == null) {
            OUTBOX.clear();
            result(new PadCommunityNet.Result(false, "No estás conectado al servidor."));
            return;
        }
        PadCommunityNet.toServer(OUTBOX.poll());
        uploadProgress = (outboxTotal - OUTBOX.size()) / (float) Math.max(1, outboxTotal);
        if (OUTBOX.isEmpty()) {
            uploadStatus = "Publicando...";
            uploadSentAt = System.currentTimeMillis();
        }
    }

    static void result(PadCommunityNet.Result r) {
        uploadStatus = null;
        uploadSentAt = 0;
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
        pages = 1;
        total = 0;
        loading = false;
        uploadStatus = null;
        uploadProgress = 0;
        uploadSentAt = 0;
        ASKED.clear();
        BROKEN.clear();
        DOWNLOADS.clear();
        DECODING.clear();
        OUTBOX.clear();
        for (Tex t : TEXTURES.values()) Minecraft.getInstance().getTextureManager().release(t.loc);
        TEXTURES.clear();
    }
}
