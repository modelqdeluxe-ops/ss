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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * RECOMPENSAS en el pad de administrador (config/tfclient/recompensas.json). 1.3.66: con las piezas de Kits
 * ({@link PadAdminUi}), así se maneja igual que el resto.
 * <ul>
 *   <li>CALENDARIO: los días en tarjetas (pulsa uno: «d:&lt;i&gt;», con sus objetos, monedas y monedas verdes), AÑADIR
 *       DÍA, QUITAR EL ÚLTIMO y los interruptores del calendario (encendido y si se reinicia al saltarse un día).</li>
 *   <li>OTRAS: recompensas sueltas en tarjetas. Su ficha («r:&lt;id&gt;»): activada, nombre, cada cuánto («rt:&lt;id&gt;»:
 *       cada día, cada semana, cada X horas o una vez), lo que da («rc:&lt;id&gt;»), icono (selector «pi:&lt;id&gt;») y
 *       BORRAR.</li>
 * </ul>
 * Los objetos se eligen con {@link PadItemPicker} (de tu inventario o de todos los del juego, mods incluidos;
 * «pd:&lt;i&gt;» y «pr:&lt;id&gt;»); las monedas que se elijan como objeto se suman a las monedas, nunca van como objeto.
 */
public final class PadAdminRewards {
    public static final PadServer.App APP = new App();
    private static final String APP_ID = "a_recompensas";
    private static final int GOLD = 0xF6B628, GREEN = 0x40C850, OFF = 0x8C96AA;

    private PadAdminRewards() {}

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

    private static int dayOf(String tab, int prefix) {
        try {
            return Integer.parseInt(tab.substring(prefix));
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** «3 objetos · +25 monedas» o «Nada todavía». */
    private static String contents(List<ItemStack> items, long coins, long greens) {
        List<String> parts = new ArrayList<>();
        if (!items.isEmpty()) parts.add(items.size() + (items.size() == 1 ? " objeto" : " objetos"));
        String c = PadKits.coinsText(coins, greens);
        if (!c.isEmpty()) parts.add(c);
        return parts.isEmpty() ? "Nada todavía." : String.join(" · ", parts);
    }

    /** La etiqueta de una tarjeta: las monedas, las verdes o cuántos objetos (gris si no da nada). */
    private static String chip(List<ItemStack> items, long coins, long greens) {
        if (coins > 0) return PadView.money(coins);
        if (greens > 0) return "+" + PadView.number(greens) + " VERDES";
        if (!items.isEmpty()) return items.size() + (items.size() == 1 ? " OBJETO" : " OBJETOS");
        return "VACÍO";
    }

    private static boolean empty(List<ItemStack> items, long coins, long greens) {
        return items.isEmpty() && coins <= 0 && greens <= 0;
    }

    static final class App implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String rawTab) {
            String tab = PadItemPicker.tab(player, APP_ID, rawTab);
            List<PadRewards.Day> days = PadRewards.days();
            if (tab.startsWith("d:") || tab.startsWith("pd:")) {
                int i = dayOf(tab, tab.indexOf(':') + 1);
                if (i >= 0 && i < days.size()) {
                    if (tab.startsWith("d:")) return dayView(player, i, days.get(i));
                    return PadItemPicker.builder(player, APP_ID, tab, "Añadir al día " + (i + 1) + ": cada clic añade ese objeto (el de tu "
                            + "inventario, tal cual).", true, "").footer(PadView.Btn.of("LISTO", "tab:d:" + i, PadView.GREEN)).build();
                }
            }
            int colon = tab.indexOf(':');
            PadRewards.Reward r = colon > 0 ? PadRewards.reward(tab.substring(colon + 1)) : null;
            if (r != null) {
                switch (tab.substring(0, colon)) {
                    case "r" -> {
                        return rewardView(player, r);
                    }
                    case "rt" -> {
                        return typeView(player, r);
                    }
                    case "rc" -> {
                        return contentsView(player, r);
                    }
                    case "pr" -> {
                        return PadItemPicker.builder(player, APP_ID, tab, "Añadir a " + r.name() + ": cada clic añade ese objeto (el de tu "
                                + "inventario, tal cual).", true, "").footer(PadView.Btn.of("LISTO", "tab:rc:" + r.id(), PadView.GREEN)).build();
                    }
                    case "pi" -> {
                        return PadItemPicker.builder(player, APP_ID, tab, "Icono de " + r.name() + ": pulsa el objeto que se verá en su tarjeta.",
                                false, r.icon()).footer(PadView.Btn.of("ATRÁS", "tab:r:" + r.id(), PadView.BLUE)).build();
                    }
                    default -> {
                    }
                }
            }
            PadView.Builder b = PadView.of(APP_ID).tab("cal", "CALENDARIO").tab("otras", "OTRAS");
            if (tab.equals("otras")) {
                b.selected("otras").header("Recompensas sueltas: pulsa una para cambiarla o crea otra abajo.").cards();
                for (PadRewards.Reward x : PadRewards.rewards()) {
                    b.cell(new PadView.Cell(x.iconStack(), x.name(), x.on() ? GREEN : OFF, "tab:r:" + x.id(), false,
                            x.on() ? PadRewards.typeText(x).toUpperCase(java.util.Locale.ROOT) : "APAGADA", -1, "",
                            x.on() ? PadView.TONE_BLUE : PadView.TONE_GRAY));
                }
                b.empty("Aún no hay recompensas sueltas. Escribe un nombre abajo y pulsa CREAR.");
                b.input("nueva", "NOMBRE DE LA RECOMPENSA NUEVA", 32, "CREAR");
                return b.build();
            }
            b.selected("cal").header("Cada día se reclama el siguiente de la racha. Pulsa un día.").cards();
            for (int i = 0; i < days.size(); i++) {
                PadRewards.Day d = days.get(i);
                boolean nothing = empty(d.items(), d.coins(), d.greens());
                b.cell(new PadView.Cell(d.icon(), "Día " + (i + 1), nothing ? OFF : GOLD, "tab:d:" + i, false, chip(d.items(), d.coins(), d.greens()),
                        -1, "", nothing ? PadView.TONE_GRAY : 0));
            }
            if (days.isEmpty()) b.row(PadAdminUi.note("El calendario no tiene días: pulsa AÑADIR DÍA."));
            b.row(PadAdminUi.section("El calendario"));
            b.row(PadAdminUi.toggle(new ItemStack(Items.CLOCK), "Calendario", "", PadRewards.calendarOn(),
                    "Sale en Recompensas: cada día se reclama el siguiente.", "Apagado: no sale en el pad.", "cal.on"));
            b.row(PadAdminUi.toggle(new ItemStack(Items.COMPASS), "Reiniciar si se salta un día", "", PadRewards.resetOnMiss(),
                    "Vuelve al día 1: la racha se pierde.", "Sigue donde iba aunque falte días.", "cal.reset"));
            boolean sure = PadServer.confirming(player, APP_ID + ".quitardia");
            b.footer(PadView.Btn.of("AÑADIR DÍA", "cal.add", PadView.GREEN));
            if (!days.isEmpty()) b.footer(PadView.Btn.of(sure ? "¿SEGURO?" : "QUITAR EL ÚLTIMO", "cal.remove", PadView.RED));
            return b.build();
        }

        /** Un día: su ficha arriba y lo que da (objetos, monedas y monedas verdes). */
        private PadView dayView(ServerPlayer player, int i, PadRewards.Day d) {
            String target = "d:" + i;
            PadView.Builder b = PadView.of(APP_ID).selected(target).compact();
            b.hero(new PadView.Row(d.icon(), "Día " + (i + 1), GOLD, List.of(contents(d.items(), d.coins(), d.greens()),
                    "El día " + (i + 1) + " de la racha de cada jugador."), -1, "", null, null));
            PadAdminUi.items(b, player, APP_ID, target, d.items());
            PadAdminUi.coins(b, player, APP_ID, target, d.coins(), d.greens());
            PadAdminUi.input(b, player, APP_ID, target);
            b.footer(PadView.Btn.of("ATRÁS", "tab:cal", PadView.BLUE));
            b.footer(PadView.Btn.of("AÑADIR OBJETOS", "tab:pd:" + i, PadView.GREEN));
            b.footer(PadView.Btn.of("MI INVENTARIO", "c.inv", PadView.GOLD));
            return b.build();
        }

        /** La ficha de una recompensa suelta: cómo es arriba y cada parte con CAMBIAR. */
        private PadView rewardView(ServerPlayer player, PadRewards.Reward r) {
            String target = "r:" + r.id();
            String f = PadAdminUi.field(player, APP_ID, target);
            PadView.Builder b = PadView.of(APP_ID).selected(target).compact();
            b.hero(new PadView.Row(r.iconStack(), r.name(), r.on() ? GREEN : OFF, List.of(PadRewards.typeText(r) + " · "
                    + contents(r.items(), r.coins(), r.greens())), -1, r.on() ? "ACTIVA" : "APAGADA", null, null));
            b.row(PadAdminUi.toggle(new ItemStack(r.on() ? Items.LIME_DYE : Items.GRAY_DYE), "Activada", "", r.on(),
                    "Sale en la app Recompensas de todos.", "Apagada: nadie la ve.", "on"));
            b.row(PadAdminUi.value(new ItemStack(Items.NAME_TAG), "Nombre", "El que ven los jugadores.", r.name(), "f:nombre", f.equals("nombre")));
            b.row(PadAdminUi.button(new ItemStack(Items.CLOCK), "Cada cuánto", PadRewards.typeText(r) + ".", "",
                    PadView.Btn.of("CAMBIAR", "tab:rt:" + r.id(), PadView.BLUE)));
            b.row(PadAdminUi.button(new ItemStack(Items.CHEST), "Lo que da", contents(r.items(), r.coins(), r.greens()), "",
                    PadView.Btn.of("CAMBIAR", "tab:rc:" + r.id(), PadView.BLUE)));
            b.row(PadAdminUi.button(r.iconStack(), "Icono", "El objeto de su tarjeta.", "", PadView.Btn.of("CAMBIAR", "tab:pi:" + r.id(), PadView.BLUE)));
            boolean sure = PadServer.confirming(player, APP_ID + ".borrar:" + r.id());
            b.row(PadAdminUi.button(new ItemStack(Items.BARRIER), "Borrar la recompensa", "Deja de salir en Recompensas.", "",
                    PadView.Btn.of(sure ? "¿SEGURO?" : "BORRAR", "borrar", PadView.RED)));
            b.footer(PadView.Btn.of("ATRÁS", "tab:otras", PadView.BLUE));
            if (f.equals("nombre")) {
                b.input("v", "NOMBRE DE LA RECOMPENSA", 32, "GUARDAR");
                b.footer(PadView.Btn.of("CANCELAR", "f:", PadView.GRAY));
            }
            if (empty(r.items(), r.coins(), r.greens())) b.row(PadAdminUi.note("Ojo: aún no da nada. Pulsa CAMBIAR en «Lo que da»."));
            return b.build();
        }

        /** Cada cuánto se puede reclamar: una de cuatro. */
        private PadView typeView(ServerPlayer player, PadRewards.Reward r) {
            String target = "r:" + r.id();
            String f = PadAdminUi.field(player, APP_ID, target);
            PadView.Builder b = PadView.of(APP_ID).compact().selected("rt:" + r.id()).header("Cada cuánto · " + r.name() + ". Cada jugador puede reclamarla:");
            b.row(PadAdminUi.choice(new ItemStack(Items.CLOCK), "Cada día", "Otra vez al día siguiente.", r.type().equals("diaria"), "tipo:diaria"));
            b.row(PadAdminUi.choice(new ItemStack(Items.CLOCK), "Cada semana", "Otra vez 7 días después.", r.type().equals("semanal"), "tipo:semanal"));
            if (r.type().equals("horas")) {
                b.row(PadAdminUi.value(new ItemStack(Items.COMPASS), "Cada cierto tiempo (elegido)", "Las horas entre una vez y la siguiente.",
                        r.hours() + " h", "f:horas", f.equals("horas")));
            } else {
                b.row(PadAdminUi.choice(new ItemStack(Items.COMPASS), "Cada cierto tiempo", "Tú eliges las horas (por ejemplo 12 o 72).", false,
                        "tipo:horas"));
            }
            b.row(PadAdminUi.choice(new ItemStack(Items.CAKE), "Una sola vez", "Cada jugador la reclama una vez y ya está.", r.type().equals("unica"),
                    "tipo:unica"));
            if (f.equals("horas")) b.input("v", "HORAS ENTRE UNA VEZ Y OTRA (1 A 87.600)", 6, "GUARDAR");
            b.footer(PadView.Btn.of("LISTO", "tab:r:" + r.id(), PadView.GREEN));
            return b.build();
        }

        /** Lo que da una recompensa: objetos, monedas y monedas verdes. */
        private PadView contentsView(ServerPlayer player, PadRewards.Reward r) {
            String target = "r:" + r.id();
            PadView.Builder b = PadView.of(APP_ID).compact().selected("rc:" + r.id()).header("Lo que da · " + r.name() + ". Las monedas van al monedero, "
                    + "nunca como objeto.");
            PadAdminUi.items(b, player, APP_ID, target, r.items());
            PadAdminUi.coins(b, player, APP_ID, target, r.coins(), r.greens());
            PadAdminUi.input(b, player, APP_ID, target);
            b.footer(PadView.Btn.of("ATRÁS", "tab:r:" + r.id(), PadView.BLUE));
            b.footer(PadView.Btn.of("AÑADIR OBJETOS", "tab:pr:" + r.id(), PadView.GREEN));
            b.footer(PadView.Btn.of("MI INVENTARIO", "c.inv", PadView.GOLD));
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String rawTab, String action, String text) {
            String tab = PadItemPicker.tab(player, APP_ID, rawTab);
            int colon = tab.indexOf(':');
            String kind = colon > 0 ? tab.substring(0, colon) : tab, arg0 = colon > 0 ? tab.substring(colon + 1) : "";
            int dayIndex = kind.equals("d") || kind.equals("pd") ? dayOf(arg0, 0) : -1;
            String rid = kind.equals("r") || kind.equals("rt") || kind.equals("rc") || kind.equals("pr") || kind.equals("pi") ? arg0 : "";
            // los selectores: objetos para un día o una recompensa, y el icono de una recompensa
            if (kind.equals("pd") || kind.equals("pr") || kind.equals("pi")) {
                ItemStack chosen = PadItemPicker.action(player, APP_ID, action, text);
                if (chosen != null) {
                    if (chosen.isEmpty()) return null;
                    if (kind.equals("pi")) {
                        String id = String.valueOf(ForgeRegistries.ITEMS.getKey(chosen.getItem()));
                        edit(o -> {
                            JsonObject r = reward(o, rid);
                            if (r != null) r.addProperty("icono", id);
                        });
                        TFPadNet.notice(player, "Icono: " + chosen.getHoverName().getString() + ".");
                        return "r:" + rid;
                    }
                    String[] said = {""};
                    edit(o -> said[0] = PadAdminUi.add(kind.equals("pd") ? day(o, dayIndex) : reward(o, rid), chosen));
                    if (!said[0].isEmpty()) TFPadNet.notice(player, said[0]);
                    return null;
                }
            }
            // el editor de lo que da (objetos y monedas): en un día o en una recompensa
            if (dayIndex >= 0 && kind.equals("d")
                    && PadAdminUi.action(player, APP_ID, "d:" + dayIndex, action, text, f -> edit(o -> {
                        JsonObject d = day(o, dayIndex);
                        if (d != null) f.accept(d);
                    }))) return null;
            if (!rid.isEmpty() && PadAdminUi.action(player, APP_ID, "r:" + rid, action, text, f -> edit(o -> {
                JsonObject r = reward(o, rid);
                if (r != null) f.accept(r);
            }))) return null;
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
                    int n = PadRewards.days().size();
                    TFPadNet.notice(player, "Día " + n + " añadido. Ponle objetos o monedas.");
                    return "d:" + (n - 1);
                }
                case "cal.remove" -> {
                    if (!PadServer.confirm(player, APP_ID + ".quitardia")) return null;
                    edit(o -> {
                        JsonArray days = calendar(o).getAsJsonArray("dias");
                        if (!days.isEmpty()) days.remove(days.size() - 1);
                    });
                    TFPadNet.notice(player, "Último día quitado.");
                }
                case "nueva" -> {
                    String name = text.trim();
                    if (name.isEmpty()) return null;
                    String base = PadAdminUi.slug(name, "recompensa");
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
                    TFPadNet.notice(player, "Recompensa «" + name + "» creada (cada día). Ahora, lo que da.");
                    return "rc:" + fid;
                }
                case "on" -> edit(o -> {
                    JsonObject r = reward(o, rid);
                    if (r != null) r.addProperty("activada", !TFJson.bool(r, "activada", true));
                });
                case "tipo" -> {
                    if (!PadRewards.TYPES.contains(arg)) return null;
                    edit(o -> {
                        JsonObject r = reward(o, rid);
                        if (r == null) return;
                        r.addProperty("tipo", arg);
                        if (arg.equals("diaria")) r.addProperty("horas", 24);
                    });
                    if (arg.equals("horas")) PadAdminUi.editField(player, APP_ID, "r:" + rid, "horas");
                }
                case "f" -> {
                    String target = "r:" + rid;
                    PadAdminUi.editField(player, APP_ID, target, arg.equals(PadAdminUi.field(player, APP_ID, target)) ? "" : arg);
                }
                case "v" -> {
                    String f = PadAdminUi.field(player, APP_ID, "r:" + rid);
                    if (f.equals("nombre")) {
                        if (text.isBlank()) return null;
                        edit(o -> {
                            JsonObject r = reward(o, rid);
                            if (r != null) r.addProperty("nombre", text.trim());
                        });
                        TFPadNet.notice(player, "Guardado: «" + text.trim() + "».");
                    } else if (f.equals("horas")) {
                        long v = PadAdminUi.number(player, text, 1, 24L * 3650);
                        if (v < 0) return null;
                        edit(o -> {
                            JsonObject r = reward(o, rid);
                            if (r != null) r.addProperty("horas", v);
                        });
                        TFPadNet.notice(player, "Guardado: cada " + v + " h.");
                    } else {
                        return null;
                    }
                    PadAdminUi.stopEditing(player, APP_ID);
                }
                case "borrar" -> {
                    if (!PadServer.confirm(player, APP_ID + ".borrar:" + rid)) return null;
                    PadRewards.Reward gone = PadRewards.reward(rid);
                    edit(o -> {
                        JsonArray keep = new JsonArray();
                        for (JsonElement e : o.getAsJsonArray("recompensas")) {
                            if (!(e.isJsonObject() && TFJson.str(e.getAsJsonObject(), "id", "").equals(rid))) keep.add(e);
                        }
                        o.add("recompensas", keep);
                    });
                    TFPadNet.notice(player, "Recompensa «" + (gone == null ? rid : gone.name()) + "» borrada.");
                    return "otras";
                }
                default -> {
                }
            }
            return null;
        }
    }
}
