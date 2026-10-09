package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.market.TFMarket;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * RECOMPENSAS en el pad de administrador (config/tfclient/recompensas.json):
 * <ul>
 *   <li>CALENDARIO: encenderlo o apagarlo, qué pasa si alguien se salta un día, y cada día (monedas y objetos).
 *       Se añaden días al final o se quita el último.</li>
 *   <li>OTRAS: recompensas sueltas (nombre, tipo: diaria, semanal, cada X horas o una vez; monedas, icono, objetos,
 *       activada).</li>
 * </ul>
 * Los objetos se eligen con {@link PadItemPicker}: de tu inventario o de todos los objetos del juego (mods incluidos).
 */
public final class PadAdminRewards {
    public static final PadServer.App APP = new App();
    private static final String APP_ID = "a_recompensas";
    private static final int TEXT = 0x18265C;

    private PadAdminRewards() {}

    private static String editing(ServerPlayer p) {
        return PadServer.get(p, APP_ID + ".edit", "");
    }

    private static void edit(Consumer<JsonObject> f) {
        JsonObject o = PadRewards.readConfig();
        f.accept(o);
        PadRewards.writeConfig(o);
    }

    private static JsonObject calendar(JsonObject o) {
        if (!o.has("calendario") || !o.get("calendario").isJsonObject()) o.add("calendario", new JsonObject());
        JsonObject c = o.getAsJsonObject("calendario");
        if (!c.has("dias") || !c.get("dias").isJsonArray()) c.add("dias", new JsonArray());
        return c;
    }

    private static JsonObject day(JsonObject o, int i) {
        JsonArray days = calendar(o).getAsJsonArray("dias");
        return i >= 0 && i < days.size() && days.get(i).isJsonObject() ? days.get(i).getAsJsonObject() : null;
    }

    private static JsonObject reward(JsonObject o, String id) {
        if (!o.has("recompensas") || !o.get("recompensas").isJsonArray()) o.add("recompensas", new JsonArray());
        for (JsonElement e : o.getAsJsonArray("recompensas")) {
            if (e.isJsonObject() && TFJson.str(e.getAsJsonObject(), "id", "").equals(id)) return e.getAsJsonObject();
        }
        return null;
    }

    private static void addItem(JsonObject target, ItemStack s) {
        if (target == null) return;
        if (!target.has("objetos") || !target.get("objetos").isJsonArray()) target.add("objetos", new JsonArray());
        target.getAsJsonArray("objetos").add(PadItemPicker.json(s));
    }

    private static int dayOf(String tab, int prefix) {
        try {
            return Integer.parseInt(tab.substring(prefix));
        } catch (RuntimeException e) {
            return -1;
        }
    }

    static final class App implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            tab = PadItemPicker.tab(player, APP_ID, tab);
            List<PadRewards.Day> days = PadRewards.days();
            if (tab.startsWith("pd:")) {
                int i = dayOf(tab, 3);
                if (i >= 0 && i < days.size()) return PadItemPicker.view(player, APP_ID, tab, "Día " + (i + 1), "tab:d:" + i);
            }
            if (tab.startsWith("pr:")) {
                PadRewards.Reward r = PadRewards.reward(tab.substring(3));
                if (r != null) return PadItemPicker.view(player, APP_ID, tab, r.name(), "tab:r:" + r.id());
            }
            if (tab.startsWith("d:")) {
                int i = dayOf(tab, 2);
                if (i >= 0 && i < days.size()) return dayView(player, i, days.get(i));
            }
            if (tab.startsWith("r:")) {
                PadRewards.Reward r = PadRewards.reward(tab.substring(2));
                if (r != null) return rewardView(player, r);
            }
            PadView.Builder b = PadView.of(APP_ID).tab("cal", "CALENDARIO").tab("otras", "OTRAS");
            if (tab.equals("otras")) {
                b.selected("otras").header("Recompensas sueltas: pulsa una para cambiarla o crea otra abajo.").cards();
                for (PadRewards.Reward r : PadRewards.rewards()) {
                    b.card(r.iconStack(), r.name(), r.on() ? 0x40C850 : 0xAABAD2, r.on() ? PadRewards.typeText(r) : "Apagada", "tab:r:" + r.id(), false);
                }
                b.empty("No hay. Escribe un nombre abajo y pulsa CREAR.");
                b.input("nueva", "NOMBRE DE LA RECOMPENSA NUEVA", 32, "CREAR");
                return b.build();
            }
            b.selected("cal").header("El calendario diario: cada día se reclama el siguiente de la racha. Pulsa un día para cambiarlo.").cards();
            for (int i = 0; i < days.size(); i++) {
                PadRewards.Day d = days.get(i);
                b.card(d.icon(), "Día " + (i + 1), 0xF6B628, (d.items().size() + " obj.") + (d.coins() > 0 ? " · " + PadShop.price(d.coins()) : ""),
                        "tab:d:" + i, false);
            }
            b.row(new PadView.Row(new ItemStack(Items.CLOCK), "Calendario", TEXT, List.of(PadRewards.calendarOn() ? "Encendido." : "Apagado: no sale en el pad."),
                    -1, "", PadView.Btn.of(PadRewards.calendarOn() ? "SÍ" : "NO", "cal.on", PadRewards.calendarOn() ? PadView.GREEN : PadView.GRAY), null));
            b.row(new PadView.Row(new ItemStack(Items.COMPASS), "Si alguien se salta un día", TEXT,
                    List.of(PadRewards.resetOnMiss() ? "Vuelve al día 1." : "Sigue donde iba."), -1, "",
                    PadView.Btn.of("CAMBIAR", "cal.reset", PadView.BLUE), null));
            boolean sure = PadServer.confirming(player, APP_ID + ".quitardia");
            b.footer(PadView.Btn.of("AÑADIR DÍA", "cal.add", PadView.GREEN));
            if (!days.isEmpty()) b.footer(PadView.Btn.of(sure ? "¿SEGURO?" : "QUITAR EL ÚLTIMO", "cal.remove", PadView.RED));
            return b.build();
        }

        private PadView dayView(ServerPlayer player, int i, PadRewards.Day d) {
            String edit = editing(player);
            PadView.Builder b = PadView.of(APP_ID).selected("d:" + i).header("Día " + (i + 1) + " del calendario · " + d.items().size() + " objetos");
            b.row(new PadView.Row(new ItemStack(Items.GOLD_NUGGET), "Monedas", TEXT, List.of("Además de los objetos."), -1,
                    d.coins() > 0 ? TFEconomy.format(d.coins()) : "—", PadView.Btn.of("EDITAR", "editar:monedas", PadView.BLUE), null)
                    .selected(edit.equals("monedas")));
            itemsRow(b, player, "d:" + i, d.items(), "pd:" + i);
            for (int k = 0; k < d.items().size(); k++) b.cell(d.items().get(k).copy(), "", TEXT, "quitar:d:" + i + ":" + k, false);
            b.footer(PadView.Btn.of("ATRÁS", "tab:cal", PadView.BLUE));
            if (!edit.isEmpty()) b.input("campo:d:" + i, "MONEDAS", 16, "GUARDAR");
            return b.build();
        }

        private PadView rewardView(ServerPlayer player, PadRewards.Reward r) {
            String edit = editing(player);
            PadView.Builder b = PadView.of(APP_ID).selected("r:" + r.id()).header(r.name() + " · " + PadRewards.typeText(r) + " · " + r.items().size() + " objetos");
            b.row(new PadView.Row(new ItemStack(Items.NAME_TAG), "Nombre", TEXT, List.of(r.name()), -1, "",
                    PadView.Btn.of("EDITAR", "editar:nombre", PadView.BLUE), null).selected(edit.equals("nombre")));
            b.row(new PadView.Row(new ItemStack(Items.LEVER), "Activada", TEXT, List.of(r.on() ? "Sale en el pad." : "No sale en el pad."), -1, "",
                    PadView.Btn.of(r.on() ? "SÍ" : "NO", "on:" + r.id(), r.on() ? PadView.GREEN : PadView.GRAY), null));
            b.row(new PadView.Row(new ItemStack(Items.CLOCK), "Cada cuánto", TEXT, List.of(PadRewards.typeText(r)), -1, "",
                    PadView.Btn.of("CAMBIAR", "tipo:" + r.id(), PadView.BLUE), null));
            if (r.type().equals("horas")) {
                b.row(new PadView.Row(new ItemStack(Items.CLOCK), "Horas de espera", TEXT, List.of("Entre una vez y la siguiente."), -1,
                        String.valueOf(r.hours()), PadView.Btn.of("EDITAR", "editar:horas", PadView.BLUE), null).selected(edit.equals("horas")));
            }
            b.row(new PadView.Row(new ItemStack(Items.GOLD_NUGGET), "Monedas", TEXT, List.of("Además de los objetos."), -1,
                    r.coins() > 0 ? TFEconomy.format(r.coins()) : "—", PadView.Btn.of("EDITAR", "editar:monedas", PadView.BLUE), null)
                    .selected(edit.equals("monedas")));
            b.row(new PadView.Row(r.iconStack(), "Icono", TEXT, List.of("El objeto que sale en su tarjeta."), -1, "",
                    PadView.Btn.of("MI MANO", "icono:" + r.id(), PadView.GRAY), null));
            itemsRow(b, player, "r:" + r.id(), r.items(), "pr:" + r.id());
            for (int k = 0; k < r.items().size(); k++) b.cell(r.items().get(k).copy(), "", TEXT, "quitar:r:" + r.id() + ":" + k, false);
            b.footer(PadView.Btn.of("ATRÁS", "tab:otras", PadView.BLUE));
            boolean sure = PadServer.confirming(player, APP_ID + ".borrar:" + r.id());
            b.footer(PadView.Btn.of(sure ? "¿SEGURO?" : "BORRAR", "borrar:" + r.id(), PadView.RED));
            if (!edit.isEmpty()) {
                b.input("campo:r:" + r.id(), edit.equals("nombre") ? "NOMBRE" : edit.equals("horas") ? "HORAS" : "MONEDAS", 32, "GUARDAR");
            }
            return b.build();
        }

        private void itemsRow(PadView.Builder b, ServerPlayer player, String target, List<ItemStack> items, String pickTab) {
            boolean sure = PadServer.confirming(player, APP_ID + ".vaciar:" + target);
            b.row(new PadView.Row(new ItemStack(Items.CHEST), "Objetos", TEXT,
                    List.of("AÑADIR: de tu inventario o de todos los objetos del juego (también mods). Pulsa uno de abajo para quitarlo."), -1,
                    String.valueOf(items.size()), PadView.Btn.of("AÑADIR", "tab:" + pickTab, PadView.GREEN),
                    PadView.Btn.of(sure ? "¿SEGURO?" : "VACIAR", "vaciar:" + target, PadView.RED)));
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            tab = PadItemPicker.tab(player, APP_ID, tab);
            // el selector: lo elegido va al día o a la recompensa
            if (tab.startsWith("pd:") || tab.startsWith("pr:")) {
                ItemStack chosen = PadItemPicker.action(player, APP_ID, action, text);
                if (chosen != null) {
                    if (!chosen.isEmpty()) {
                        String t = tab;
                        edit(o -> addItem(t.startsWith("pd:") ? day(o, dayOf(t, 3)) : reward(o, t.substring(3)), chosen));
                        TFPadNet.notice(player, "Añadido: " + chosen.getCount() + " × " + chosen.getHoverName().getString() + ".");
                    }
                    return null;
                }
            }
            String[] a = action.split(":", 2);
            String arg = a.length > 1 ? a[1] : "";
            switch (a[0]) {
                case "cal.on" -> edit(o -> calendar(o).addProperty("activado", !TFJson.bool(calendar(o), "activado", true)));
                case "cal.reset" -> edit(o -> calendar(o).addProperty("reiniciarSiFalta", !TFJson.bool(calendar(o), "reiniciarSiFalta", true)));
                case "cal.add" -> {
                    edit(o -> {
                        JsonObject d = new JsonObject();
                        d.addProperty("monedas", 0);
                        d.add("objetos", new JsonArray());
                        calendar(o).getAsJsonArray("dias").add(d);
                    });
                    return "d:" + (PadRewards.days().size() - 1);
                }
                case "cal.remove" -> {
                    if (!PadServer.confirm(player, APP_ID + ".quitardia")) return null;
                    edit(o -> {
                        JsonArray days = calendar(o).getAsJsonArray("dias");
                        if (!days.isEmpty()) days.remove(days.size() - 1);
                    });
                }
                case "nueva" -> {
                    String name = text.trim();
                    if (name.isEmpty()) return null;
                    String base = name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
                    if (base.isEmpty()) base = "recompensa";
                    if (base.length() > 24) base = base.substring(0, 24);
                    String id = base;
                    for (int i = 2; PadRewards.reward(id) != null; i++) id = base + "_" + i;
                    String fid = id;
                    edit(o -> {
                        if (!o.has("recompensas") || !o.get("recompensas").isJsonArray()) o.add("recompensas", new JsonArray());
                        JsonObject r = new JsonObject();
                        r.addProperty("id", fid);
                        r.addProperty("nombre", name);
                        r.addProperty("icono", "");
                        r.addProperty("tipo", "diaria");
                        r.addProperty("horas", 24);
                        r.addProperty("monedas", 0);
                        r.addProperty("activada", true);
                        r.add("objetos", new JsonArray());
                        o.getAsJsonArray("recompensas").add(r);
                    });
                    TFPadNet.notice(player, "Recompensa creada (diaria). Añade objetos o monedas.");
                    return "r:" + fid;
                }
                case "on" -> edit(o -> {
                    JsonObject r = reward(o, arg);
                    if (r != null) r.addProperty("activada", !TFJson.bool(r, "activada", true));
                });
                case "tipo" -> edit(o -> {
                    JsonObject r = reward(o, arg);
                    if (r == null) return;
                    List<String> types = PadRewards.TYPES;
                    int at = types.indexOf(TFJson.str(r, "tipo", "diaria"));
                    r.addProperty("tipo", types.get((at + 1) % types.size()));
                });
                case "icono" -> {
                    ItemStack h = player.getMainHandItem();
                    if (h.isEmpty()) {
                        TFPadNet.notice(player, "Ponte en la mano el objeto que quieres de icono.");
                        return null;
                    }
                    edit(o -> {
                        JsonObject r = reward(o, arg);
                        if (r != null) r.addProperty("icono", String.valueOf(ForgeRegistries.ITEMS.getKey(h.getItem())));
                    });
                }
                case "borrar" -> {
                    if (!PadServer.confirm(player, APP_ID + ".borrar:" + arg)) return null;
                    edit(o -> {
                        JsonArray keep = new JsonArray();
                        for (JsonElement e : o.getAsJsonArray("recompensas")) {
                            if (!(e.isJsonObject() && TFJson.str(e.getAsJsonObject(), "id", "").equals(arg))) keep.add(e);
                        }
                        o.add("recompensas", keep);
                    });
                    return "otras";
                }
                case "vaciar" -> {
                    if (!PadServer.confirm(player, APP_ID + ".vaciar:" + arg)) return null;
                    edit(o -> {
                        JsonObject t = arg.startsWith("d:") ? day(o, dayOf(arg, 2)) : reward(o, arg.substring(2));
                        if (t != null) t.add("objetos", new JsonArray());
                    });
                }
                case "quitar" -> { // quitar:d:<i>:<k> o quitar:r:<id>:<k>
                    int colon = arg.lastIndexOf(':');
                    if (colon < 0) return null;
                    String target = arg.substring(0, colon);
                    int k;
                    try {
                        k = Integer.parseInt(arg.substring(colon + 1));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                    edit(o -> {
                        JsonObject t = target.startsWith("d:") ? day(o, dayOf(target, 2)) : reward(o, target.substring(2));
                        if (t != null) PadAdmin.removeItem(t, k);
                    });
                }
                case "editar" -> PadServer.put(player, APP_ID + ".edit", arg);
                case "campo" -> { // campo:d:<i> o campo:r:<id>
                    String field = editing(player);
                    if (field.isEmpty()) return null;
                    if (field.equals("nombre")) {
                        if (text.isBlank()) return null;
                        edit(o -> {
                            JsonObject r = reward(o, arg.substring(2));
                            if (r != null) r.addProperty("nombre", text.trim());
                        });
                    } else {
                        long v = text.trim().equals("0") ? 0 : TFMarket.parsePrice(text.trim());
                        if (v < 0) {
                            TFPadNet.notice(player, "Escribe un número.");
                            return null;
                        }
                        edit(o -> {
                            JsonObject t = arg.startsWith("d:") ? day(o, dayOf(arg, 2)) : reward(o, arg.substring(2));
                            if (t == null) return;
                            t.addProperty(field.equals("horas") ? "horas" : "monedas", field.equals("horas") ? Math.max(1, v) : v);
                        });
                    }
                    PadServer.put(player, APP_ID + ".edit", null);
                    TFPadNet.notice(player, "Guardado.");
                }
                default -> {
                }
            }
            return null;
        }
    }
}
