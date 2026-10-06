package net.tierrasfantasticas.tfclient.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.tierrasfantasticas.tfclient.TFClient;

/** Leer y guardar JSON del servidor (los datos del mundo van en &lt;mundo&gt;/tfclient/). */
public final class TFJson {
    public static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private TFJson() {}

    public static Path worldFile(MinecraftServer server, String name) {
        return server.getWorldPath(LevelResource.ROOT).resolve("tfclient").resolve(name);
    }

    public static JsonObject read(Path file) {
        if (!Files.exists(file)) return null;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(reader);
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            TFClient.LOGGER.error("TF Client: {} no es un JSON válido: {}", file, e.getMessage());
            return null;
        }
    }

    /** Guarda sin dejar el archivo a medias si el servidor se cae mientras escribe. */
    public static void write(Path file, JsonElement json) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                PRETTY.toJson(json, writer);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            TFClient.LOGGER.error("TF Client: no se pudo guardar {}: {}", file, e.getMessage());
        }
    }

    public static String str(JsonObject o, String key, String def) {
        return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : def;
    }

    public static long num(JsonObject o, String key, long def) {
        try {
            return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsLong() : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static double dec(JsonObject o, String key, double def) {
        try {
            return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsDouble() : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static boolean bool(JsonObject o, String key, boolean def) {
        return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsBoolean() : def;
    }

    public static JsonObject obj(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : new JsonObject();
    }
}
