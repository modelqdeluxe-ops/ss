/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.pad.PadSpeakerNet;

/**
 * (1.3.134) Compartir canciones con un disco, en el cliente.
 * <ul>
 *   <li>Compartir (el botón de cada canción en la app Música): se manda al servidor el link de la canción (o
 *       tfaudio:&lt;sha1&gt; si es de tu carpeta: el servidor la pide y se sube como la del altavoz) y te da el disco
 *       en la mano (PadMusicShare).</li>
 *   <li>Al gastar un disco (clic derecho): la canción se baja (de su link o del servidor) y se guarda en tu música, en
 *       la pestaña COMPARTIDOS ({@link MusicLibrary#SHARED}). Si ya la tenías, solo entra en COMPARTIDOS. Lo que queda
 *       por bajar se apunta en musica/compartidas-pendientes.json: si te vas antes, se baja al volver.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class MusicShare {
    /** Una canción de un disco que falta por bajar. */
    private record Pending(String url, String title, String artist, long durationMs, String from) {}

    private static final List<Pending> PENDING = new ArrayList<>();
    private static boolean loaded;
    /** La que se baja ahora o null; si es de internet, su descarga (MusicDownloader). */
    private static Pending downloading;
    private static MusicDownloader.Job job;
    /** Tras un fallo, cuándo se vuelve a intentar (y si ya se avisó). */
    private static long retryAt;
    private static boolean warned;
    private static int ticks;

    private MusicShare() {}

    /** El botón de compartir: pide el disco. Devuelve el aviso para enseñar. */
    public static String share(MusicLibrary.Track t) {
        if (Minecraft.getInstance().getConnection() == null) return "Entra en el servidor para compartirla.";
        String url = MusicSpeaker.shareUrl(t);
        if (url == null) return "Esta canción pesa demasiado para compartirla (" + (PadSpeakerNet.MAX_BYTES >> 20) + " MB como mucho).";
        PadSpeakerNet.toServerAny(new PadSpeakerNet.Share(url, cut(t.shownTitle(), 60), cut(t.artist(), 60), t.durationMs()));
        return url.startsWith(PadSpeakerNet.LOCAL) ? "Preparando el disco…" : "Disco en camino…";
    }

    private static String cut(String s, int n) {
        return s.length() > n ? s.substring(0, n) : s;
    }

    /** Gastaste un disco: la canción a COMPARTIDOS (se baja si no la tienes). */
    public static void received(PadSpeakerNet.Shared m) {
        String url = m.url().trim();
        if (!(url.startsWith("https://") || url.startsWith("http://") || url.startsWith(PadSpeakerNet.LOCAL)) || url.length() > 500) return;
        MusicLibrary.Track have = find(url);
        if (have != null) {
            MusicLibrary.addToList(MusicLibrary.SHARED, have.id());
            tell("«" + have.shownTitle() + "» ya la tenías: está en Música › COMPARTIDOS.");
            return;
        }
        load();
        Pending p = new Pending(url, cut(m.title(), 60), cut(m.artist(), 60), m.durationMs(), cut(m.from(), 40));
        PENDING.removeIf(o -> o.url.equals(url));
        PENDING.add(p);
        save();
        tell("Bajando «" + (p.title.isBlank() ? "la canción" : p.title) + "»" + (p.from.isBlank() ? "" : " de " + p.from) + "…");
        next();
    }

    /** La canción de la biblioteca que vino de ese link (o null). */
    private static MusicLibrary.Track find(String url) {
        for (MusicLibrary.Track t : MusicLibrary.tracks()) if (t.url().equals(url)) return t;
        return null;
    }

    /** Empieza a bajar la siguiente que falte (una a la vez). */
    private static void next() {
        if (downloading != null || PENDING.isEmpty() || Minecraft.getInstance().getConnection() == null) return;
        Pending p = PENDING.get(0);
        MusicLibrary.Track have = find(p.url);
        if (have != null) {
            done(p, have);
            return;
        }
        if (p.url.startsWith(PadSpeakerNet.LOCAL)) {
            if (MusicSpeaker.fetchShared(p.url)) downloading = p;
        } else if (MusicDownloader.download(p.url, MusicLibrary.SHARED, p.title, p.artist)) {
            downloading = p;
            job = MusicDownloader.job();
        }
    }

    /** No se pudo: se avisa una vez y se vuelve a intentar en un minuto (la que falló pasa al final). */
    private static void failed(Pending p, String why) {
        downloading = null;
        job = null;
        retryAt = System.currentTimeMillis() + 60_000;
        if (PENDING.size() > 1 && PENDING.remove(p)) PENDING.add(p);
        if (!warned) tell("No se pudo bajar «" + p.title + "»" + (why == null ? "" : ": " + why) + ". Se vuelve a intentar en un rato.");
        warned = true;
    }

    /** Llegó (o falló) una canción del servidor (MusicSpeaker.finish). */
    static void arrived(String url, Path file, boolean ok) {
        Pending p = downloading;
        if (p == null || !p.url.equals(url)) return;
        if (!ok || file == null) {
            failed(p, null);
            return;
        }
        downloading = null;
        try {
            MusicLibrary.Track t = store(p, file);
            done(p, t);
        } catch (Exception e) {
            TFClient.LOGGER.warn("Música: no se pudo guardar la canción compartida", e);
            tell("No se pudo guardar «" + p.title + "».");
        }
    }

    /** La canción de un disco, del servidor a tu música (con su portada si la trae dentro). */
    private static MusicLibrary.Track store(Pending p, Path from) throws Exception {
        AudioDecoder.Kind kind = AudioDecoder.AudioFormatSniffer.sniff(from);
        if (kind == AudioDecoder.Kind.UNKNOWN) throw new java.io.IOException("no es una canción");
        Path dir = MusicLibrary.dir();
        Files.createDirectories(dir);
        String id = Long.toString(System.currentTimeMillis(), 36)
                + Integer.toString(java.util.concurrent.ThreadLocalRandom.current().nextInt(36 * 36 * 36), 36);
        Path to = dir.resolve(id + kind.ext());
        Files.copy(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        MusicTags.Tags tags = MusicTags.read(to);
        boolean cover = tags.cover() != null && MusicDownloader.saveCover(tags.cover(), dir.resolve(id + ".png"));
        String title = !p.title.isBlank() ? p.title : !tags.title().isBlank() ? tags.title() : "Canción compartida";
        String artist = !p.artist.isBlank() ? p.artist : tags.artist();
        MusicLibrary.Track t = new MusicLibrary.Track(id, title, artist, to.getFileName().toString(), cover, Math.max(0, p.durationMs),
                p.url, System.currentTimeMillis());
        MusicLibrary.add(t);
        return t;
    }

    private static void done(Pending p, MusicLibrary.Track t) {
        warned = false;
        MusicLibrary.addToList(MusicLibrary.SHARED, t.id());
        PENDING.remove(p);
        save();
        tell("«" + t.shownTitle() + "» guardada en Música › COMPARTIDOS" + (p.from.isBlank() ? "." : " (te la pasó " + p.from + ")."));
    }

    private static void tell(String text) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        TFClient.LOGGER.info("Música: {}", text);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks % 20 != 0) return;
        // la de internet: acabó su descarga (MusicDownloader ya la metió en COMPARTIDOS)
        Pending p = downloading;
        MusicDownloader.Job j = job;
        if (p != null && j != null && j.finished) {
            downloading = null;
            job = null;
            MusicLibrary.Track t = j.result != null ? j.result : find(p.url);
            if (t != null) done(p, t);
            else failed(p, j.error);
        }
        // lo que quedó por bajar (también lo de la vez anterior), cada 5 s; tras un fallo, al minuto
        if (downloading == null && ticks % 100 == 0 && System.currentTimeMillis() >= retryAt) {
            load();
            next();
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static Path file() {
        return MusicLibrary.dir().resolve("compartidas-pendientes.json");
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            for (JsonElement e : JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                PENDING.add(new Pending(str(o, "link"), str(o, "titulo"), str(o, "artista"),
                        o.has("duracionMs") ? o.get("duracionMs").getAsLong() : 0, str(o, "de")));
            }
        } catch (Exception e) {
            TFClient.LOGGER.warn("Música: compartidas-pendientes.json no se pudo leer", e);
        }
    }

    private static void save() {
        JsonArray a = new JsonArray();
        for (Pending p : PENDING) {
            JsonObject o = new JsonObject();
            o.addProperty("link", p.url);
            o.addProperty("titulo", p.title);
            o.addProperty("artista", p.artist);
            o.addProperty("duracionMs", p.durationMs);
            o.addProperty("de", p.from);
            a.add(o);
        }
        try {
            Files.createDirectories(MusicLibrary.dir());
            if (a.isEmpty()) Files.deleteIfExists(file());
            else Files.writeString(file(), a.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            TFClient.LOGGER.warn("Música: no se pudo guardar compartidas-pendientes.json", e);
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }
}
