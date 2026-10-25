package net.tierrasfantasticas.tfclient.jobs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * (1.3.143, el dueño: «verifica todas las misiones, en jobs y todo lo demás, que esté bien; no quiero más actualizaciones
 * de este tipo») Mira, con los registros y las recetas del servidor, que cada misión se pueda hacer: que el bloque, el
 * objeto o el mob existan (o la etiqueta, y que no esté vacía), que el tipo vaya con el objetivo, que lo de «fabricar»
 * salga de una receta de la mesa, lo de «fundir» de un horno, ahumador o alto horno y que lo de «criar» se pueda criar.
 * Al arrancar el servidor avisa en la consola de lo que esté mal en oficios.json y misiones.json; la prueba en el juego
 * (BossTest «oficios») lo pasa por las que van en el mod.
 */
public final class TFMissionCheck {
    private TFMissionCheck() {}

    private static final List<String> BLOCK_TYPES = List.of("romper", "cosechar", "colocar");
    private static final List<String> ENTITY_TYPES = List.of("matar", "criar");

    /** Revisa las de oficios.json y misiones.json del servidor y avisa en la consola. */
    public static void logProblems(MinecraftServer server) {
        try {
            List<String> problems = new ArrayList<>();
            int n = checkJobs(server, TFJson.read(TFJobsConfig.file()), problems)
                    + checkPad(server, TFJson.read(net.tierrasfantasticas.tfclient.util.TFConfigDir.file("misiones.json", "tfclient-misiones.json")), problems);
            if (problems.isEmpty()) {
                TFClient.LOGGER.info("TF Misiones: {} misiones revisadas, todas se pueden hacer", n);
            } else {
                TFClient.LOGGER.warn("TF Misiones: {} misiones revisadas, {} con problemas:", n, problems.size());
                for (String p : problems) TFClient.LOGGER.warn("  - {}", p);
            }
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Misiones: no se pudieron revisar: {}", e.toString());
        }
    }

    /** Las de los oficios (acciones y misiones). Devuelve cuántas revisó. */
    public static int checkJobs(MinecraftServer server, JsonObject json, List<String> problems) {
        if (json == null || !json.has("oficios")) return 0;
        int n = 0;
        for (JsonElement e : json.getAsJsonArray("oficios")) {
            JsonObject job = e.getAsJsonObject();
            String jid = TFJson.str(job, "id", "?");
            for (JsonElement a : array(job, "acciones")) {
                JsonObject o = a.getAsJsonObject();
                n++;
                one(server, "oficio " + jid + ", acción", TFJson.str(o, "tipo", ""), TFJson.str(o, "objetivo", "*"), null, problems);
            }
            for (JsonElement m : array(job, "misiones")) {
                JsonObject o = m.getAsJsonObject();
                n++;
                String where = "oficio " + jid + ", misión " + TFJson.str(o, "id", "?");
                one(server, where, TFJson.str(o, "tipo", ""), TFJson.str(o, "objetivo", "*"), TFJson.str(o, "icono", null), problems);
                if (o.has("recompensa") && o.get("recompensa").isJsonObject()) {
                    for (JsonElement it : array(o.getAsJsonObject("recompensa"), "objetos")) {
                        String id = it.getAsString().trim().split("\\s+")[0];
                        if (!itemExists(id)) problems.add(where + ": el premio «" + id + "» no existe");
                    }
                }
            }
        }
        return n;
    }

    /** Las misiones del pad (diarias y semanales) y las cazas. Devuelve cuántas revisó. */
    public static int checkPad(MinecraftServer server, JsonObject json, List<String> problems) {
        if (json == null) return 0;
        int n = 0;
        for (String key : List.of("diarias", "cazas")) {
            for (JsonElement m : array(json, key)) {
                JsonObject o = m.getAsJsonObject();
                n++;
                one(server, key + ", " + TFJson.str(o, "id", "?"), TFJson.str(o, "tipo", ""), TFJson.str(o, "objetivo", "*"),
                        TFJson.str(o, "icono", null), problems);
            }
        }
        return n;
    }

    private static void one(MinecraftServer server, String where, String type, String raw, String icon, List<String> problems) {
        String bad = problem(server, type, raw);
        if (bad != null) problems.add(where + " (" + type + " " + raw + "): " + bad);
        if (icon != null && !itemExists(icon)) problems.add(where + ": el icono «" + icon + "» no existe");
    }

    /** null si la misión se puede hacer; si no, por qué. */
    static String problem(MinecraftServer server, String type, String raw) {
        if (!TFJobsConfig.TYPES.contains(type)) return "el tipo no existe";
        boolean any = raw.equals("*");
        boolean tag = raw.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(tag ? raw.substring(1) : raw);
        if (raw.equals("hostil") || raw.equals("animal")) return type.equals("matar") ? null : "«" + raw + "» solo vale para matar";
        if (!any && id == null) return "objetivo mal escrito";
        if (type.equals("encantar")) return any ? null : "encantar solo cuenta con «*»";
        if (any) return null;
        if (BLOCK_TYPES.contains(type)) {
            // (Optional.map con null daría «no existe»: se mira el tamaño)
            if (tag && BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, id)).map(s -> s.size()).orElse(0) == 0)
                return "la etiqueta de bloques no existe o está vacía";
            if (!tag && !BuiltInRegistries.BLOCK.containsKey(id)) return "el bloque no existe";
            var sample = sample(server, type, raw);
            return sample != null && sample.test(new TFJobsConfig.Target(raw)) ? null : "un bloque de verdad de ese objetivo no cuenta (no encaja)";
        }
        if (ENTITY_TYPES.contains(type)) {
            if (tag && BuiltInRegistries.ENTITY_TYPE.getTag(TagKey.create(Registries.ENTITY_TYPE, id)).map(s -> s.size()).orElse(0) == 0)
                return "la etiqueta de mobs no existe o está vacía";
            if (!tag && !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return "el mob no existe";
            if (type.equals("criar") && !tag) {
                var entity = BuiltInRegistries.ENTITY_TYPE.get(id).create(server.overworld());
                boolean breeds = entity instanceof Animal;
                if (entity != null) entity.discard();
                if (!breeds) return "ese mob no se puede criar";
            }
            var sample = sample(server, type, raw);
            return sample != null && sample.test(new TFJobsConfig.Target(raw)) ? null : "un mob de verdad de ese objetivo no cuenta (no encaja)";
        }
        // objetos: pescar, fabricar, fundir, preparar, reparar
        if (tag) {
            if (BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id)).map(s -> s.size() == 0).orElse(true))
                return "la etiqueta de objetos no existe o está vacía";
        } else if (!BuiltInRegistries.ITEM.containsKey(id)) {
            return "el objeto no existe";
        }
        TFJobsConfig.Target target = new TFJobsConfig.Target(raw);
        java.util.function.Predicate<TFJobsConfig.Target> sample = sample(server, type, raw);
        if (sample == null || !sample.test(target)) return "un objeto de verdad de ese objetivo no cuenta (no encaja)";
        if (type.equals("fabricar") && !made(server, target, RecipeType.CRAFTING)) return "no sale de ninguna receta de la mesa de trabajo";
        if (type.equals("fundir") && !made(server, target, RecipeType.SMELTING) && !made(server, target, RecipeType.SMOKING)
                && !made(server, target, RecipeType.BLASTING)) return "no sale de ningún horno (horno, ahumador o alto horno)";
        return null;
    }

    private static <C extends net.minecraft.world.Container, T extends Recipe<C>> boolean made(MinecraftServer server, TFJobsConfig.Target target,
                                                                                             RecipeType<T> recipeType) {
        for (T recipe : server.getRecipeManager().getAllRecipesFor(recipeType)) {
            ItemStack result = recipe.getResultItem(server.registryAccess());
            if (!result.isEmpty() && target.item(result)) return true;
        }
        return false;
    }

    private static boolean itemExists(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl != null && BuiltInRegistries.ITEM.containsKey(rl);
    }

    /**
     * Algo de verdad del objetivo (un bloque, un objeto o un mob) para comprobar que cuenta: lo que el juego le pasaría a
     * TFJobs al hacerlo. null si no hay ninguno (el objetivo no existe o la etiqueta está vacía).
     */
    public static java.util.function.Predicate<TFJobsConfig.Target> sample(MinecraftServer server, String type, String raw) {
        boolean any = raw.equals("*");
        boolean tag = raw.startsWith("#");
        if (type.equals("encantar")) return t -> t.item(ItemStack.EMPTY);
        if (BLOCK_TYPES.contains(type)) {
            net.minecraft.world.level.block.Block block = any ? net.minecraft.world.level.block.Blocks.STONE : null;
            ResourceLocation id = any ? null : ResourceLocation.tryParse(tag ? raw.substring(1) : raw);
            if (id != null && tag) block = BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, id))
                    .flatMap(set -> set.stream().findFirst()).map(net.minecraft.core.Holder::value).orElse(null);
            else if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) block = BuiltInRegistries.BLOCK.get(id);
            if (block == null) return null;
            var state = block.defaultBlockState();
            return t -> t.block(state);
        }
        if (ENTITY_TYPES.contains(type)) {
            net.minecraft.world.entity.EntityType<?> et = any || raw.equals("hostil") ? net.minecraft.world.entity.EntityType.ZOMBIE
                    : raw.equals("animal") ? net.minecraft.world.entity.EntityType.COW : null;
            ResourceLocation id = et != null ? null : ResourceLocation.tryParse(tag ? raw.substring(1) : raw);
            if (id != null && tag) et = BuiltInRegistries.ENTITY_TYPE.getTag(TagKey.create(Registries.ENTITY_TYPE, id))
                    .flatMap(set -> set.stream().findFirst()).map(net.minecraft.core.Holder::value).orElse(null);
            else if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) et = BuiltInRegistries.ENTITY_TYPE.get(id);
            if (et == null) return null;
            net.minecraft.world.entity.Entity entity = et.create(server.overworld());
            if (entity == null) return null;
            entity.discard();
            return t -> t.entity(entity);
        }
        net.minecraft.world.item.Item item = any ? net.minecraft.world.item.Items.STONE : null;
        ResourceLocation id = any ? null : ResourceLocation.tryParse(tag ? raw.substring(1) : raw);
        if (id != null && tag) item = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id))
                .flatMap(set -> set.stream().findFirst()).map(net.minecraft.core.Holder::value).orElse(null);
        else if (id != null && BuiltInRegistries.ITEM.containsKey(id)) item = BuiltInRegistries.ITEM.get(id);
        if (item == null || item == net.minecraft.world.item.Items.AIR) return null;
        ItemStack stack = new ItemStack(item);
        return t -> t.item(stack);
    }

    /**
     * (1.3.143, el dueño: «añade una pequeña descripción de lo que debe hacer en cada misión, para que la gente no se
     * confunda») Cómo se hace, en una línea, según el tipo y el objetivo (vale también para las que se añadan a mano).
     */
    public static String how(String type, String raw) {
        String t = raw == null ? "*" : raw;
        return switch (type) {
            case "romper" -> "Los que pusiste tú no cuentan.";
            case "cosechar" -> switch (t) {
                case "minecraft:sweet_berry_bush", "minecraft:cave_vines", "minecraft:cave_vines_plant" -> "Clic derecho a las bayas.";
                case "minecraft:melon", "minecraft:pumpkin" -> "Los que pusiste tú no cuentan.";
                case "minecraft:sugar_cane", "minecraft:cactus", "minecraft:bamboo" -> "Deja el de abajo para que vuelva a crecer.";
                default -> "Solo maduros.";
            };
            case "colocar" -> "Quitar y volver a poner en el mismo sitio no cuenta.";
            case "matar" -> "Los de spawners no cuentan.";
            case "fabricar" -> "En la mesa de trabajo o el inventario.";
            case "fundir" -> "Saca tú el resultado; con tolva no cuenta.";
            case "preparar" -> "Saca tú las pociones del soporte.";
            case "criar" -> "Dales de comer a dos para que tengan una cría.";
            default -> "";
        };
    }

    private static JsonArray array(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
    }
}
