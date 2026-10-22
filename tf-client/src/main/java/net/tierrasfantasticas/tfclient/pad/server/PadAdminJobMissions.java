package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.tierrasfantasticas.tfclient.jobs.TFJobsConfig;
import net.tierrasfantasticas.tfclient.jobs.TFJobsMenu;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Las misiones y los pagos de cada oficio en el pad de administrador (1.3.114, el dueño: «no veo configuración de las
 * misiones del jobs»). La app a_oficios abre aquí cada oficio (pestaña «j:id»): lo que paga cada acción (monedas y XP) y
 * sus misiones; cada misión (pestaña «m:id:n») se cambia entera: nombre, descripción, icono, tipo, objetivo, cantidad,
 * nivel, cada cuánto se repite y la recompensa. Todo se guarda en config/tfclient/oficios.json y se recarga al momento.
 */
final class PadAdminJobMissions {
    static final String APP_ID = "a_oficios";
    private static final int TEXT = 0x18265C;
    private static final List<String> REPEAT = List.of("diaria", "siempre", "nunca");

    private PadAdminJobMissions() {}

    static boolean handles(String tab) {
        return tab.startsWith("j:") || tab.startsWith("m:");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // oficios.json
    // ---------------------------------------------------------------------------------------------------------------

    private static JsonObject job(JsonObject root, String id) {
        if (root == null || !root.has("oficios") || !root.get("oficios").isJsonArray()) return null;
        for (JsonElement e : root.getAsJsonArray("oficios")) {
            if (e.isJsonObject() && TFJson.str(e.getAsJsonObject(), "id", "").equalsIgnoreCase(id)) return e.getAsJsonObject();
        }
        return null;
    }

    private static JsonArray array(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonArray()) o.add(key, new JsonArray());
        return o.getAsJsonArray(key);
    }

    private static JsonObject at(JsonArray a, int i) {
        return i >= 0 && i < a.size() && a.get(i).isJsonObject() ? a.get(i).getAsJsonObject() : null;
    }

    private static JsonObject reward(JsonObject m) {
        if (!m.has("recompensa") || !m.get("recompensa").isJsonObject()) m.add("recompensa", new JsonObject());
        return m.getAsJsonObject("recompensa");
    }

    /** Guarda oficios.json y recarga los oficios (con los avisos, si los hay). */
    private static void save(ServerPlayer player, JsonObject root) {
        TFJson.write(TFJobsConfig.file(), root);
        List<String> w = TFJobsConfig.load();
        if (!w.isEmpty()) TFPadNet.notice(player, "Guardado con " + w.size() + " avisos: " + w.get(0));
    }

    /** «0,6» o «0.6» o «5». NaN si no es un número. */
    private static double decimal(String text) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static String dec(double v) {
        return v == Math.rint(v) ? PadView.number((long) v) : String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "");
    }

    private static String repeatText(String r) {
        return switch (r) {
            case "siempre" -> "se repite al cobrarla";
            case "nunca" -> "una sola vez";
            default -> "diaria (rota)";
        };
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Vistas
    // ---------------------------------------------------------------------------------------------------------------

    static PadView view(ServerPlayer player, String tab) {
        JsonObject root = TFJson.read(TFJobsConfig.file());
        String[] t = tab.split(":");
        JsonObject job = t.length > 1 ? job(root, t[1]) : null;
        if (job == null) {
            return PadView.of(APP_ID).header("Ese oficio ya no está en config/tfclient/oficios.json.")
                    .footer(PadView.Btn.of("ATRÁS", "tab:", PadView.BLUE)).build();
        }
        if (t[0].equals("m") && t.length > 2) {
            int i = PadAdminUi.parse(t[2]);
            JsonObject m = at(array(job, "misiones"), i);
            if (m != null) return missionView(player, tab, job, m, i);
        }
        return jobView(player, tab, job);
    }

    private static PadView jobView(ServerPlayer player, String tab, JsonObject job) {
        String id = TFJson.str(job, "id", "");
        String f = PadAdminUi.field(player, APP_ID, tab);
        PadView.Builder b = PadView.of(APP_ID).selected(tab).compact();
        TFJobsConfig.Job loaded = TFJobsConfig.jobs.get(id);
        ItemStack icon = loaded != null ? new ItemStack(TFJobsMenu.icon(loaded)) : new ItemStack(Items.IRON_PICKAXE);
        b.hero(new PadView.Row(icon, TFJson.str(job, "nombre", id), 0x2EAF5C,
                List.of(TFJson.str(job, "descripcion", ""), "Lo que paga y sus misiones. Se guarda en oficios.json al momento."), -1, "", null, null));
        b.row(PadAdminUi.section("LO QUE PAGA (POR CADA UNO)"));
        JsonArray actions = array(job, "acciones");
        for (int i = 0; i < actions.size(); i++) {
            JsonObject a = at(actions, i);
            if (a == null) continue;
            String target = TFJson.str(a, "objetivo", "*");
            String value = dec(TFJson.dec(a, "monedas", 0)) + " FC · " + dec(TFJson.dec(a, "xp", 0)) + " XP";
            boolean editing = f.equals("am" + i) || f.equals("ax" + i);
            b.row(new PadView.Row(new ItemStack(TFJobsMenu.item(target.startsWith("#") ? "" : target, Items.PAPER)),
                    TFJson.str(a, "tipo", "?") + " " + target, TEXT,
                    List.of(editing ? (f.startsWith("am") ? "Escribe abajo las Fantastic Coins." : "Escribe abajo la XP del oficio.")
                            : "Pulsa MONEDAS o XP para cambiarlo."), -1, value,
                    PadView.Btn.of("MONEDAS", "f:am" + i, f.equals("am" + i) ? PadView.GOLD : PadView.BLUE),
                    PadView.Btn.of("XP", "f:ax" + i, f.equals("ax" + i) ? PadView.GOLD : PadView.BLUE)).selected(editing));
        }
        if (actions.isEmpty()) b.row(PadAdminUi.note("Este oficio no paga por nada: añade acciones en oficios.json."));
        JsonArray missions = array(job, "misiones");
        b.row(PadAdminUi.section("MISIONES (" + missions.size() + ")"));
        for (int i = 0; i < missions.size(); i++) {
            JsonObject m = at(missions, i);
            if (m == null) continue;
            JsonObject r = reward(m);
            String line = TFJson.str(m, "tipo", "?") + " " + PadView.number(TFJson.num(m, "cantidad", 1)) + " × " + TFJson.str(m, "objetivo", "*")
                    + " · nivel " + TFJson.num(m, "nivel", 1) + " · " + repeatText(TFJson.str(m, "repetir", "diaria"));
            String prize = dec(TFJson.dec(r, "monedas", 0)) + " FC · " + dec(TFJson.dec(r, "xp", 0)) + " XP";
            String go = "tab:m:" + id + ":" + i;
            b.row(new PadView.Row(new ItemStack(TFJobsMenu.item(TFJson.str(m, "icono", "minecraft:paper"), Items.PAPER)),
                    TFJson.str(m, "nombre", TFJson.str(m, "id", "?")), TEXT, List.of(line), -1, prize,
                    PadView.Btn.of("EDITAR", go, PadView.BLUE), null).clickable(go));
        }
        b.row(PadAdminUi.button(new ItemStack(Items.WRITABLE_BOOK), "Nueva misión", "Sale con valores de ejemplo: luego cámbialos.", "",
                PadView.Btn.of("AÑADIR", "nueva", PadView.GREEN)));
        if (f.startsWith("am") || f.startsWith("ax")) {
            b.input("v", f.startsWith("am") ? "FANTASTIC COINS (0,05 O 2)" : "XP DEL OFICIO", 12, "GUARDAR");
            b.footer(PadView.Btn.of("CANCELAR", "f:", PadView.GRAY));
        } else {
            b.footer(PadView.Btn.of("ATRÁS", "tab:", PadView.BLUE));
        }
        return b.build();
    }

    private record Field(String key, String title, String help, String hint, ItemStack icon) {}

    private static final List<Field> FIELDS = List.of(
            new Field("nombre", "Nombre", "El que ve el jugador.", "NOMBRE", new ItemStack(Items.NAME_TAG)),
            new Field("descripcion", "Descripción", "Una línea (vacía: se explica sola).", "DESCRIPCIÓN", new ItemStack(Items.PAPER)),
            new Field("icono", "Icono", "Un objeto: minecraft:wheat.", "ID DEL OBJETO", new ItemStack(Items.ITEM_FRAME)),
            new Field("objetivo", "Objetivo", "Un id (minecraft:wheat), una etiqueta (#minecraft:logs), * o, al matar, hostil / animal.",
                    "OBJETIVO", new ItemStack(Items.TARGET)),
            new Field("cantidad", "Cantidad", "Cuántos hacen falta (las diarias suben solas hasta x2,5 en la temporada).", "CANTIDAD",
                    new ItemStack(Items.HOPPER)),
            new Field("nivel", "Nivel del oficio", "Desde qué nivel le puede tocar.", "NIVEL (1 A 50)", new ItemStack(Items.EXPERIENCE_BOTTLE)),
            new Field("recompensa.monedas", "Fantastic Coins", "Las que cobra al cumplirla.", "FANTASTIC COINS", PadKits.coinIcon(false)),
            new Field("recompensa.xp", "XP del oficio", "La que da para subir de nivel el oficio.", "XP", new ItemStack(Items.LAPIS_LAZULI)));

    private static Field field(String key) {
        for (Field fd : FIELDS) if (fd.key.equals(key)) return fd;
        return null;
    }

    private static PadView missionView(ServerPlayer player, String tab, JsonObject job, JsonObject m, int i) {
        String id = TFJson.str(job, "id", "");
        String f = PadAdminUi.field(player, APP_ID, tab);
        PadView.Builder b = PadView.of(APP_ID).selected(tab).compact();
        b.hero(new PadView.Row(new ItemStack(TFJobsMenu.item(TFJson.str(m, "icono", "minecraft:paper"), Items.PAPER)),
                TFJson.str(m, "nombre", TFJson.str(m, "id", "?")), 0x2EAF5C,
                List.of(TFJson.str(job, "nombre", id) + " · misión «" + TFJson.str(m, "id", "?") + "»"), -1, "", null, null));
        String type = TFJson.str(m, "tipo", "romper");
        b.row(PadAdminUi.cycle(new ItemStack(Items.IRON_PICKAXE), "Tipo", "Qué hay que hacer.", type, "tipo:-", "tipo:+"));
        String rep = TFJson.str(m, "repetir", "diaria");
        b.row(PadAdminUi.cycle(new ItemStack(Items.CLOCK), "Se repite", "Diaria: rota cada día entre las de su nivel.", repeatText(rep),
                "rep:-", "rep:+"));
        for (Field fd : FIELDS) {
            JsonObject p = fd.key.startsWith("recompensa.") ? reward(m) : m;
            String k = fd.key.substring(fd.key.indexOf('.') + 1);
            String value = switch (k) {
                case "cantidad", "nivel" -> PadView.number(TFJson.num(p, k, 1));
                case "monedas", "xp" -> dec(TFJson.dec(p, k, 0));
                default -> TFJson.str(p, k, "");
            };
            b.row(PadAdminUi.value(fd.icon, fd.title, fd.help, value, "f:" + fd.key, f.equals(fd.key)));
        }
        boolean sure = PadServer.confirming(player, APP_ID + ".borrar:" + id + ":" + i);
        b.row(PadAdminUi.button(new ItemStack(Items.BARRIER), "Borrar la misión", sure ? "Pulsa otra vez para borrarla." : "", "",
                PadView.Btn.of(sure ? "¿SEGURO?" : "BORRAR", "borrar", PadView.RED)));
        if (!f.isEmpty()) {
            Field fd = field(f);
            b.input("v", fd != null ? fd.hint : "VALOR", 96, "GUARDAR");
            b.footer(PadView.Btn.of("CANCELAR", "f:", PadView.GRAY));
        } else {
            b.footer(PadView.Btn.of("ATRÁS", "tab:j:" + id, PadView.BLUE));
        }
        return b.build();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Acciones
    // ---------------------------------------------------------------------------------------------------------------

    static String action(ServerPlayer player, String tab, String action, String text) {
        JsonObject root = TFJson.read(TFJobsConfig.file());
        String[] t = tab.split(":");
        JsonObject job = t.length > 1 ? job(root, t[1]) : null;
        if (job == null) {
            TFPadNet.notice(player, "No se pudo leer config/tfclient/oficios.json (o ya no tiene ese oficio).");
            return "";
        }
        String id = TFJson.str(job, "id", "");
        int mi = t[0].equals("m") && t.length > 2 ? PadAdminUi.parse(t[2]) : -1;
        JsonArray missions = array(job, "misiones");
        JsonObject m = mi >= 0 ? at(missions, mi) : null;
        String[] a = action.split(":", 2);
        String arg = a.length > 1 ? a[1] : "";
        switch (a[0]) {
            case "f" -> PadAdminUi.editField(player, APP_ID, tab, arg.equals(PadAdminUi.field(player, APP_ID, tab)) ? "" : arg);
            case "v" -> {
                String f = PadAdminUi.field(player, APP_ID, tab);
                if (f.isEmpty()) return null;
                String v = text.trim();
                if (m == null && (f.startsWith("am") || f.startsWith("ax"))) {
                    JsonObject act = at(array(job, "acciones"), PadAdminUi.parse(f.substring(2)));
                    double d = decimal(v);
                    if (act == null || Double.isNaN(d) || d < 0 || d > 1_000_000) {
                        TFPadNet.notice(player, "Escribe un número (puede llevar decimales: 0,05).");
                        return null;
                    }
                    act.addProperty(f.startsWith("am") ? "monedas" : "xp", d);
                } else if (m != null) {
                    Field fd = field(f);
                    if (fd == null) return null;
                    JsonObject p = f.startsWith("recompensa.") ? reward(m) : m;
                    String k = f.substring(f.indexOf('.') + 1);
                    switch (k) {
                        case "cantidad", "nivel" -> {
                            long c = PadAdminUi.number(player, v, 1, k.equals("nivel") ? Math.max(1, TFJobsConfig.maxLevel) : 1_000_000);
                            if (c < 0) return null;
                            p.addProperty(k, c);
                        }
                        case "monedas", "xp" -> {
                            double d = decimal(v);
                            if (Double.isNaN(d) || d < 0 || d > 100_000_000) {
                                TFPadNet.notice(player, "Escribe un número.");
                                return null;
                            }
                            p.addProperty(k, d);
                        }
                        case "icono" -> {
                            String item = v.contains(":") ? v.toLowerCase(Locale.ROOT) : "minecraft:" + v.toLowerCase(Locale.ROOT);
                            if (TFJobsMenu.item(item, null) == null) {
                                TFPadNet.notice(player, "No existe el objeto «" + v + "».");
                                return null;
                            }
                            p.addProperty(k, item);
                        }
                        case "nombre" -> {
                            if (v.isEmpty()) return null;
                            p.addProperty(k, v);
                        }
                        default -> p.addProperty(k, v.isEmpty() && k.equals("objetivo") ? "*" : v);
                    }
                } else {
                    return null;
                }
                save(player, root);
                PadAdminUi.stopEditing(player, APP_ID);
                TFPadNet.notice(player, "Guardado.");
            }
            case "tipo" -> {
                if (m == null) return null;
                List<String> types = TFJobsConfig.TYPES;
                int at = Math.max(0, types.indexOf(TFJson.str(m, "tipo", types.get(0))));
                m.addProperty("tipo", types.get(Math.floorMod(at + (arg.equals("-") ? -1 : 1), types.size())));
                save(player, root);
            }
            case "rep" -> {
                if (m == null) return null;
                int at = Math.max(0, REPEAT.indexOf(TFJson.str(m, "repetir", "diaria")));
                m.addProperty("repetir", REPEAT.get(Math.floorMod(at + (arg.equals("-") ? -1 : 1), REPEAT.size())));
                save(player, root);
            }
            case "borrar" -> {
                if (m == null || !PadServer.confirm(player, APP_ID + ".borrar:" + id + ":" + mi)) return null;
                missions.remove(mi);
                save(player, root);
                TFPadNet.notice(player, "Misión borrada.");
                return "j:" + id;
            }
            case "nueva" -> {
                java.util.Set<String> used = new java.util.HashSet<>();
                for (JsonElement e : missions) if (e.isJsonObject()) used.add(TFJson.str(e.getAsJsonObject(), "id", ""));
                String mid = "mision";
                for (int n = 1; used.contains(mid); n++) mid = "mision_" + n;
                JsonObject nm = new JsonObject();
                nm.addProperty("id", mid);
                nm.addProperty("nombre", "Misión nueva");
                nm.addProperty("icono", "minecraft:paper");
                JsonArray acts = array(job, "acciones");
                JsonObject first = at(acts, 0);
                nm.addProperty("tipo", first != null ? TFJson.str(first, "tipo", "romper") : "romper");
                nm.addProperty("objetivo", first != null ? TFJson.str(first, "objetivo", "*") : "*");
                nm.addProperty("cantidad", 64);
                nm.addProperty("nivel", 1);
                nm.addProperty("repetir", "diaria");
                JsonObject r = new JsonObject();
                r.addProperty("monedas", 20);
                r.addProperty("xp", 60);
                nm.add("recompensa", r);
                missions.add(nm);
                save(player, root);
                return "m:" + id + ":" + (missions.size() - 1);
            }
            default -> {
            }
        }
        return null;
    }
}
