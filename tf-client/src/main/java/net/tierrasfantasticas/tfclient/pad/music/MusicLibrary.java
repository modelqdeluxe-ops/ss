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
 * La música del jugador, en su ordenador: .minecraft/tfclient/musica/ con cada canción (&lt;id&gt;.mp3/.ogg/.wav), su
 * portada (&lt;id&gt;.png, 256x256) y biblioteca.json (la lista, en orden, y los ajustes del reproductor: volumen,
 * aleatorio, repetir). Solo la usa este cliente: nadie más la oye.
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
    private static final List<Track> TRACKS = new ArrayList<>();
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
