package net.tierrasfantasticas.tfclient.pad.music;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * La música del jugador, en su ordenador: .minecraft/tfclient/musica/ con cada canción (&lt;id&gt;.mp3/.m4a/.ogg/…), su
 * portada (&lt;id&gt;.png, 256x256) y biblioteca.json (la lista, en orden, las playlists y los ajustes del reproductor:
 * volumen, aleatorio, repetir, la lista que suena, el altavoz y si se oyen los altavoces de otros). Las canciones están
 * en el ordenador del jugador; con el altavoz encendido, los de cerca las bajan del mismo link (ver MusicSpeaker).
 */
public final class MusicLibrary {
    /** Una canción guardada. file: nombre del archivo dentro de la carpeta; durationMs 0 si no se sabe. */
    public record Track(String id, String title, String artist, String file, boolean hasCover, long durationMs, String url, long added) {
        public String shownTitle() {
            return title.isBlank() ? "Sin título" : title;
        }

        public String shownArtist() {
            return artist.isBlank() ? "Artista desconocido" : artist;
        }
    }

    public enum Repeat {
        OFF, ALL, ONE;

        Repeat next() {
            return values()[(ordinal() + 1) % 3];
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /** Una playlist: su nombre y las canciones (ids), en orden. */
    public record Playlist(String id, String name, List<String> tracks) {}

    private static final List<Track> TRACKS = new ArrayList<>();
    private static final List<Playlist> LISTS = new ArrayList<>();
    /** La lista que suena y se ve ("" = todas las canciones). */
    static String currentList = "";
    /** Altavoz: los jugadores de cerca oyen lo que suena (si la canción vino de un link). */
    static boolean speaker;
    /** Oír los altavoces de otros jugadores. */
    static boolean hearOthers = true;
    private static final Map<String, ResourceLocation> COVERS = new HashMap<>();
    private static boolean loaded;
    static float volume = 0.8F;
    static boolean shuffle;
    static Repeat repeat = Repeat.ALL;

    private MusicLibrary() {}

    public static Path dir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("tfclient").resolve("musica");
    }

    public static synchronized List<Track> tracks() {
        load();
        return List.copyOf(TRACKS);
    }

    public static synchronized Track get(String id) {
        load();
        for (Track t : TRACKS) if (t.id().equals(id)) return t;
        return null;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Playlists
    // ---------------------------------------------------------------------------------------------------------------

    public static synchronized List<Playlist> playlists() {
        load();
        return List.copyOf(LISTS);
    }

    public static synchronized String currentList() {
        load();
        if (!currentList.isEmpty() && playlist(currentList) == null) currentList = "";
        return currentList;
    }

    public static synchronized void selectList(String id) {
        load();
        currentList = id == null || playlist(id) == null ? "" : id;
        save();
    }

    public static synchronized Playlist playlist(String id) {
        for (Playlist p : LISTS) if (p.id().equals(id)) return p;
        return null;
    }

    /** Lo que suena y se ve: las canciones de la lista elegida, en su orden, o todas. */
    public static synchronized List<Track> queue() {
        load();
        Playlist p = currentList.isEmpty() ? null : playlist(currentList);
        if (p == null) return List.copyOf(TRACKS);
        List<Track> out = new ArrayList<>();
        for (String id : p.tracks()) {
            Track t = get(id);
            if (t != null) out.add(t);
        }
        return out;
    }

    public static synchronized Playlist createList(String name) {
        load();
        String clean = name.strip();
        if (clean.isEmpty()) return null;
        if (clean.length() > 24) clean = clean.substring(0, 24);
        Playlist p = new Playlist(Long.toString(System.currentTimeMillis(), 36), clean, new ArrayList<>());
        LISTS.add(p);
        currentList = p.id();
        save();
        return p;
    }

    public static synchronized void deleteList(String id) {
        load();
        LISTS.removeIf(p -> p.id().equals(id));
        if (currentList.equals(id)) currentList = "";
        save();
    }

    public static synchronized boolean inList(String listId, String trackId) {
        Playlist p = playlist(listId);
        return p != null && p.tracks().contains(trackId);
    }

    /** Mete la canción en la lista o la saca si ya estaba. */
    public static synchronized void toggleInList(String listId, String trackId) {
        load();
        Playlist p = playlist(listId);
        if (p == null) return;
        List<String> ids = new ArrayList<>(p.tracks());
        if (!ids.remove(trackId)) ids.add(trackId);
        LISTS.set(LISTS.indexOf(p), new Playlist(p.id(), p.name(), ids));
        save();
    }

    /** Mete la canción en la lista (si no estaba ya). */
    public static synchronized void addToList(String listId, String trackId) {
        load();
        Playlist p = playlist(listId);
        if (p == null || p.tracks().contains(trackId)) return;
        List<String> ids = new ArrayList<>(p.tracks());
        ids.add(trackId);
        LISTS.set(LISTS.indexOf(p), new Playlist(p.id(), p.name(), ids));
        save();
    }

    public static synchronized boolean speaker() {
        load();
        return speaker;
    }

    public static synchronized void setSpeaker(boolean on) {
        load();
        speaker = on;
        save();
    }

    public static synchronized boolean hearOthers() {
        load();
        return hearOthers;
    }

    public static synchronized void setHearOthers(boolean on) {
        load();
        hearOthers = on;
        save();
    }

    public static Path file(Track t) {
        return dir().resolve(t.file());
    }

    static synchronized void add(Track t) {
        load();
        TRACKS.removeIf(o -> o.id().equals(t.id()));
        TRACKS.add(0, t);
        save();
    }

    static synchronized void update(Track t) {
        load();
        for (int i = 0; i < TRACKS.size(); i++) if (TRACKS.get(i).id().equals(t.id())) TRACKS.set(i, t);
        save();
    }

    /** Borra la canción y su portada del ordenador. */
    public static synchronized void remove(String id) {
        load();
        Track t = get(id);
        if (t == null) return;
        TRACKS.remove(t);
        for (int i = 0; i < LISTS.size(); i++) {
            Playlist p = LISTS.get(i);
            if (p.tracks().contains(id)) {
                List<String> ids = new ArrayList<>(p.tracks());
                ids.remove(id);
                LISTS.set(i, new Playlist(p.id(), p.name(), ids));
            }
        }
        save();
        releaseCover(id);
        try {
            Files.deleteIfExists(file(t));
            Files.deleteIfExists(dir().resolve(id + ".png"));
        } catch (IOException e) {
            TFClient.LOGGER.warn("Música: no se pudo borrar {}", t.file());
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Canciones metidas a mano en la carpeta
    // ---------------------------------------------------------------------------------------------------------------

    private static final java.util.concurrent.atomic.AtomicBoolean SCANNING = new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * Busca en .minecraft/tfclient/musica (y sus subcarpetas) las canciones que el jugador metió a mano (cualquier MP3,
     * MP4/M4A, AAC, OGG o WAV, se llamen como se llamen) y las añade a la biblioteca con su título, artista, portada y duración (de
     * sus etiquetas; sin título, el nombre del archivo). En su propio hilo; se llama al abrir Música.
     */
    public static void scanFolder() {
        if (SCANNING.getAndSet(true)) return;
        Thread t = new Thread(() -> {
            try {
                scanNow();
            } catch (Exception e) {
                TFClient.LOGGER.warn("Música: no se pudo mirar la carpeta", e);
            } finally {
                SCANNING.set(false);
            }
        }, "TF Música: carpeta");
        t.setDaemon(true);
        t.start();
    }

    private static void scanNow() throws IOException {
        load();
        Path dir = dir();
        if (!Files.isDirectory(dir)) {
            Files.createDirectories(dir);
            return;
        }
        java.util.Set<String> known = new java.util.HashSet<>();
        synchronized (MusicLibrary.class) {
            for (Track t : TRACKS) known.add(t.file());
        }
        List<Path> found;
        try (java.util.stream.Stream<Path> s = Files.walk(dir, 4)) {
            found = s.filter(Files::isRegularFile).sorted().toList();
        }
        int added = 0;
        for (Path p : found) {
            String rel = dir.relativize(p).toString().replace('\\', '/');
            String low = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            if (known.contains(rel) || rel.startsWith("altavoz/") || low.startsWith(".") || low.endsWith(".part")
                    || low.endsWith(".png") || low.endsWith(".jpg") || low.endsWith(".json") || low.endsWith(".audio")) continue;
            try {
                AudioDecoder.Kind kind = AudioDecoder.AudioFormatSniffer.sniff(p);
                if (kind == AudioDecoder.Kind.UNKNOWN) continue;
                try (AudioDecoder d = AudioDecoder.open(p, 0)) {
                    if (d.next() == null) continue;
                }
                long duration = switch (kind) {
                    case MP3 -> JLayerDecoder.isMpeg1(p) ? Mp3Frames.scan(p, -1).durationMs() : JLayerDecoder.durationMs(p);
                    case OGG -> OggDecoder.durationMs(p);
                    case MP4 -> AacDecoder.durationMs(p);
                    case AAC -> AdtsDecoder.durationMs(p);
                    default -> MusicDownloader.wavDuration(p);
                };
                MusicTags.Tags tags = MusicTags.read(p);
                String id = Long.toString(System.currentTimeMillis(), 36)
                        + Integer.toString(java.util.concurrent.ThreadLocalRandom.current().nextInt(36 * 36 * 36), 36);
                boolean cover = tags.cover() != null && MusicDownloader.saveCover(tags.cover(), dir.resolve(id + ".png"));
                String name = p.getFileName().toString();
                int dot = name.lastIndexOf('.');
                if (dot > 0) name = name.substring(0, dot);
                name = name.replace('_', ' ').trim();
                String title = !tags.title().isBlank() ? tags.title() : name;
                add(new Track(id, title, tags.artist(), rel, cover, duration, "", System.currentTimeMillis()));
                known.add(rel);
                added++;
            } catch (Exception e) {
                TFClient.LOGGER.warn("Música: {} no se pudo leer: {}", rel, e.getMessage());
            }
        }
        if (added > 0) TFClient.LOGGER.info("Música: {} canción(es) nuevas de la carpeta", added);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Portadas
    // ---------------------------------------------------------------------------------------------------------------

    /** La portada como textura (se carga la primera vez) o null si la canción no tiene. Solo en el hilo de render. */
    public static ResourceLocation cover(Track t) {
        if (t == null || !t.hasCover()) return null;
        ResourceLocation loc = COVERS.get(t.id());
        if (loc != null) return loc;
        Path png = dir().resolve(t.id() + ".png");
        if (!Files.exists(png)) return null;
        try (InputStream in = Files.newInputStream(png)) {
            NativeImage img = NativeImage.read(in);
            loc = new ResourceLocation(TFClient.MOD_ID, "musica/" + t.id().toLowerCase(Locale.ROOT));
            DynamicTexture texture = new DynamicTexture(img);
            texture.setFilter(true, false); // es una foto: suavizada al escalar
            Minecraft.getInstance().getTextureManager().register(loc, texture);
            COVERS.put(t.id(), loc);
            return loc;
        } catch (Exception e) {
            TFClient.LOGGER.warn("Música: no se pudo leer la portada de {}", t.id());
            COVERS.put(t.id(), null);
            return null;
        }
    }

    /** Para cuando cambia la portada o se borra la canción. */
    public static void releaseCover(String id) {
        ResourceLocation loc = COVERS.remove(id);
        if (loc != null) Minecraft.getInstance().execute(() -> Minecraft.getInstance().getTextureManager().release(loc));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Archivo
    // ---------------------------------------------------------------------------------------------------------------

    static synchronized void load() {
        if (loaded) return;
        loaded = true;
        Path json = dir().resolve("biblioteca.json");
        if (!Files.exists(json)) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(json, StandardCharsets.UTF_8)).getAsJsonObject();
            if (o.has("volumen")) volume = Math.max(0, Math.min(1, o.get("volumen").getAsFloat()));
            if (o.has("aleatorio")) shuffle = o.get("aleatorio").getAsBoolean();
            if (o.has("repetir")) {
                String r = o.get("repetir").getAsString();
                repeat = r.equals("no") ? Repeat.OFF : r.equals("una") ? Repeat.ONE : Repeat.ALL;
            }
            if (o.has("listaActual")) currentList = o.get("listaActual").getAsString();
            if (o.has("altavoz")) speaker = o.get("altavoz").getAsBoolean();
            if (o.has("oirAltavoces")) hearOthers = o.get("oirAltavoces").getAsBoolean();
            if (o.has("listas") && o.get("listas").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("listas")) {
                    JsonObject l = e.getAsJsonObject();
                    List<String> ids = new ArrayList<>();
                    if (l.has("canciones")) for (JsonElement c : l.getAsJsonArray("canciones")) ids.add(c.getAsString());
                    LISTS.add(new Playlist(str(l, "id"), str(l, "nombre"), ids));
                }
            }
            JsonArray list = o.has("canciones") ? o.getAsJsonArray("canciones") : new JsonArray();
            for (JsonElement e : list) {
                JsonObject c = e.getAsJsonObject();
                String file = str(c, "archivo");
                if (file.isEmpty() || !Files.exists(dir().resolve(file))) continue;
                TRACKS.add(new Track(str(c, "id"), str(c, "titulo"), str(c, "artista"), file,
                        c.has("portada") && c.get("portada").getAsBoolean(), c.has("duracionMs") ? c.get("duracionMs").getAsLong() : 0,
                        str(c, "link"), c.has("anadida") ? c.get("anadida").getAsLong() : 0));
            }
        } catch (Exception e) {
            TFClient.LOGGER.warn("Música: biblioteca.json no se pudo leer", e);
        }
    }

    static synchronized void save() {
        JsonObject o = new JsonObject();
        o.addProperty("volumen", Math.round(volume * 100) / 100F);
        o.addProperty("aleatorio", shuffle);
        o.addProperty("repetir", repeat == Repeat.OFF ? "no" : repeat == Repeat.ONE ? "una" : "todas");
        JsonArray list = new JsonArray();
        for (Track t : TRACKS) {
            JsonObject c = new JsonObject();
            c.addProperty("id", t.id());
            c.addProperty("titulo", t.title());
            c.addProperty("artista", t.artist());
            c.addProperty("archivo", t.file());
            c.addProperty("portada", t.hasCover());
            c.addProperty("duracionMs", t.durationMs());
            c.addProperty("link", t.url());
            c.addProperty("anadida", t.added());
            list.add(c);
        }
        o.add("canciones", list);
        JsonArray lists = new JsonArray();
        for (Playlist p : LISTS) {
            JsonObject l = new JsonObject();
            l.addProperty("id", p.id());
            l.addProperty("nombre", p.name());
            JsonArray ids = new JsonArray();
            p.tracks().forEach(ids::add);
            l.add("canciones", ids);
            lists.add(l);
        }
        o.add("listas", lists);
        o.addProperty("listaActual", currentList);
        o.addProperty("altavoz", speaker);
        o.addProperty("oirAltavoces", hearOthers);
        try {
            Files.createDirectories(dir());
            Path tmp = dir().resolve("biblioteca.json.tmp");
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            Files.move(tmp, dir().resolve("biblioteca.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            TFClient.LOGGER.warn("Música: no se pudo guardar biblioteca.json", e);
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }
}
