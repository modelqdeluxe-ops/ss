/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.skins;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Traer la skin de un jugador de Minecraft por su nombre (en el servidor, en su propio hilo): api.mojang.com da su id,
 * sessionserver.mojang.com sus texturas (la dirección de la skin y si es de brazos finos) y la imagen se baja de
 * textures.minecraft.net (de ningún otro sitio). Si algo falla, el motivo en español.
 */
final class SkinMojang {
    static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final int TIMEOUT_MS = 8000;
    private static final ExecutorService HTTP = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "TF Skins");
        thread.setDaemon(true);
        return thread;
    });

    /** Lo que sale: la skin (o null) y, si no, por qué; name: el nombre bien escrito (el de Mojang). */
    record Result(SkinImages.Result skin, boolean slim, String name, String error) {}

    private SkinMojang() {}

    /** Pide la skin de name; done se llama en el hilo de las descargas (quien la usa la pasa al del servidor). */
    static void fetch(String name, Consumer<Result> done) {
        HTTP.execute(() -> {
            Result r;
            try {
                r = get(name);
            } catch (Exception e) {
                TFClient.LOGGER.info("TF Skins: no se pudo traer la skin de {}: {}", name, e.toString());
                r = new Result(null, false, name, "No se pudo hablar con Mojang. Prueba en un rato.");
            }
            done.accept(r);
        });
    }

    private static Result get(String name) throws Exception {
        Response profile = http("https://api.mojang.com/users/profiles/minecraft/" + name, 16 * 1024);
        if (profile.status == 204 || profile.status == 404 || profile.status == 400) {
            return new Result(null, false, name, "No hay ningún jugador de Minecraft que se llame " + name + ".");
        }
        if (profile.status == 429) return new Result(null, false, name, "Mojang pide esperar un poco. Prueba en un minuto.");
        if (profile.status != 200) return new Result(null, false, name, "Mojang no contesta ahora (" + profile.status + ").");
        JsonObject p = JsonParser.parseString(profile.text()).getAsJsonObject();
        String id = TFJson.str(p, "id", "");
        String realName = TFJson.str(p, "name", name);
        if (!id.matches("[0-9a-fA-F]{32}")) return new Result(null, false, name, "Mojang no dio ese jugador.");
        Response session = http("https://sessionserver.mojang.com/session/minecraft/profile/" + id, 64 * 1024);
        if (session.status != 200) return new Result(null, false, realName, "Mojang no contesta ahora (" + session.status + ").");
        JsonObject s = JsonParser.parseString(session.text()).getAsJsonObject();
        String textures = null;
        if (s.has("properties") && s.get("properties").isJsonArray()) {
            for (JsonElement el : s.getAsJsonArray("properties")) {
                if (el.isJsonObject() && "textures".equals(TFJson.str(el.getAsJsonObject(), "name", ""))) {
                    textures = TFJson.str(el.getAsJsonObject(), "value", null);
                }
            }
        }
        if (textures == null) return new Result(null, false, realName, realName + " no tiene skin propia.");
        JsonObject t = JsonParser.parseString(new String(Base64.getDecoder().decode(textures), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject skin = TFJson.obj(TFJson.obj(t, "textures"), "SKIN");
        String url = TFJson.str(skin, "url", "");
        if (url.isEmpty()) return new Result(null, false, realName, realName + " lleva la skin por defecto (no tiene una propia).");
        boolean slim = "slim".equals(TFJson.str(TFJson.obj(skin, "metadata"), "model", ""));
        URI uri = URI.create(url);
        if (!"textures.minecraft.net".equalsIgnoreCase(uri.getHost())) return new Result(null, false, realName, "Mojang dio una dirección rara.");
        Response png = http("https://textures.minecraft.net" + uri.getRawPath(), SkinImages.MAX_INPUT);
        if (png.status != 200) return new Result(null, false, realName, "No se pudo bajar la skin (" + png.status + ").");
        try {
            return new Result(SkinImages.normalize(png.body), slim, realName, null);
        } catch (SkinImages.Bad bad) {
            return new Result(null, false, realName, bad.getMessage());
        }
    }

    private record Response(int status, byte[] body) {
        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    /** GET con tiempo límite; lee como mucho max bytes (si hay más, falla). */
    private static Response http(String url, int max) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent", "TF-Client/" + TFClient.VERSION);
        try {
            int status = c.getResponseCode();
            InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null) return new Response(status, new byte[0]);
            try (in) {
                byte[] body = in.readNBytes(max + 1);
                if (body.length > max) throw new java.io.IOException("respuesta demasiado grande");
                return new Response(status, body);
            }
        } finally {
            c.disconnect();
        }
    }
}
