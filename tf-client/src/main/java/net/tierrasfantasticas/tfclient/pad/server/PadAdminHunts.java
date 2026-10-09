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
import net.tierrasfantasticas.tfclient.market.TFMarket;
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

    /** El campo que se está escribiendo de esa presa, o "". */
    private static String editing(ServerPlayer p, String id) {
        String v = PadServer.get(p, APP_ID + ".edit", "");
        return v.startsWith(id + "|") ? v.substring(id.length() + 1) : "";
    }

    static final class App implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            JsonObject o = PadMissions.readConfig();
            if (tab.startsWith("c:")) {
                JsonObject h = hunt(o, tab.substring(2));
                if (h != null) return detail(player, h);
            }
            PadView.Builder b = PadView.of(APP_ID)
                    .header("Salen 4 cada 12 h y rotan por temporadas de 90 días (suben hasta x2,5). Pulsa una para cambiarla;")
                    .header("para un mob de un mod, escribe su id abajo.");
            for (JsonElement e : hunts(o)) {
                if (!e.isJsonObject()) continue;
                JsonObject h = e.getAsJsonObject();
                String id = TFJson.str(h, "id", "");
                b.row(new PadView.Row(icon(h), TFJson.str(h, "nombre", id), TEXT,
                        List.of(TFJson.num(h, "cantidad", 1) + " × " + TFJson.str(h, "objetivo", "?")), -1,
                        PadView.moneyExact(TFJson.num(h, "monedas", 0)), null, null).clickable("tab:c:" + id));
            }
            b.empty("No hay presas. Escribe el id de un mob abajo y pulsa AÑADIR.");
            b.input("nueva", "ID DEL MOB (MINECRAFT:ZOMBIE, MOD:MOB)", 64, "AÑADIR");
            return b.build();
        }

        private PadView detail(ServerPlayer player, JsonObject h) {
            String id = TFJson.str(h, "id", "");
            String edit = editing(player, id);
            PadView.Builder b = PadView.of(APP_ID).selected("c:" + id);
            b.hero(new PadView.Row(icon(h), TFJson.str(h, "nombre", id), 0xC83C3C,
                    List.of(TFJson.str(h, "descripcion", "").replace("{n}", String.valueOf(TFJson.num(h, "cantidad", 1)))), -1,
                    PadView.moneyExact(TFJson.num(h, "monedas", 0)), null, null));
            b.row(field(h, "nombre", "Nombre", Items.NAME_TAG, TFJson.str(h, "nombre", ""), edit));
            b.row(field(h, "objetivo", "Mob", Items.ZOMBIE_HEAD, TFJson.str(h, "objetivo", "") + "  (id, #etiqueta, hostil o animal)", edit));
            b.row(field(h, "cantidad", "Cantidad de base", Items.ARROW, String.valueOf(TFJson.num(h, "cantidad", 1)), edit));
            b.row(field(h, "monedas", "Monedas de base", Items.GOLD_NUGGET, PadView.moneyExact(TFJson.num(h, "monedas", 0)).substring(1), edit));
            b.row(field(h, "descripcion", "Descripción", Items.WRITABLE_BOOK, TFJson.str(h, "descripcion", "") + "  ({n} = la cantidad)", edit));
            b.row(new PadView.Row(icon(h), "Icono", TEXT, List.of("El objeto que sale en su fila."), -1, "",
                    PadView.Btn.of("MI MANO", "icono:" + id, PadView.GRAY), null));
            b.footer(PadView.Btn.of("ATRÁS", "tab:", PadView.BLUE));
            boolean sure = PadServer.confirming(player, APP_ID + ".borrar:" + id);
            b.footer(PadView.Btn.of(sure ? "¿SEGURO?" : "BORRAR", "borrar:" + id, PadView.RED));
            if (!edit.isEmpty()) b.input("campo:" + id, edit.toUpperCase(Locale.ROOT), 96, "GUARDAR");
            return b.build();
        }

        private static PadView.Row field(JsonObject h, String key, String title, Item icon, String value, String edit) {
            return new PadView.Row(new ItemStack(icon), title, TEXT, List.of(value), -1, "",
                    PadView.Btn.of("EDITAR", "editar:" + TFJson.str(h, "id", "") + ":" + key, PadView.BLUE), null).selected(edit.equals(key));
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            if (action.startsWith("tab:")) return action.substring(4);
            String[] a = action.split(":", 2);
            String arg = a.length > 1 ? a[1] : "";
            JsonObject o = PadMissions.readConfig();
            switch (a[0]) {
                case "nueva" -> {
                    String target = text.trim().toLowerCase(Locale.ROOT);
                    if (!validTarget(target)) {
                        TFPadNet.notice(player, "No existe ese mob: «" + text.trim() + "». Ejemplo: minecraft:zombie o elmod:mimob.");
                        return null;
                    }
                    String path = target.contains(":") ? target.substring(target.indexOf(':') + 1) : target.replace("#", "");
                    String base = "c_" + path.replaceAll("[^a-z0-9]+", "_");
                    String id = base;
                    for (int i = 2; hunt(o, id) != null; i++) id = base + "_" + i;
                    JsonObject h = new JsonObject();
                    h.addProperty("id", id);
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
                    return "c:" + id;
                }
                case "editar" -> { // editar:<id>:<campo>
                    int c = arg.lastIndexOf(':');
                    if (c > 0) PadServer.put(player, APP_ID + ".edit", arg.substring(0, c) + "|" + arg.substring(c + 1));
                }
                case "campo" -> {
                    String field = editing(player, arg);
                    JsonObject h = hunt(o, arg);
                    if (field.isEmpty() || h == null || text.isBlank()) return null;
                    String v = text.trim();
                    switch (field) {
                        case "cantidad", "monedas" -> {
                            long n = v.equals("0") ? 0 : TFMarket.parsePrice(v);
                            if (n < 0 || (field.equals("cantidad") && n < 1)) {
                                TFPadNet.notice(player, "Escribe un número.");
                                return null;
                            }
                            h.addProperty(field, field.equals("cantidad") ? Math.min(100_000, n) : n);
                        }
                        case "objetivo" -> {
                            if (!validTarget(v.toLowerCase(Locale.ROOT))) {
                                TFPadNet.notice(player, "No existe ese mob: «" + v + "».");
                                return null;
                            }
                            h.addProperty("objetivo", v.toLowerCase(Locale.ROOT));
                        }
                        default -> h.addProperty(field, v);
                    }
                    PadMissions.writeConfig(o);
                    PadServer.put(player, APP_ID + ".edit", null);
                    TFPadNet.notice(player, "Guardado.");
                }
                case "icono" -> {
                    ItemStack hand = player.getMainHandItem();
                    JsonObject h = hunt(o, arg);
                    if (hand.isEmpty() || h == null) {
                        TFPadNet.notice(player, "Ponte en la mano el objeto que quieres de icono.");
                        return null;
                    }
                    h.addProperty("icono", String.valueOf(ForgeRegistries.ITEMS.getKey(hand.getItem())));
                    PadMissions.writeConfig(o);
                }
                case "borrar" -> {
                    if (!PadServer.confirm(player, APP_ID + ".borrar:" + arg)) return null;
                    JsonArray keep = new JsonArray();
                    for (JsonElement e : hunts(o)) {
                        if (!(e.isJsonObject() && TFJson.str(e.getAsJsonObject(), "id", "").equals(arg))) keep.add(e);
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
