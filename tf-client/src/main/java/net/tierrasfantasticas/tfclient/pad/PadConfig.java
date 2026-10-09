package net.tierrasfantasticas.tfclient.pad;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.util.TFConfigDir;

/**
 * config/tfclient/pad.json: los ajustes del pad de todos (qué apps salen, esperas de Viajes y Explorar, hogares sin
 * rango, envíos del Monedero, regalos y GTS). Se crea con los valores por defecto y lo cambia el pad de administrador
 * (o a mano, y se recarga desde el pad de administrador). Cada ajuste tiene su clave, su tipo y sus límites aquí.
 */
public final class PadConfig {
    /** Un ajuste numérico o de sí/no, con su texto para el pad de administrador. */
    public record Setting(String key, String label, String help, long min, long max, long def, boolean bool) {}

    public static final List<Setting> SETTINGS = List.of(
            new Setting("viajes.espera", "Espera entre viajes (s)", "Tras viajar al spawn, a un warp o a un punto.", 0, 3600, 30, false),
            new Setting("viajes.cuentaAtras", "Cuenta atrás al viajar (s)", "Sin moverse ni recibir daño. 0 = al momento.", 0, 10, 3, false),
            new Setting("viajes.warps", "Warps de EssentialsX en Viajes", "Salen todos los /warp del servidor.", 0, 1, 1, true),
            new Setting("explorar.activado", "Explorar", "Viaje a un sitio seguro al azar.", 0, 1, 1, true),
            new Setting("explorar.min", "Explorar: distancia mínima", "Bloques desde el spawn.", 100, 100000, 800, false),
            new Setting("explorar.max", "Explorar: distancia máxima", "Bloques desde el spawn.", 200, 200000, 6000, false),
            new Setting("explorar.espera", "Explorar: espera (s)", "Entre una exploración y otra.", 0, 86400, 300, false),
            new Setting("hogares.sinRango", "Hogares sin rango", "Los que tiene quien no tiene rango.", 0, 100, 3, false),
            new Setting("monedero.enviar", "Mandar monedas", "Desde el Monedero a otro jugador.", 0, 1, 1, true),
            new Setting("monedero.maximo", "Máximo por envío", "Monedas.", 1, 1_000_000_000L, 1_000_000, false),
            new Setting("regalos.activado", "Regalos", "Desde Jugadores: objetos y monedas.", 0, 1, 1, true),
            new Setting("regalos.porDia", "Regalos por día", "Por jugador.", 1, 1000, 10, false),
            new Setting("gts.maxPublicaciones", "GTS: cosas a la venta", "Por jugador a la vez.", 1, 100, 10, false),
            new Setting("gts.dias", "GTS: días a la venta", "Luego vuelve a Recoger.", 1, 60, 7, false));

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<String, Long> VALUES = new LinkedHashMap<>();
    private static final List<String> DISABLED = new ArrayList<>();
    private static final List<String> HIDDEN_WARPS = new ArrayList<>();

    private PadConfig() {}

    public static Path file() {
        return TFConfigDir.file("pad.json", null);
    }

    public static long get(String key) {
        Long v = VALUES.get(key);
        if (v != null) return v;
        for (Setting s : SETTINGS) if (s.key.equals(key)) return s.def;
        return 0;
    }

    public static boolean on(String key) {
        return get(key) != 0;
    }

    public static Setting setting(String key) {
        for (Setting s : SETTINGS) if (s.key.equals(key)) return s;
        return null;
    }

    /** Cambia un ajuste (dentro de sus límites) y guarda. */
    public static void set(String key, long value) {
        Setting s = setting(key);
        if (s == null) return;
        VALUES.put(key, Math.max(s.min, Math.min(s.max, value)));
        save();
    }

    /** Apps que el staff apagó: no salen en la portada y el servidor no las abre. */
    public static List<String> disabledApps() {
        return List.copyOf(DISABLED);
    }

    public static boolean appOn(String app) {
        return !DISABLED.contains(app);
    }

    public static void toggleApp(String app) {
        if (!DISABLED.remove(app)) DISABLED.add(app);
        save();
    }

    public static List<String> hiddenWarps() {
        return List.copyOf(HIDDEN_WARPS);
    }

    public static void toggleWarp(String warp) {
        if (!HIDDEN_WARPS.remove(warp)) HIDDEN_WARPS.add(warp);
        save();
    }

    public static void load() {
        VALUES.clear();
        DISABLED.clear();
        HIDDEN_WARPS.clear();
        Path f = file();
        if (Files.isRegularFile(f)) {
            try {
                JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject values = root.has("ajustes") ? root.getAsJsonObject("ajustes") : new JsonObject();
                for (Setting s : SETTINGS) {
                    JsonElement e = values.get(s.key);
                    if (e == null || !e.isJsonPrimitive()) continue;
                    long v = e.getAsJsonPrimitive().isBoolean() ? (e.getAsBoolean() ? 1 : 0) : e.getAsLong();
                    VALUES.put(s.key, Math.max(s.min, Math.min(s.max, v)));
                }
                if (root.has("appsApagadas")) for (JsonElement e : root.getAsJsonArray("appsApagadas")) DISABLED.add(e.getAsString());
                if (root.has("warpsOcultos")) for (JsonElement e : root.getAsJsonArray("warpsOcultos")) HIDDEN_WARPS.add(e.getAsString());
            } catch (Exception e) {
                TFClient.LOGGER.error("TF Pad: config/tfclient/pad.json no se pudo leer; se usan los valores por defecto", e);
                return;
            }
        }
        save();
    }

    public static void save() {
        JsonObject root = new JsonObject();
        JsonObject values = new JsonObject();
        for (Setting s : SETTINGS) {
            long v = get(s.key);
            if (s.bool) values.addProperty(s.key, v != 0);
            else values.addProperty(s.key, v);
        }
        root.add("ajustes", values);
        JsonArray off = new JsonArray();
        DISABLED.forEach(off::add);
        root.add("appsApagadas", off);
        JsonArray hidden = new JsonArray();
        HIDDEN_WARPS.forEach(hidden::add);
        root.add("warpsOcultos", hidden);
        try {
            Path f = file();
            Path tmp = f.resolveSibling("pad.json.tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            TFClient.LOGGER.error("TF Pad: no se pudo guardar config/tfclient/pad.json", e);
        }
    }
}
