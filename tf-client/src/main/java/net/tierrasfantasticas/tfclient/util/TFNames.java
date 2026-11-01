/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * (1.3.159, el dueño: «pon los items en español», «usamos español latinoamericano») El nombre de un objeto en español
 * también en el servidor, que no tiene más idioma que el inglés: los de Minecraft salen de
 * assets/tfclient/pad/nombres_es.json (el es_mx.json oficial de la 1.20.1, solo objetos y bloques) y los de los mods,
 * de su propio es_mx.json (o es_es.json si no traen el de México) dentro de su jar. Lo que no tiene traducción sale como
 * en el juego (en inglés). Los objetos con nombre puesto (yunque) salen con ese nombre.
 */
public final class TFNames {
    private static volatile Map<String, String> table;

    private TFNames() {}

    /** El nombre en español de lo que se ve (con su nombre puesto, si lo tiene). */
    public static String es(ItemStack s) {
        if (s == null || s.isEmpty()) return "";
        if (s.hasCustomHoverName()) return s.getHoverName().getString();
        String n = table().get(s.getItem().getDescriptionId(s));
        if (n == null) n = table().get(s.getItem().getDescriptionId());
        return n != null ? n : s.getHoverName().getString();
    }

    public static String es(Item item) {
        return es(new ItemStack(item));
    }

    /** Para buscar: sin mayúsculas ni acentos ni signos («Piedra labrada» → «piedra labrada»). */
    public static String fold(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9ñ]+", " ").trim();
    }

    /**
     * ¿Lo que se busca está en este objeto? Cada palabra buscada tiene que estar en su nombre en español, en el de
     * inglés o en su id (diamond_sword); así «espada dia» y «diamond» encuentran la espada de diamante.
     */
    public static boolean matches(ItemStack s, String query) {
        String q = fold(query);
        if (q.isEmpty()) return true;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        String hay = fold(es(s)) + " " + fold(s.getHoverName().getString()) + " " + (id == null ? "" : fold(id.getPath().replace('_', ' ')));
        for (String w : q.split(" ")) if (!hay.contains(w)) return false;
        return true;
    }

    static Map<String, String> table() {
        Map<String, String> t = table;
        if (t != null) return t;
        synchronized (TFNames.class) {
            if (table != null) return table;
            Map<String, String> out = new HashMap<>();
            try (InputStream in = TFNames.class.getResourceAsStream("/assets/tfclient/pad/nombres_es.json")) {
                if (in != null) read(new InputStreamReader(in, StandardCharsets.UTF_8), out, false);
            } catch (Exception e) {
                TFClient.LOGGER.warn("TF Client: no se pudieron leer los nombres en español de Minecraft", e);
            }
            int mods = 0;
            try {
                for (var info : ModList.get().getModFiles()) {
                    Path assets = info.getFile().findResource("assets");
                    if (assets == null || !Files.isDirectory(assets)) continue;
                    try (Stream<Path> ns = Files.list(assets)) {
                        for (Path dir : ns.toList()) {
                            if (dir.getFileName().toString().equals("minecraft")) continue;
                            for (String lang : List.of("es_mx.json", "es_es.json", "es_ar.json", "es_cl.json")) {
                                Path p = dir.resolve("lang").resolve(lang);
                                if (!Files.isRegularFile(p)) continue;
                                try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                                    read(r, out, true);
                                    mods++;
                                } catch (Exception e) {
                                    // un archivo de idioma roto: se salta
                                }
                            }
                        }
                    }
                }
            } catch (Exception | LinkageError e) {
                TFClient.LOGGER.warn("TF Client: no se pudieron leer los nombres en español de los mods", e);
            }
            TFClient.LOGGER.info("TF Client: {} nombres de objetos en español ({} archivos de idioma de mods)", out.size(), mods);
            table = out;
            return out;
        }
    }

    /** Lee un archivo de idioma (solo item.* y block.*); keep: lo que ya estaba se queda (el es_mx antes que el es_es). */
    private static void read(Reader r, Map<String, String> out, boolean keep) {
        JsonElement root = JsonParser.parseReader(r);
        if (!root.isJsonObject()) return;
        JsonObject o = root.getAsJsonObject();
        for (String k : o.keySet()) {
            if (!(k.startsWith("item.") || k.startsWith("block.")) || !o.get(k).isJsonPrimitive()) continue;
            if (keep) out.putIfAbsent(k, o.get(k).getAsString());
            else out.put(k, o.get(k).getAsString());
        }
    }
}
