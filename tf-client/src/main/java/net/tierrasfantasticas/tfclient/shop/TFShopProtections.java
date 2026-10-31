/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.shop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * (1.3.156, el dueño: «pon el límite a las de la tienda: solo pueden comprar 1 de cada tipo») Las piedras de protección
 * de la tienda del juego: cada jugador compra como mucho una de cada tamaño, para siempre. Lo comprado se guarda en
 * &lt;mundo&gt;/tfclient/tienda_protecciones.json ({uuid: [«tfclient:proteccion_10x10», …]}).
 */
public final class TFShopProtections {
    private static final String FILE = "tienda_protecciones.json";
    private static JsonObject data;
    private static MinecraftServer loadedFor;

    private TFShopProtections() {}

    /** ¿Es una piedra de protección (con límite de una por jugador)? */
    public static boolean limited(String itemId) {
        return itemId != null && itemId.startsWith("tfclient:proteccion_");
    }

    private static JsonObject data(MinecraftServer server) {
        if (data == null || loadedFor != server) {
            JsonObject read = server == null ? null : TFJson.read(TFJson.worldFile(server, FILE));
            data = read == null ? new JsonObject() : read;
            loadedFor = server;
        }
        return data;
    }

    /** ¿Ya compró una de ese tipo? */
    public static boolean bought(MinecraftServer server, UUID uuid, String itemId) {
        JsonElement e = data(server).get(uuid.toString());
        if (e == null || !e.isJsonArray()) return false;
        for (JsonElement x : e.getAsJsonArray()) if (x.getAsString().equals(itemId)) return true;
        return false;
    }

    /** Apunta que compró una de ese tipo. */
    public static void mark(MinecraftServer server, UUID uuid, String itemId) {
        JsonObject d = data(server);
        JsonArray a = d.has(uuid.toString()) && d.get(uuid.toString()).isJsonArray() ? d.getAsJsonArray(uuid.toString()) : new JsonArray();
        a.add(itemId);
        d.add(uuid.toString(), a);
        if (server != null) TFJson.write(TFJson.worldFile(server, FILE), d);
    }

    /** Para la prueba en el juego: olvida lo comprado por ese jugador. */
    public static void forget(MinecraftServer server, UUID uuid) {
        data(server).remove(uuid.toString());
        if (server != null) TFJson.write(TFJson.worldFile(server, FILE), data(server));
    }
}
