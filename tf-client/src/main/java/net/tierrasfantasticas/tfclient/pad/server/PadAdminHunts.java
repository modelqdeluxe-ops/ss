package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * CAZAS en el pad de administrador (las «cazas» de config/tfclient/misiones.json): la lista de presas que rotan (4 cada
 * 12 h para todo el servidor, por temporadas de 90 días). Cada presa: nombre, mob (minecraft:zombie, el id de un mob de
 * un mod, una etiqueta #… o hostil/animal), cantidad y monedas de base (a lo largo de la temporada suben hasta x2,5),
 * la descripción ({n} = la cantidad) y el icono. Se crean escribiendo el id del mob; se borran con doble clic.
 */
public final class PadAdminHunts {
    public static final PadServer.App APP = new App();
    private static final String APP_ID = "a_cazas";
    private static final int TEXT = 0x18265C;

    private PadAdminHunts() {}

    private static JsonArray hunts(JsonObject o) {
        if (!o.has("cazas") || !o.get("cazas").isJsonArray()) o.add("cazas", new JsonArray());
        return o.getAsJsonArray("cazas");
    }

    private static JsonObject hunt(JsonObject o, String id) {
        for (JsonElement e : hunts(o)) {
            if (e.isJsonObject() && TFJson.str(e.getAsJsonObject(), "id", "").equals(id)) return e.getAsJsonObject();
        }
        return null;
    }

    private static ItemStack icon(JsonObject h) {
        Item i = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(TFJson.str(h, "icono", "minecraft:iron_sword")));
        return new ItemStack(i == null || i == Items.AIR ? Items.IRON_SWORD : i);
    }

    /** ¿Es un objetivo que se puede cazar? Un mob que exista (también de mods), una etiqueta, hostil o animal. */
    private static boolean validTarget(String t) {
        if (t.equals("hostil") || t.equals("animal") || t.equals("*")) return true;
        if (t.startsWith("#")) return ResourceLocation.tryParse(t.substring(1)) != null;
        ResourceLocation id = ResourceLocation.tryParse(t);
        return id != null && ForgeRegistries.ENTITY_TYPES.containsKey(id);
    }

    /** Los campos de una presa: clave, título, explicación, lo que pide el campo de abajo e icono. */
    private record Field(String key, String title, String help, String hint, Item icon) {}

    private static final List<Field> FIELDS = List.of(
            new Field("nombre", "Nombre", "El que ven los jugadores.", "NOMBRE DE LA CAZA", Items.NAME_TAG),
            new Field("objetivo", "Mob", "Su id (minecraft:zombie, elmod:mob), una #etiqueta, hostil o animal.",
                    "ID DEL MOB, #ETIQUETA, HOSTIL O ANIMAL", Items.ZOMBIE_HEAD),
            new Field("cantidad", "Cantidad de base", "Cuántos hay que cazar (sube con la temporada).", "CANTIDAD (1 A 100.000)", Items.ARROW),
            new Field("monedas", "Monedas de base", "El premio: a lo largo de la temporada sube hasta x2,5.", "MONEDAS DE BASE", Items.GOLD_NUGGET),
            new Field("descripcion", "Descripción", "", "DESCRIPCIÓN ({N} = LA CANTIDAD)", Items.WRITABLE_BOOK));

    private static Field field(String key) {
        for (Field f : FIELDS) if (f.key.equals(key)) return f;
        return null;
    }

    static final class App implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String rawTab) {
            String tab = PadItemPicker.tab(player, APP_ID, rawTab);
            JsonObject o = PadMissions.readConfig();
            if (tab.startsWith("c:") || tab.startsWith("pi:")) {
                String id = tab.substring(tab.indexOf(':') + 1);
                JsonObject h = hunt(o, id);
                if (h != null) {
                    if (tab.startsWith("c:")) return detail(player, h);
                    return PadItemPicker.builder(player, APP_ID, tab, "Icono de " + TFJson.str(h, "nombre", id) + ": pulsa el objeto que "
                            + "saldrá en su fila.", false, TFJson.str(h, "icono", "")).footer(PadView.Btn.of("ATRÁS", "tab:c:" + id, PadView.BLUE)).build();
                }
            }
            JsonArray list = hunts(o);
            PadView.Builder b = PadView.of(APP_ID).selected("")
                    .header("Salen 4 cada 12 h y rotan por temporadas de 90 días (el premio sube hasta x2,5). Pulsa una para cambiarla; "
                            + "para una nueva, escribe el id del mob abajo.");
            for (JsonElement e : list) {
                if (!e.isJsonObject()) continue;
                JsonObject h = e.getAsJsonObject();
                String id = TFJson.str(h, "id", "");
                b.row(new PadView.Row(icon(h), TFJson.str(h, "nombre", id), TEXT,
                        List.of(TFJson.num(h, "cantidad", 1) + " × " + TFJson.str(h, "objetivo", "?")), -1,
                        PadView.moneyExact(TFJson.num(h, "monedas", 0)), PadView.Btn.of("EDITAR", "tab:c:" + id, PadView.BLUE), null)
                        .clickable("tab:c:" + id));
            }
            b.empty("No hay presas. Escribe el id de un mob abajo (minecraft:zombie o el de un mod) y pulsa AÑADIR.");
            b.input("nueva", "ID DEL MOB (MINECRAFT:ZOMBIE, MOD:MOB)", 64, "AÑADIR");
            return b.build();
        }

        private PadView detail(ServerPlayer player, JsonObject h) {
            String id = TFJson.str(h, "id", "");
            String target = "c:" + id;
            String f = PadAdminUi.field(player, APP_ID, target);
            long n = TFJson.num(h, "cantidad", 1), coins = TFJson.num(h, "monedas", 0);
            String desc = TFJson.str(h, "descripcion", "");
            PadView.Builder b = PadView.of(APP_ID).selected(target).compact();
            b.hero(new PadView.Row(icon(h), TFJson.str(h, "nombre", id), 0xC83C3C,
                    List.of(desc.isEmpty() ? n + " × " + TFJson.str(h, "objetivo", "?") : desc.replace("{n}", String.valueOf(n)),
                            "Así la ven los jugadores (con la cantidad de base)."), -1, PadView.moneyExact(coins), null, null));
            for (Field fd : FIELDS) {
                String value = switch (fd.key) {
                    case "cantidad" -> "×" + PadView.number(n);
                    case "monedas" -> PadView.moneyExact(coins);
                    case "descripcion" -> "";
                    default -> TFJson.str(h, fd.key, "");
                };
                String help = fd.key.equals("descripcion") ? (desc.isEmpty() ? "Sin descripción." : "«" + desc + "»") + " {n} = la cantidad." : fd.help;
                b.row(PadAdminUi.value(new ItemStack(fd.icon), fd.title, help, value, "f:" + fd.key, f.equals(fd.key)));
            }
            b.row(PadAdminUi.button(icon(h), "Icono", "El objeto que sale en su fila.", "", PadView.Btn.of("CAMBIAR", "tab:pi:" + id, PadView.BLUE)));
            boolean sure = PadServer.confirming(player, APP_ID + ".borrar:" + id);
            b.row(PadAdminUi.button(new ItemStack(Items.BARRIER), "Borrar la caza", "Deja de salir en la rotación.", "",
                    PadView.Btn.of(sure ? "¿SEGURO?" : "BORRAR", "borrar", PadView.RED)));
            b.footer(PadView.Btn.of("ATRÁS", "tab:", PadView.BLUE));
            Field fd = field(f);
            if (fd != null) {
                b.input("v", fd.hint, 96, "GUARDAR");
                b.footer(PadView.Btn.of("CANCELAR", "f:", PadView.GRAY));
            }
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String rawTab, String action, String text) {
            String tab = PadItemPicker.tab(player, APP_ID, rawTab);
            String id = tab.startsWith("c:") || tab.startsWith("pi:") ? tab.substring(tab.indexOf(':') + 1) : "";
            JsonObject o = PadMissions.readConfig();
            // el selector del icono
            if (tab.startsWith("pi:")) {
                ItemStack chosen = PadItemPicker.action(player, APP_ID, action, text);
                if (chosen != null) {
                    JsonObject h = hunt(o, id);
                    if (chosen.isEmpty() || h == null) return null;
                    h.addProperty("icono", String.valueOf(ForgeRegistries.ITEMS.getKey(chosen.getItem())));
                    PadMissions.writeConfig(o);
                    TFPadNet.notice(player, "Icono: " + chosen.getHoverName().getString() + ".");
                    return "c:" + id;
                }
            }
            String[] a = action.split(":", 2);
            String arg = a.length > 1 ? a[1] : "";
            switch (a[0]) {
                case "nueva" -> {
                    String target = text.trim().toLowerCase(Locale.ROOT);
                    if (!validTarget(target)) {
                        TFPadNet.notice(player, "No existe ese mob: «" + text.trim() + "». Ejemplo: minecraft:zombie o elmod:mimob.");
                        return null;
                    }
                    String path = target.contains(":") ? target.substring(target.indexOf(':') + 1) : target.replace("#", "");
                    String base = "c_" + path.replaceAll("[^a-z0-9]+", "_");
                    String nid = base;
                    for (int i = 2; hunt(o, nid) != null; i++) nid = base + "_" + i;
                    JsonObject h = new JsonObject();
                    h.addProperty("id", nid);
                    h.addProperty("nombre", "Caza de " + path.replace('_', ' '));
                    h.addProperty("descripcion", "Derrota {n} " + path.replace('_', ' '));
                    h.addProperty("tipo", "matar");
                    h.addProperty("objetivo", target);
                    h.addProperty("cantidad", 15);
                    h.addProperty("monedas", 40);
                    h.addProperty("icono", "minecraft:iron_sword");
                    hunts(o).add(h);
                    PadMissions.writeConfig(o);
                    TFPadNet.notice(player, "Presa añadida. Cambia su nombre, cantidad, monedas e icono.");
                    return "c:" + nid;
                }
                case "f" -> PadAdminUi.editField(player, APP_ID, tab, arg.equals(PadAdminUi.field(player, APP_ID, tab)) ? "" : arg);
                case "v" -> {
                    String field = PadAdminUi.field(player, APP_ID, tab);
                    JsonObject h = hunt(o, id);
                    if (field.isEmpty() || h == null || text.isBlank()) return null;
                    String v = text.trim();
                    switch (field) {
                        case "cantidad" -> {
                            long n = PadAdminUi.number(player, v, 1, 100_000);
                            if (n < 0) return null;
                            h.addProperty(field, n);
                        }
                        case "monedas" -> {
                            long n = PadAdminUi.number(player, v, 0, 1_000_000_000L);
                            if (n < 0) return null;
                            h.addProperty(field, n);
                        }
                        case "objetivo" -> {
                            if (!validTarget(v.toLowerCase(Locale.ROOT))) {
                                TFPadNet.notice(player, "No existe ese mob: «" + v + "». Ejemplo: minecraft:zombie o elmod:mimob.");
                                return null;
                            }
                            h.addProperty("objetivo", v.toLowerCase(Locale.ROOT));
                        }
                        default -> h.addProperty(field, v);
                    }
                    PadMissions.writeConfig(o);
                    PadAdminUi.stopEditing(player, APP_ID);
                    TFPadNet.notice(player, "Guardado.");
                }
                case "borrar" -> {
                    if (id.isEmpty() || !PadServer.confirm(player, APP_ID + ".borrar:" + id)) return null;
                    JsonArray keep = new JsonArray();
                    for (JsonElement e : hunts(o)) {
                        if (!(e.isJsonObject() && TFJson.str(e.getAsJsonObject(), "id", "").equals(id))) keep.add(e);
                    }
                    o.add("cazas", keep);
                    PadMissions.writeConfig(o);
                    return "";
                }
                default -> {
                }
            }
            return null;
        }
    }
}
