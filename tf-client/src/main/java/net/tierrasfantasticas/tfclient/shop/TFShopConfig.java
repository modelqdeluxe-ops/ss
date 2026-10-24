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
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * config/tfclient/tienda.json: la tienda del servidor (/tf shop). Categorías, objetos, precios de compra y venta,
 * límites diarios, comandos y textos. Se crea la primera vez con una tienda por defecto; /tf shop recargar la vuelve a
 * leer sin reiniciar. Los precios son en las monedas del servidor (TFEconomy).
 */
public final class TFShopConfig {
    /** Un objeto de una categoría. cantidad: unidades por lote; comprar/vender: precio de un lote (0 = no se puede). */
    public record Entry(String key, String itemId, Item item, @Nullable CompoundTag nbt, String name, List<String> lore,
                        int amount, long buy, long sell, long buyDaily, long sellDaily, List<String> commands, boolean giveItem,
                        int permission) {
        public boolean buyable() {
            return buy > 0;
        }

        public boolean sellable() {
            return sell > 0 && giveItem;
        }

        /** Un lote del objeto tal como se entrega. */
        public ItemStack stack(int units) {
            ItemStack stack = new ItemStack(item, Math.max(1, units));
            if (nbt != null) stack.setTag(nbt.copy());
            return stack;
        }
    }

    public record Category(String id, String name, List<String> description, Item icon, int color, int slot, int permission,
                           List<Entry> entries) {}

    // Ajustes generales
    public static boolean enabled = true;
    public static String title = "Tienda del servidor";
    public static Item filler = Items.BLACK_STAINED_GLASS_PANE;
    public static boolean sounds = true;
    public static boolean sellAllButton = true;
    public static boolean sellFromInventory = true;
    public static boolean log = true;
    public static List<Integer> steps = List.of(1, 8, 64);
    public static int maxLots = 64;
    public static Map<String, String> messages = new LinkedHashMap<>();
    public static Map<String, Category> categories = new LinkedHashMap<>();

    private static final Map<String, String> DEFAULT_MESSAGES = Map.ofEntries(
            Map.entry("compra", "&a✔ Compraste &f{cantidad}× {objeto} &apor &6{precio}&a."),
            Map.entry("venta", "&a✔ Vendiste &f{cantidad}× {objeto} &apor &6{precio}&a."),
            Map.entry("ventaTodo", "&a✔ Vendiste {lineas} objetos por &6{precio}&a."),
            Map.entry("sinDinero", "&cNo tienes bastantes monedas: cuesta &6{precio}&c (te faltan &6{faltan}&c)."),
            Map.entry("sinObjetos", "&cNo tienes {objeto} para vender (sin nombre, encantamientos ni daño)."),
            Map.entry("nadaQueVender", "&cNo tienes nada que se pueda vender aquí."),
            Map.entry("limiteCompra", "&cHoy ya no puedes comprar más {objeto} (límite diario: {limite})."),
            Map.entry("limiteCompraQueda", "&cHoy solo puedes comprar {quedan} más de {objeto} (límite diario: {limite})."),
            Map.entry("limiteVenta", "&cHoy ya no puedes vender más {objeto} (límite diario: {limite})."),
            Map.entry("sinPermiso", "&cNo tienes permiso para esto."),
            Map.entry("desactivada", "&cLa tienda está cerrada ahora mismo."));

    /** La versión de la tienda por defecto (versionTienda; la misma que VERSION de tools/tienda/precios.py). */
    static final int VERSION = 3;

    /**
     * (1.3.135, el dueño: «en la tienda solo la x10, x25, x40, x64 y x80; las de 100 en adelante van con los rangos») Las
     * piedras de protección que ya no se venden en la tienda.
     */
    private static final List<String> RANK_STONES = List.of("tfclient:proteccion_100x100",
            "tfclient:proteccion_150x150", "tfclient:proteccion_250x250", "tfclient:proteccion_300x300", "tfclient:proteccion_500x500");

    /** Una tienda de la versión 2: quita esas piedras (copia «tienda.antes-v3.json») y la deja en la versión 3. */
    private static JsonObject dropRankStones(Path file, JsonObject json) {
        if (json == null || TFJson.num(json, "versionTienda", 0) >= VERSION) return json;
        int removed = 0;
        if (json.has("categorias") && json.get("categorias").isJsonArray()) {
            for (JsonElement c : json.getAsJsonArray("categorias")) {
                if (!c.isJsonObject() || !c.getAsJsonObject().has("objetos") || !c.getAsJsonObject().get("objetos").isJsonArray()) continue;
                JsonArray items = c.getAsJsonObject().getAsJsonArray("objetos");
                for (int i = items.size() - 1; i >= 0; i--) {
                    JsonElement o = items.get(i);
                    if (o.isJsonObject() && RANK_STONES.contains(TFJson.str(o.getAsJsonObject(), "objeto", ""))) {
                        items.remove(i);
                        removed++;
                    }
                }
                if (TFJson.str(c.getAsJsonObject(), "id", "").equals("protecciones")) {
                    JsonArray d = new JsonArray();
                    d.add("Piedras de protección: 10x10 a 80x80 (las de 100x100 en adelante van con los rangos).");
                    c.getAsJsonObject().add("descripcion", d);
                }
            }
        }
        try {
            Path backup = file.resolveSibling("tienda.antes-v3.json");
            if (!Files.exists(backup)) Files.copy(file, backup);
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Tienda: no se pudo guardar la copia de tienda.json: {}", e.getMessage());
        }
        json.addProperty("versionTienda", VERSION);
        TFJson.write(file, json);
        TFClient.LOGGER.info("TF Tienda: quitadas {} piedras de protección de los rangos (la de antes, en tienda.antes-v3.json)", removed);
        return json;
    }

    private TFShopConfig() {}

    public static Path file() {
        return net.tierrasfantasticas.tfclient.util.TFConfigDir.file("tienda.json", "tfclient-shop.json");
    }

    public static String message(String key) {
        return messages.getOrDefault(key, DEFAULT_MESSAGES.getOrDefault(key, key));
    }

    @Nullable
    public static Category category(String id) {
        return id == null ? null : categories.get(id);
    }

    /** Lee la configuración (y la crea con la tienda por defecto la primera vez). Devuelve los avisos. */
    public static List<String> load() {
        List<String> warnings = new ArrayList<>();
        Path file = file();
        JsonObject json;
        if (!Files.exists(file)) {
            json = defaults();
            if (json != null) TFJson.write(file, json);
            TFClient.LOGGER.info("TF Tienda: creado {} con la tienda por defecto", file);
        } else {
            json = TFJson.read(file);
            if (json == null) {
                warnings.add(file.getFileName() + " no es un JSON válido: se mantiene la tienda de antes");
                return warnings;
            }
            // (1.3.134) todos los objetos del juego con precios del estudio de la economía (tools/tienda/precios.py,
            // TIENDA.md) y la tienda solo compra Tesoros: la de antes se guarda (tienda.antes-v2.json) y se cambia
            json = net.tierrasfantasticas.tfclient.util.TFConfigDir.upgrade(file, json, "versionTienda", 2, defaults(), "tienda.json");
            // (1.3.135) a la de la 1.3.134 solo se le quitan las piedras que van con los rangos (sin tocar lo demás)
            json = dropRankStones(file, json);
        }
        if (json == null) return warnings;
        enabled = TFJson.bool(json, "activada", true);
        title = TFJson.str(json, "titulo", "Tienda del servidor");
        filler = item(TFJson.str(json, "relleno", "minecraft:black_stained_glass_pane"), Items.BLACK_STAINED_GLASS_PANE, "relleno", warnings);
        sounds = TFJson.bool(json, "sonidos", true);
        sellAllButton = TFJson.bool(json, "botonVenderTodo", true);
        sellFromInventory = TFJson.bool(json, "venderDesdeInventario", true);
        log = TFJson.bool(json, "registro", true);
        maxLots = (int) Math.max(1, Math.min(2304, TFJson.num(json, "maximoPorCompra", 64)));
        List<Integer> parsedSteps = new ArrayList<>();
        if (json.has("pasos") && json.get("pasos").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("pasos")) {
                try {
                    int n = el.getAsInt();
                    if (n > 0 && parsedSteps.size() < 3 && !parsedSteps.contains(n)) parsedSteps.add(n);
                } catch (RuntimeException ignored) {
                    // no es un número
                }
            }
        }
        steps = parsedSteps.isEmpty() ? List.of(1, 8, 64) : List.copyOf(parsedSteps);
        Map<String, String> msgs = new LinkedHashMap<>(DEFAULT_MESSAGES);
        JsonObject m = TFJson.obj(json, "mensajes");
        for (String key : m.keySet()) msgs.put(key, TFJson.str(m, key, msgs.getOrDefault(key, "")));
        messages = msgs;

        Map<String, Category> parsed = new LinkedHashMap<>();
        JsonArray list = json.has("categorias") && json.get("categorias").isJsonArray() ? json.getAsJsonArray("categorias") : new JsonArray();
        for (JsonElement el : list) {
            if (!el.isJsonObject()) continue;
            JsonObject c = el.getAsJsonObject();
            String id = TFJson.str(c, "id", "").toLowerCase(Locale.ROOT);
            if (!id.matches("[a-z0-9_]{1,32}") || parsed.containsKey(id)) {
                warnings.add("categoría sin id válido o repetido (letras, números y _): " + TFJson.str(c, "nombre", "?"));
                continue;
            }
            List<Entry> entries = new ArrayList<>();
            int index = 0;
            for (JsonElement ie : array(c, "objetos")) {
                index++;
                JsonObject o = ie.getAsJsonObject();
                String itemId = TFJson.str(o, "objeto", "");
                Item item = itemOrNull(itemId);
                if (item == null) {
                    warnings.add(id + ": el objeto «" + itemId + "» no existe (¿falta el mod?)");
                    continue;
                }
                CompoundTag nbt = null;
                String snbt = TFJson.str(o, "nbt", "");
                if (!snbt.isBlank()) {
                    try {
                        nbt = TagParser.parseTag(snbt);
                    } catch (Exception e) {
                        warnings.add(id + ": NBT no válido en «" + itemId + "»: " + e.getMessage());
                        continue;
                    }
                }
                List<String> commands = strings(o, "comandos");
                String key = TFJson.str(o, "id", id + "_" + index).toLowerCase(Locale.ROOT);
                entries.add(new Entry(key, itemId, item, nbt, TFJson.str(o, "nombre", ""), strings(o, "descripcion"),
                        (int) Math.max(1, Math.min(6400, TFJson.num(o, "cantidad", 1))),
                        Math.max(0, TFJson.num(o, "comprar", 0)), Math.max(0, TFJson.num(o, "vender", 0)),
                        Math.max(0, TFJson.num(o, "limiteCompraDiario", 0)), Math.max(0, TFJson.num(o, "limiteVentaDiario", 0)),
                        commands, TFJson.bool(o, "darObjeto", commands.isEmpty()), (int) Math.max(0, TFJson.num(o, "nivelPermiso", 0))));
            }
            int color = 0xFCC94A;
            try {
                color = Integer.parseInt(TFJson.str(c, "color", "#fcc94a").replace("#", ""), 16);
            } catch (NumberFormatException e) {
                warnings.add(id + ": color no válido");
            }
            List<String> description = strings(c, "descripcion");
            if (description.isEmpty() && c.has("descripcion") && c.get("descripcion").isJsonPrimitive()) {
                description = List.of(c.get("descripcion").getAsString());
            }
            parsed.put(id, new Category(id, TFJson.str(c, "nombre", id), description,
                    item(TFJson.str(c, "icono", "minecraft:chest"), Items.CHEST, id + ".icono", warnings), color,
                    (int) TFJson.num(c, "hueco", -1), (int) Math.max(0, TFJson.num(c, "nivelPermiso", 0)), entries));
        }
        categories = parsed;
        int total = categories.values().stream().mapToInt(cat -> cat.entries().size()).sum();
        TFClient.LOGGER.info("TF Tienda: {} categorías y {} objetos{}", categories.size(), total,
                warnings.isEmpty() ? "" : " (" + warnings.size() + " avisos)");
        warnings.forEach(w -> TFClient.LOGGER.warn("TF Tienda: {}", w));
        return warnings;
    }

    @Nullable
    private static Item itemOrNull(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !ForgeRegistries.ITEMS.containsKey(rl)) return null;
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        return item == null || item == Items.AIR ? null : item;
    }

    private static Item item(String id, Item fallback, String where, List<String> warnings) {
        Item item = itemOrNull(id);
        if (item == null) {
            warnings.add(where + ": el objeto «" + id + "» no existe");
            return fallback;
        }
        return item;
    }

    private static JsonObject defaults() {
        try (InputStream in = TFShopConfig.class.getResourceAsStream("/tfclient-shop-default.json")) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            TFClient.LOGGER.error("TF Tienda: no se pudo leer la tienda por defecto", e);
            return null;
        }
    }

    private static JsonArray array(JsonObject o, String key) {
        JsonArray out = new JsonArray();
        if (o.has(key) && o.get(key).isJsonArray()) {
            for (JsonElement el : o.getAsJsonArray(key)) if (el.isJsonObject()) out.add(el);
        }
        return out;
    }

    static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (o != null && o.has(key) && o.get(key).isJsonArray()) {
            for (JsonElement el : o.getAsJsonArray(key)) if (el.isJsonPrimitive()) out.add(el.getAsString());
        }
        return out;
    }
}
