/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * (1.3.114, el dueño: «la economía es demasiado elevada, está muy inflada la moneda, se gana mucho y regalamos mucho;
 * la moneda debe ser estable») lo que se regalaba sin trabajarlo, rebajado. Con la tienda «tipo dólar» (un diamante,
 * 150) y lo que gana un jugador normal (~400 al día: oficio, misiones, cazas), el TF Pass daba ~11.000 monedas por
 * temporada y el Gachapón de 100 a 5.000 por tirada (65 tiradas por temporada), además de 8 diamantes, tótems y
 * netherita: más que todo lo trabajado. Ahora el pase da ~1.700 por temporada y el gacha la quinta parte, y los
 * objetos raros, menos.
 * <p>
 * Las configs del servidor que ya existían (pase.json y gachapon.json, version 1) se rebajan igual al cargar, sin
 * cambiar nada más (la temporada, los niveles, lo que añadió el dueño): se guarda una copia «.antes-v2.json».
 */
public final class PadEconomy {
    private PadEconomy() {}

    /** Los objetos raros, a cuánto se quedan (lo demás, igual). */
    static final Map<String, Double> RARE = Map.of(
            "minecraft:diamond", 0.4,
            "minecraft:netherite_scrap", 0.5,
            "minecraft:totem_of_undying", 0.5,
            "minecraft:enchanted_golden_apple", 0.34,
            "minecraft:golden_apple", 0.5,
            "minecraft:ender_pearl", 0.5);

    /**
     * Si la config es de antes (version &lt; 2): las monedas de cada premio (tipo «monedas») por coinFactor y los objetos
     * raros por RARE (como mínimo 1), copia de la vieja y version 2. Devuelve la que vale.
     */
    static JsonObject deflate(Path file, JsonObject o, double coinFactor, String what) {
        if (o == null || TFJson.num(o, "version", 1) >= 2) return o;
        try {
            String name = file.getFileName().toString().replaceFirst("\\.json$", "");
            Path backup = file.resolveSibling(name + ".antes-v2.json");
            if (Files.exists(file) && !Files.exists(backup)) Files.copy(file, backup);
            int[] n = {0};
            walk(o, coinFactor, n);
            o.addProperty("version", 2);
            TFJson.write(file, o);
            TFClient.LOGGER.info("TF Pad: {}: economía estable (1.3.114), {} premios rebajados (la de antes, en {})", what, n[0],
                    backup.getFileName());
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Pad: no se pudo rebajar {}: {}", what, e.getMessage());
        }
        return o;
    }

    /**
     * (1.3.115, el dueño: «no quiero una economía inflada, que se dé mucho dinero; el server va para largo, así que debe
     * ser una economía más exigente, ya que habrá muchas formas de ganar dinero») lo que se gana trabajando también baja:
     * los oficios (lo que paga cada acción, las misiones y los niveles), las misiones del pad y las cazas, al 60 %. A las
     * configs que ya había se les aplica una vez (marca «economia»: 3; copia «.antes-economia3.json»), sin tocar nada
     * más de lo que cambió el dueño. Devuelve la que vale.
     */
    public static JsonObject scale(Path file, JsonObject o, double factor, String what, String... fields) {
        if (o == null || TFJson.num(o, "economia", 0) >= 3) return o;
        try {
            String name = file.getFileName().toString().replaceFirst("\\.json$", "");
            Path backup = file.resolveSibling(name + ".antes-economia3.json");
            if (Files.exists(file) && !Files.exists(backup)) Files.copy(file, backup);
            int[] n = {0};
            scaleWalk(o, factor, java.util.Set.of(fields), n);
            o.addProperty("economia", 3);
            TFJson.write(file, o);
            TFClient.LOGGER.info("TF Pad: {}: economía exigente (1.3.115), {} pagos al {} % (la de antes, en {})", what, n[0],
                    Math.round(factor * 100), backup.getFileName());
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Pad: no se pudo ajustar {}: {}", what, e.getMessage());
        }
        return o;
    }

    private static void scaleWalk(JsonElement e, double f, java.util.Set<String> fields, int[] n) {
        if (e == null) return;
        if (e.isJsonArray()) {
            for (JsonElement c : e.getAsJsonArray()) scaleWalk(c, f, fields, n);
            return;
        }
        if (!e.isJsonObject()) return;
        JsonObject o = e.getAsJsonObject();
        for (Map.Entry<String, JsonElement> c : o.entrySet()) {
            JsonElement v = c.getValue();
            if (fields.contains(c.getKey()) && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
                double d = v.getAsDouble() * f;
                if (v.getAsDouble() > 0) {
                    c.setValue(new com.google.gson.JsonPrimitive(d < 10 ? Math.round(d * 100) / 100.0 : (double) Math.max(1, Math.round(d))));
                    n[0]++;
                }
            } else if (v.isJsonObject() || v.isJsonArray()) {
                scaleWalk(v, f, fields, n);
            }
        }
    }

    /**
     * Pone «_ayuda» (lo que significa cada clave) al principio de la config si no la tiene, para que se pueda cambiar a
     * mano o con una IA sin leer el código (1.3.114). Devuelve la que vale.
     */
    static JsonObject help(Path file, JsonObject o, String... lines) {
        if (o == null || o.has("_ayuda")) return o;
        JsonObject out = new JsonObject();
        com.google.gson.JsonArray a = new com.google.gson.JsonArray();
        for (String l : lines) a.add(l);
        out.add("_ayuda", a);
        for (Map.Entry<String, JsonElement> e : o.entrySet()) out.add(e.getKey(), e.getValue());
        if (Files.exists(file)) TFJson.write(file, out);
        return out;
    }

    private static void walk(JsonElement e, double coinFactor, int[] n) {
        if (e == null) return;
        if (e.isJsonArray()) {
            for (JsonElement c : e.getAsJsonArray()) walk(c, coinFactor, n);
            return;
        }
        if (!e.isJsonObject()) return;
        JsonObject o = e.getAsJsonObject();
        String type = TFJson.str(o, "tipo", "");
        if (o.has("cantidad") && type.equals("monedas")) {
            long v = TFJson.num(o, "cantidad", 0);
            o.addProperty("cantidad", Math.max(1, Math.round(v * coinFactor / 5.0) * 5));
            n[0]++;
        } else if (o.has("cantidad") && type.equals("objeto")) {
            Double f = RARE.get(TFJson.str(o, "id", ""));
            if (f != null) {
                o.addProperty("cantidad", Math.max(1, Math.round(TFJson.num(o, "cantidad", 1) * f)));
                n[0]++;
            }
        }
        for (Map.Entry<String, JsonElement> c : o.entrySet()) {
            if (c.getValue().isJsonObject() || c.getValue().isJsonArray()) walk(c.getValue(), coinFactor, n);
        }
    }

    /** (1.3.153) Lo que se gana trabajando (oficios, misiones del pad y cazas), otra vez a la mitad. */
    public static final double COINS_4 = 0.5;
    /** (1.3.153) La curva de niveles de los oficios ahora que solo suben con las misiones (ver economy4). */
    public static final double JOB_XP_BASE = 150, JOB_XP_FACTOR = 1.075;

    /**
     * (1.3.153, el dueño: «necesito que des menos monedas en los oficios, misiones, etc, y que los jobs no me suban nivel
     * cuando mino o hago la acción, solo me debe dar al completar la misión» y «quita recompensas que den netherita») A
     * las configs que ya había, una vez (marca «economia»: 4; copia «.antes-economia4.json»): las monedas de los campos
     * dados a la mitad (COINS_4) y, en oficios.json (jobs), las acciones sin XP y la curva de niveles para subir solo con
     * misiones: con la de antes (120 × 1,15^nivel) un nivel 30 pedía ~15 días de misiones y el 49, ~180; con 150 ×
     * 1,075^nivel, de medio día al principio a ~6 días al final (el 50, en unos 4 meses haciendo las 3 diarias). También
     * quita la netherita de los premios ({@link #withoutNetherite}). Lo demás que cambió el dueño se queda. Devuelve la
     * que vale.
     */
    public static JsonObject economy4(Path file, JsonObject o, String what, boolean jobs, String... fields) {
        if (o == null || TFJson.num(o, "economia", 0) >= 4) return o;
        try {
            String name = file.getFileName().toString().replaceFirst("\\.json$", "");
            Path backup = file.resolveSibling(name + ".antes-economia4.json");
            if (Files.exists(file) && !Files.exists(backup)) Files.copy(file, backup);
            int[] n = {0};
            scaleWalk(o, COINS_4, java.util.Set.of(fields), n);
            if (jobs) {
                if (o.has("oficios") && o.get("oficios").isJsonArray()) {
                    for (JsonElement j : o.getAsJsonArray("oficios")) {
                        if (!j.isJsonObject() || !j.getAsJsonObject().has("acciones") || !j.getAsJsonObject().get("acciones").isJsonArray()) continue;
                        for (JsonElement a : j.getAsJsonObject().getAsJsonArray("acciones")) {
                            if (a.isJsonObject()) a.getAsJsonObject().addProperty("xp", 0);
                        }
                    }
                }
                JsonObject levels = TFJson.obj(o, "niveles");
                levels.addProperty("xpBase", JOB_XP_BASE);
                levels.addProperty("multiplicador", JOB_XP_FACTOR);
                if (!o.has("niveles")) o.add("niveles", levels);
            }
            int gone = stripNetherite(o, "");
            o.addProperty("economia", 4);
            o.addProperty("sinNetherita", true);
            TFJson.write(file, o);
            TFClient.LOGGER.info("TF Pad: {}: economía 4 (1.3.153), {} pagos a la mitad{}{} (la de antes, en {})", what, n[0],
                    jobs ? ", las acciones sin XP y los niveles con la curva de las misiones" : "",
                    gone > 0 ? ", " + gone + " premios de netherita quitados" : "", backup.getFileName());
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Pad: no se pudo ajustar {}: {}", what, e.getMessage());
        }
        return o;
    }

    /**
     * (1.3.153, el dueño: «quita recompensas que den netherita») Quita de una config de premios (pase, gachapón, kits,
     * recompensas…), una vez (marca «sinNetherita»), cada premio que dé netherita o escombros ancestrales: los objetos
     * («minecraft:netherite_ingot 1» en una lista «objetos») y los premios {tipo: objeto, id: …}. Si un nivel o día se
     * queda sin premios, se le pone un diamante para que no salga en blanco. Devuelve la que vale.
     */
    public static JsonObject withoutNetherite(Path file, JsonObject o, String what) {
        if (o == null || TFJson.bool(o, "sinNetherita", false)) return o;
        try {
            int gone = stripNetherite(o, "");
            o.addProperty("sinNetherita", true);
            if (Files.exists(file)) TFJson.write(file, o);
            if (gone > 0) TFClient.LOGGER.info("TF Pad: {}: {} premios de netherita quitados (1.3.153)", what, gone);
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Pad: no se pudo quitar la netherita de {}: {}", what, e.getMessage());
        }
        return o;
    }

    /** Quita los premios de netherita de debajo de e (key: la clave de e en su padre). Devuelve cuántos quitó. */
    public static int stripNetherite(JsonElement e, String key) {
        int n = 0;
        if (e == null) return 0;
        if (e.isJsonObject()) {
            for (Map.Entry<String, JsonElement> c : e.getAsJsonObject().entrySet()) n += stripNetherite(c.getValue(), c.getKey());
        } else if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            int before = a.size();
            for (int i = a.size() - 1; i >= 0; i--) {
                if (netheriteReward(a.get(i), key)) {
                    a.remove(i);
                    n++;
                } else {
                    n += stripNetherite(a.get(i), key);
                }
            }
            // un nivel del pase o un día del calendario que solo daba netherita: un diamante, para que no salga vacío
            if (before > 0 && a.isEmpty() && key.equals("premios")) {
                JsonObject d = new JsonObject();
                d.addProperty("tipo", "objeto");
                d.addProperty("id", "minecraft:diamond");
                d.addProperty("cantidad", 1);
                a.add(d);
            }
        }
        return n;
    }

    /** Un premio que da netherita: «minecraft:netherite_ingot 1» en «objetos», o {tipo: objeto, id/nbt con netherita}. */
    private static boolean netheriteReward(JsonElement c, String key) {
        if (c.isJsonPrimitive()) return key.equals("objetos") && netherite(c.getAsString());
        if (!c.isJsonObject()) return false;
        JsonObject o = c.getAsJsonObject();
        return TFJson.str(o, "tipo", "objeto").equals("objeto") && (netherite(TFJson.str(o, "id", "")) || netherite(TFJson.str(o, "nbt", "")));
    }

    /** Un objeto de netherita («minecraft:netherite_ingot», «minecraft:netherite_sword{…}», «minecraft:ancient_debris 2»…). */
    static boolean netherite(String s) {
        String l = s.toLowerCase(java.util.Locale.ROOT);
        return l.contains(":") && (l.contains("netherite") || l.contains("ancient_debris"));
    }
}
