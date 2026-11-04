/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.stream.Stream;
import net.minecraftforge.fml.loading.FMLPaths;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * (1.3.174, el dueño: «que la web con el puente se conecte a la data de nLogin y saque esas gráficas sola, desde el 26 de
 * octubre») Manda a la web, con cada consulta del puente, lo nuevo de los registros de nLogin ({@link NLoginLog}) para
 * la página /metricas. El cursor (por dónde va) lo guarda la web: al arrancar, el puente le pregunta ({@code want}) y
 * luego manda lo que va después (de 400 en 400 eventos: lo de días atrás llega en unos minutos). Si una consulta falla,
 * la siguiente vuelve a mandar lo mismo: la web solo lo guarda si empieza justo donde se quedó.
 *
 * <p>Sin el plugin nLogin (o sin su carpeta de registros) no manda nada. Nunca manda IP ni contraseñas.
 */
final class TFNLogin {
    private TFNLogin() {}

    private static final int MAX_EVENTS = 400;
    private static final long MAX_BYTES = 2L << 20;
    private static final long ERROR_LOG_EVERY_MS = 10 * 60 * 1000L;

    /** Lo que dijo la web: por dónde va y desde qué día; null hasta que conteste. */
    private static boolean known;
    private static NLoginLog.Cursor cursor;
    private static String desde = "2026-10-26";
    private static boolean announced;
    private static long lastErrorLog;

    /** La carpeta de registros de nLogin: plugins/nLogin/logs (el nombre de la carpeta, sin mirar mayúsculas). */
    static Path logsDir() {
        Path plugins = FMLPaths.GAMEDIR.get().resolve("plugins");
        if (!Files.isDirectory(plugins)) return null;
        try (Stream<Path> list = Files.list(plugins)) {
            return list.filter(p -> Files.isDirectory(p) && p.getFileName().toString().equalsIgnoreCase("nLogin"))
                    .map(p -> p.resolve("logs"))
                    .filter(Files::isDirectory)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** Lo que va en esta consulta (se llama en el hilo del puente), o null si no hay nada que mandar. */
    static synchronized JsonObject collect() {
        Path dir = logsDir();
        if (dir == null) return null;
        JsonObject out = new JsonObject();
        if (!known) {
            out.addProperty("want", true);
            return out;
        }
        try {
            NLoginLog.Batch batch = NLoginLog.read(dir, desde, cursor, MAX_EVENTS, MAX_BYTES, ZoneId.systemDefault());
            if (batch.to() == null || batch.to().equals(batch.from())) return null;
            out.add("from", cursorJson(batch.from()));
            out.add("to", cursorJson(batch.to()));
            JsonArray events = new JsonArray();
            for (NLoginLog.Event e : batch.events()) {
                JsonArray a = new JsonArray();
                a.add(e.id());
                a.add(e.t());
                a.add(e.kind());
                a.add(e.name());
                a.add(e.created());
                events.add(a);
            }
            out.add("events", events);
            return out;
        } catch (Exception e) {
            long now = System.currentTimeMillis();
            if (now - lastErrorLog > ERROR_LOG_EVERY_MS) {
                lastErrorLog = now;
                TFClient.LOGGER.warn("TF Bridge: no se pudieron leer los registros de nLogin de {}: {}", dir, e.toString());
            }
            return null;
        }
    }

    /** La respuesta de la web: { cursor, desde, ok } (se llama en el hilo del servidor). */
    static synchronized void answer(JsonElement element) {
        if (element == null || !element.isJsonObject()) return;
        JsonObject o = element.getAsJsonObject();
        if (o.has("desde") && o.get("desde").isJsonPrimitive()) {
            String d = o.get("desde").getAsString();
            if (d.matches("\\d{4}-\\d{2}-\\d{2}")) desde = d;
        }
        NLoginLog.Cursor c = null;
        if (o.has("cursor") && o.get("cursor").isJsonObject()) {
            JsonObject j = o.getAsJsonObject("cursor");
            if (j.has("file") && j.has("offset")) c = new NLoginLog.Cursor(j.get("file").getAsString(), j.get("offset").getAsLong());
        }
        cursor = c;
        known = true;
        if (!announced) {
            announced = true;
            TFClient.LOGGER.info("TF Bridge: mandando a la web los registros de nLogin (desde el {}) para /metricas", desde);
        }
    }

    private static JsonElement cursorJson(NLoginLog.Cursor c) {
        if (c == null) return com.google.gson.JsonNull.INSTANCE;
        JsonObject o = new JsonObject();
        o.addProperty("file", c.file());
        o.addProperty("offset", c.offset());
        return o;
    }
}
