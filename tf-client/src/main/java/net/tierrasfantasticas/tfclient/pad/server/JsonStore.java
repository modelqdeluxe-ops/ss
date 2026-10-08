package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Datos de una app del pad en &lt;mundo&gt;/tfclient/&lt;archivo&gt;: un JSON con un objeto por jugador («jugadores») y lo
 * que haga falta más. Si el archivo está roto, se copia aparte antes de empezar de cero (nunca se pisa sin copia).
 */
class JsonStore implements PadServer.Store {
    private final String file;
    JsonObject root = new JsonObject();
    private boolean dirty;

    JsonStore(String file) {
        this.file = file;
    }

    @Override
    public void load(MinecraftServer server) {
        Path path = TFJson.worldFile(server, file);
        JsonObject read = TFJson.read(path);
        if (read == null && Files.exists(path)) {
            try {
                Files.copy(path, path.resolveSibling(file.replace(".json", "") + "-roto-" + System.currentTimeMillis() + ".json"));
            } catch (Exception e) {
                TFClient.LOGGER.error("TF Pad: {} no se pudo leer ni copiar", file, e);
            }
        }
        root = read == null ? new JsonObject() : read;
        dirty = false;
        loaded(server);
    }

    /** Para leer lo que haga falta después de cargar. */
    void loaded(MinecraftServer server) {}

    @Override
    public void save(MinecraftServer server) {
        dirty = false;
        TFJson.write(TFJson.worldFile(server, file), root);
    }

    @Override
    public boolean dirty() {
        return dirty;
    }

    void changed() {
        dirty = true;
    }

    JsonObject obj(JsonObject parent, String key) {
        JsonElement e = parent.get(key);
        if (e != null && e.isJsonObject()) return e.getAsJsonObject();
        JsonObject o = new JsonObject();
        parent.add(key, o);
        return o;
    }

    /** El objeto de un jugador (se crea si no estaba). */
    JsonObject player(UUID uuid) {
        return obj(obj(root, "jugadores"), uuid.toString());
    }

    JsonObject playerIfAny(UUID uuid) {
        JsonElement all = root.get("jugadores");
        if (all == null || !all.isJsonObject()) return null;
        JsonElement e = all.getAsJsonObject().get(uuid.toString());
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    static long num(JsonObject o, String key) {
        return TFJson.num(o, key, 0);
    }
}
