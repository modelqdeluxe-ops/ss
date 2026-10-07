package net.tierrasfantasticas.tfclient.items;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraftforge.fml.ModList;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Los sets de Tierras Fantásticas (armas, herramientas, armaduras y cosméticos), leídos de
 * assets/tfclient/tf_sets.json, que genera tools/build_mod_items.py con los mismos objetos que enseña la web.
 */
public final class TFSets {
    /** Un objeto de un set. type: sword, heavy, axe, pickaxe, shovel, hoe, bow, crossbow, fishing_rod, shield,
     *  trident, head (cosmético en la cabeza), back (cosmético en la espalda), held (cosmético en la mano), balloon
     *  (globo en la mano) o armor. glide: unas alas, que planean (TFWings). */
    public record ItemDef(String id, String type, String slot, String worn, boolean glide) {}

    /** tier: nivel de atributos del set (ver TFTier). cosmetic: set de cosméticos (sus objetos no se vinculan, TFBinding). */
    public record SetDef(String id, String name, int color, List<ItemDef> items, int armorFrames, int armorFrametime, TFTier tier,
                         boolean cosmetic) {}

    private static final Map<String, SetDef> SETS = new LinkedHashMap<>();

    private TFSets() {}

    public static Map<String, SetDef> all() {
        return Collections.unmodifiableMap(SETS);
    }

    public static SetDef get(String id) {
        return SETS.get(id);
    }

    public static void load() {
        SETS.clear();
        try {
            Path path = ModList.get().getModFileById(TFClient.MOD_ID).getFile().findResource("assets", TFClient.MOD_ID, "tf_sets.json");
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                for (JsonElement el : root.getAsJsonArray("sets")) {
                    JsonObject s = el.getAsJsonObject();
                    List<ItemDef> items = new ArrayList<>();
                    JsonArray arr = s.getAsJsonArray("items");
                    for (JsonElement ie : arr) {
                        JsonObject i = ie.getAsJsonObject();
                        items.add(new ItemDef(
                                i.get("id").getAsString(),
                                i.get("type").getAsString(),
                                i.has("slot") ? i.get("slot").getAsString() : null,
                                i.has("worn") ? i.get("worn").getAsString() : null,
                                i.has("glide") && i.get("glide").getAsBoolean()));
                    }
                    int color = Integer.parseInt(s.get("color").getAsString().substring(1), 16);
                    SETS.put(s.get("id").getAsString(), new SetDef(
                            s.get("id").getAsString(), s.get("name").getAsString(), color, List.copyOf(items),
                            s.has("armorFrames") ? s.get("armorFrames").getAsInt() : 1,
                            s.has("armorFrametime") ? Math.max(1, s.get("armorFrametime").getAsInt()) : 2,
                            TFTier.parse(s.has("tier") ? s.get("tier").getAsString() : null),
                            s.has("cosmetic") && s.get("cosmetic").getAsBoolean()));
                }
            }
            TFClient.LOGGER.info("TF Client: {} sets cargados", SETS.size());
        } catch (Exception e) {
            TFClient.LOGGER.error("TF Client: no se pudo leer tf_sets.json", e);
        }
    }
}
