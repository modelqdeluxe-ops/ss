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
import net.tierrasfantasticas.tfclient.economy.TFCoins;
import net.tierrasfantasticas.tfclient.market.TFMarket;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Piezas comunes del pad de administrador, para que todas sus apps se manejen igual:
 * <ul>
 *   <li>Filas: interruptor (SÍ/NO con lo que hace en cada caso), valor (lo que vale ahora en su etiqueta y CAMBIAR, que
 *       abre el campo de abajo), opción (una de varias: ELEGIR / ELEGIDO), título de sección y nota.</li>
 *   <li>El campo que se está escribiendo: uno a la vez por app, en la sesión «&lt;app&gt;.f» = «ficha|campo», así no se
 *       queda abierto al pasar a otra ficha. CANCELAR lo cierra.</li>
 *   <li>El editor de «lo que trae» un kit, un día del calendario o una recompensa: los objetos en casillas (pulsa uno para
 *       cambiar su cantidad o quitarlo), las monedas y las monedas verdes como números (van al monedero, nunca como
 *       objeto), AÑADIR desde el selector o todo tu inventario de una vez. Las acciones empiezan por «c.».</li>
 * </ul>
 */
final class PadAdminUi {
    static final int TEXT = 0x18265C, MUTED = 0x7E8CA8, GOLD = 0xC27A10;

    private PadAdminUi() {}

    // ---------------------------------------------------------------------------------------------------------------
    // Filas
    // ---------------------------------------------------------------------------------------------------------------

    /** Interruptor: el título, para qué sirve y lo que pasa ahora (onText / offText). Toda la fila se puede pulsar. */
    static PadView.Row toggle(ItemStack icon, String title, String help, boolean on, String onText, String offText, String action) {
        List<String> lines = new ArrayList<>();
        if (!help.isEmpty()) lines.add(help);
        lines.add(on ? onText : offText);
        return new PadView.Row(icon, title, on ? TEXT : MUTED, lines, -1, "",
                PadView.Btn.of(on ? "SÍ" : "NO", action, on ? PadView.GREEN : PadView.RED), null).clickable(action);
    }

    /** Un valor: lo que vale en su etiqueta y CAMBIAR (abre el campo de abajo). editing: la fila sale marcada. */
    static PadView.Row value(ItemStack icon, String title, String help, String value, String action, boolean editing) {
        return new PadView.Row(icon, title, TEXT, help.isEmpty() ? List.of() : List.of(help), -1, value,
                PadView.Btn.of(editing ? "ESCRIBE ABAJO" : "CAMBIAR", action, editing ? PadView.GOLD : PadView.BLUE), null)
                .clickable(action).selected(editing);
    }

    /** Lo mismo con un segundo botón (QUITAR, ELEGIR…). */
    static PadView.Row value(ItemStack icon, String title, String help, String value, String action, boolean editing, PadView.Btn second) {
        return new PadView.Row(icon, title, TEXT, help.isEmpty() ? List.of() : List.of(help), -1, value,
                PadView.Btn.of(editing ? "ESCRIBE ABAJO" : "CAMBIAR", action, editing ? PadView.GOLD : PadView.BLUE), second)
                .clickable(action).selected(editing);
    }

    /** Una opción de varias: la elegida sale marcada con ELEGIDO; las otras, con ELEGIR. */
    static PadView.Row choice(ItemStack icon, String title, String help, boolean chosen, String action) {
        PadView.Btn btn = chosen ? new PadView.Btn("ELEGIDO", "", PadView.GREEN, true) : PadView.Btn.of("ELEGIR", action, PadView.BLUE);
        PadView.Row r = new PadView.Row(icon, title, chosen ? TEXT : MUTED, help.isEmpty() ? List.of() : List.of(help), -1, "", btn, null)
                .selected(chosen);
        return chosen ? r : r.clickable(action);
    }

    /** Un botón (fila con icono, título, explicación y un botón). */
    static PadView.Row button(ItemStack icon, String title, String help, String value, PadView.Btn btn) {
        return new PadView.Row(icon, title, TEXT, help.isEmpty() ? List.of() : List.of(help), -1, value, btn, null)
                .clickable(btn.action());
    }

    /** Saltos de los botones − y + de las monedas (y precios) y de las monedas verdes. */
    static final long[] COIN_STEPS = {0, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10_000, 25_000, 50_000, 100_000, 250_000,
            500_000, 1_000_000};
    static final long[] GREEN_STEPS = {0, 1, 2, 3, 5, 10, 15, 20, 25, 50, 100, 250, 500};

    /** El siguiente salto hacia arriba (dir &gt; 0) o hacia abajo desde v. */
    static long step(long v, long[] steps, int dir) {
        if (dir > 0) {
            for (long s : steps) if (s > v) return s;
            return Math.max(v, steps[steps.length - 1]);
        }
        long best = 0;
        for (long s : steps) if (s < v) best = s;
        return best;
    }

    /**
     * Un número con − y + (1.3.67): los botones saltan por valores redondos; pulsar la fila abre el campo de abajo para
     * escribir el número exacto. editing: la fila sale marcada.
     */
    static PadView.Row stepper(ItemStack icon, String title, String help, String value, String minus, String plus, String click,
                               boolean editing) {
        return new PadView.Row(icon, title, TEXT, help.isEmpty() ? List.of() : List.of(help), -1, value,
                PadView.Btn.of("-", minus, PadView.BLUE), PadView.Btn.of("+", plus, PadView.BLUE)).clickable(click).selected(editing);
    }

    /** Una de varias opciones con « y » para pasar por ellas (1.3.67). */
    static PadView.Row cycle(ItemStack icon, String title, String help, String value, String prev, String next) {
        return new PadView.Row(icon, title, TEXT, help.isEmpty() ? List.of() : List.of(help), -1, value,
                PadView.Btn.of("«", prev, PadView.BLUE), PadView.Btn.of("»", next, PadView.BLUE)).clickable(next);
    }

    static PadView.Row section(String title) {
        return new PadView.Row(ItemStack.EMPTY, title, TEXT, List.of(), -1, "", null, null);
    }

    static PadView.Row note(String text) {
        return new PadView.Row(ItemStack.EMPTY, "", TEXT, List.of(text), -1, "", null, null);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // El campo que se está escribiendo
    // ---------------------------------------------------------------------------------------------------------------

    /** El campo de la ficha target que se está escribiendo, o "". */
    static String field(ServerPlayer p, String app, String target) {
        String v = PadServer.get(p, app + ".f", "");
        return v.startsWith(target + "|") ? v.substring(target.length() + 1) : "";
    }

    static void editField(ServerPlayer p, String app, String target, String field) {
        PadServer.put(p, app + ".f", field == null || field.isEmpty() ? null : target + "|" + field);
        PadServer.put(p, app + ".sel", null);
    }

    static void stopEditing(ServerPlayer p, String app) {
        PadServer.put(p, app + ".f", null);
        PadServer.put(p, app + ".sel", null);
    }

    /** Un número escrito (1500, 1.500, 5k, 2m). -1 si no vale. */
    static long number(String text) {
        String t = text.trim();
        if (t.equals("0")) return 0;
        return TFMarket.parsePrice(t);
    }

    /** Lee un número entre min y max; si no vale, avisa y devuelve -1. */
    static long number(ServerPlayer p, String text, long min, long max) {
        long v = number(text);
        if (v < 0) {
            TFPadNet.notice(p, "Escribe solo un número (por ejemplo 250, 1.500 o 5k).");
            return -1;
        }
        if (v < min || v > max) {
            TFPadNet.notice(p, "Tiene que estar entre " + PadView.number(min) + " y " + PadView.number(max) + ".");
            return -1;
        }
        return v;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Lo que trae (kit, día del calendario, recompensa)
    // ---------------------------------------------------------------------------------------------------------------

    private static int selected(ServerPlayer p, String app, String target) {
        String v = PadServer.get(p, app + ".sel", "");
        if (!v.startsWith(target + "|")) return -1;
        try {
            return Integer.parseInt(v.substring(target.length() + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** El índice de verdad en «objetos» de la entrada n.º valid de las que se leen (las ilegibles no cuentan). */
    private static int realIndex(JsonArray a, int valid) {
        int n = 0;
        for (int i = 0; i < a.size(); i++) {
            if (PadItemPicker.stack(a.get(i)).isEmpty()) continue;
            if (n++ == valid) return i;
        }
        return -1;
    }

    private static JsonArray objects(JsonObject o) {
        if (!o.has("objetos") || !o.get("objetos").isJsonArray()) o.add("objetos", new JsonArray());
        return o.getAsJsonArray("objetos");
    }

    /**
     * Añade un objeto a lo que trae target: si es una moneda, se suma a «monedas» o «verdes» (no va como objeto); si ya
     * estaba el mismo objeto, se suma a su cantidad. Devuelve el aviso para el admin.
     */
    static String add(JsonObject target, ItemStack s) {
        if (target == null || s.isEmpty()) return "";
        TFCoins.Kind k = TFCoins.kind(s);
        if (k != TFCoins.Kind.NONE) {
            String key = k == TFCoins.Kind.GOLD ? "monedas" : "verdes";
            target.addProperty(key, Math.max(0, TFJson.num(target, key, 0)) + s.getCount());
            return "Las monedas no van como objeto: +" + s.getCount() + (k == TFCoins.Kind.GOLD ? " monedas" : " monedas verdes")
                    + " que se ingresan en el monedero.";
        }
        JsonArray a = objects(target);
        for (int i = 0; i < a.size(); i++) {
            ItemStack have = PadItemPicker.stack(a.get(i));
            if (have.isEmpty() || !ItemStack.isSameItemSameTags(have, s)) continue;
            ItemStack sum = have.copy();
            sum.setCount((int) Math.min(PadItemPicker.MAX_AMOUNT, (long) have.getCount() + s.getCount()));
            a.set(i, PadItemPicker.json(sum));
            return "Ahora trae " + sum.getCount() + " × " + s.getHoverName().getString() + ".";
        }
        a.add(PadItemPicker.json(s));
        return "Añadido: " + s.getCount() + " × " + s.getHoverName().getString() + ".";
    }

    /**
     * Las casillas con los objetos (pulsa uno para elegirlo) y debajo, si hay uno elegido, su fila con la cantidad,
     * QUITAR y LISTO; si no, cuántos hay y VACIAR (con confirmación).
     */
    static void items(PadView.Builder b, ServerPlayer p, String app, String target, List<ItemStack> items) {
        items(b, p, app, target, items, null, null);
    }

    /**
     * Lo mismo, pero si se dan add1 y add2 (MI INVENTARIO, ELEGIR…) la fila de debajo de las casillas lleva esos botones
     * para añadir en vez de VACIAR (1.3.67: una fila menos en la ficha del kit).
     */
    static void items(PadView.Builder b, ServerPlayer p, String app, String target, List<ItemStack> items, PadView.Btn add1, PadView.Btn add2) {
        int sel = selected(p, app, target);
        for (int i = 0; i < items.size(); i++) b.cell(items.get(i).copy(), "", TEXT, "c.sel:" + i, i == sel);
        if (sel >= 0 && sel < items.size()) {
            ItemStack s = items.get(sel);
            b.row(new PadView.Row(s.copy(), s.getHoverName().getString(), TEXT,
                    List.of("Escribe abajo cuántos trae (1 a " + PadView.number(PadItemPicker.MAX_AMOUNT) + ") o quítalo."), -1,
                    "×" + PadView.number(s.getCount()), PadView.Btn.of("QUITAR", "c.del:" + sel, PadView.RED),
                    PadView.Btn.of("LISTO", "c.sel:-1", PadView.GRAY)).selected(true));
        } else if (add1 != null) {
            b.row(new PadView.Row(new ItemStack(Items.CHEST), items.isEmpty() ? "Sin objetos" : items.size() + (items.size() == 1 ? " objeto" : " objetos"),
                    TEXT, List.of(items.isEmpty() ? "MI INVENTARIO: todo lo que llevas. ELEGIR: uno a uno."
                    : "Pulsa uno arriba para cambiarlo o quitarlo."), -1, "", add1, add2));
        } else if (!items.isEmpty()) {
            boolean sure = PadServer.confirming(p, app + ".vaciar:" + target);
            b.row(new PadView.Row(new ItemStack(Items.CHEST), items.size() + (items.size() == 1 ? " objeto" : " objetos"), TEXT,
                    List.of("Pulsa uno de arriba para cambiar cuántos trae o para quitarlo."), -1, "",
                    PadView.Btn.of(sure ? "¿SEGURO?" : "VACIAR", "c.vaciar", PadView.RED), null));
        } else {
            b.row(note("Aún no trae objetos. AÑADIR OBJETOS: elige de tu inventario o de todos los del juego (también de mods). "
                    + "MI INVENTARIO: añade de una vez todo lo que llevas encima."));
        }
    }

    /**
     * Las filas de las monedas (de oro y verdes): números que se ingresan en el monedero, nunca objetos. 1.3.67: con − y +
     * (saltos redondos); pulsando la fila se escribe el número exacto.
     */
    static void coins(PadView.Builder b, ServerPlayer p, String app, String target, long coins, long greens) {
        String f = field(p, app, target);
        b.row(stepper(PadKits.coinIcon(false), "Monedas", "Al monedero; pulsa la fila para escribirlas.",
                coins > 0 ? "+" + PadView.moneyExact(coins) : "Ninguna", "c.step:monedas:-", "c.step:monedas:+", "c.f:monedas",
                f.equals("monedas")));
        b.row(stepper(PadKits.coinIcon(true), "Monedas verdes", "Del Gachapón; pulsa la fila para escribirlas.",
                greens > 0 ? "+" + PadView.number(greens) : "Ninguna", "c.step:verdes:-", "c.step:verdes:+", "c.f:verdes", f.equals("verdes")));
    }

    /** El campo de abajo del editor: la cantidad del objeto elegido o las monedas que se cambian. true si lo puso. */
    static boolean input(PadView.Builder b, ServerPlayer p, String app, String target) {
        if (selected(p, app, target) >= 0) {
            b.input("c.qty", "CUÁNTOS TRAE (1 A " + PadView.number(PadItemPicker.MAX_AMOUNT) + ")", 8, "GUARDAR");
            return true;
        }
        String f = field(p, app, target);
        if (f.equals("monedas") || f.equals("verdes")) {
            b.input("c.val", f.equals("monedas") ? "MONEDAS (0 = NINGUNA)" : "MONEDAS VERDES (0 = NINGUNA)", 12, "GUARDAR");
            return true;
        }
        return false;
    }

    /**
     * Las acciones «c.…» del editor. edit cambia el objeto JSON (kit, día o recompensa) y lo guarda; si no existe, no
     * llama a la función. Devuelve true si la acción era del editor.
     */
    static boolean action(ServerPlayer p, String app, String target, String action, String text, Consumer<Consumer<JsonObject>> edit) {
        if (!action.startsWith("c.")) return false;
        String[] a = action.split(":", 2);
        String arg = a.length > 1 ? a[1] : "";
        switch (a[0]) {
            case "c.sel" -> {
                int i = parse(arg);
                PadServer.put(p, app + ".f", null);
                PadServer.put(p, app + ".sel", i < 0 || i == selected(p, app, target) ? null : target + "|" + i);
            }
            case "c.del" -> {
                int i = parse(arg);
                String[] said = {""};
                edit.accept(o -> {
                    JsonArray arr = objects(o);
                    int real = realIndex(arr, i);
                    if (real < 0) return;
                    ItemStack s = PadItemPicker.stack(arr.get(real));
                    said[0] = "Quitado: " + s.getCount() + " × " + s.getHoverName().getString() + ".";
                    arr.remove(real);
                });
                PadServer.put(p, app + ".sel", null);
                if (!said[0].isEmpty()) TFPadNet.notice(p, said[0]);
            }
            case "c.qty" -> {
                int i = selected(p, app, target);
                long n = number(p, text, 1, PadItemPicker.MAX_AMOUNT);
                if (i < 0 || n < 0) return true;
                edit.accept(o -> {
                    JsonArray arr = objects(o);
                    int real = realIndex(arr, i);
                    if (real < 0) return;
                    ItemStack s = PadItemPicker.stack(arr.get(real));
                    s.setCount((int) n);
                    arr.set(real, PadItemPicker.json(s));
                });
                PadServer.put(p, app + ".sel", null);
                TFPadNet.notice(p, "Guardado: trae " + PadView.number(n) + ".");
            }
            case "c.f" -> editField(p, app, target, arg.equals(field(p, app, target)) ? "" : arg);
            case "c.val" -> {
                String f = field(p, app, target);
                if (!f.equals("monedas") && !f.equals("verdes")) return true;
                long v = number(p, text, 0, 1_000_000_000L);
                if (v < 0) return true;
                edit.accept(o -> o.addProperty(f, v));
                stopEditing(p, app);
                TFPadNet.notice(p, v == 0 ? "Sin " + (f.equals("monedas") ? "monedas." : "monedas verdes.")
                        : "Guardado: +" + PadView.number(v) + (f.equals("monedas") ? " monedas" : " monedas verdes") + " al monedero.");
            }
            case "c.step" -> { // c.step:monedas:+ o c.step:verdes:-
                String[] k = arg.split(":");
                if (k.length < 2 || !(k[0].equals("monedas") || k[0].equals("verdes"))) return true;
                long[] steps = k[0].equals("monedas") ? COIN_STEPS : GREEN_STEPS;
                long[] now = {0};
                edit.accept(o -> {
                    now[0] = step(Math.max(0, TFJson.num(o, k[0], 0)), steps, k[1].equals("+") ? 1 : -1);
                    o.addProperty(k[0], now[0]);
                });
                stopEditing(p, app);
            }
            case "c.zero" -> {
                if (arg.equals("monedas") || arg.equals("verdes")) edit.accept(o -> o.addProperty(arg, 0));
                stopEditing(p, app);
            }
            case "c.inv" -> {
                List<ItemStack> inv = new ArrayList<>();
                for (ItemStack s : p.getInventory().items) if (!PadItemPicker.hidden(s)) inv.add(s.copy());
                if (inv.isEmpty()) {
                    TFPadNet.notice(p, "No llevas nada en el inventario (el pad de administrador no cuenta).");
                    return true;
                }
                edit.accept(o -> {
                    for (ItemStack s : inv) add(o, s);
                });
                TFPadNet.notice(p, "Añadido todo tu inventario (" + inv.size() + (inv.size() == 1 ? " montón" : " montones")
                        + "). Las monedas, si llevabas, van como monedas.");
            }
            case "c.vaciar" -> {
                if (!PadServer.confirm(p, app + ".vaciar:" + target)) return true;
                edit.accept(o -> o.add("objetos", new JsonArray()));
                TFPadNet.notice(p, "Sin objetos.");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    /** El id corto (para el archivo) a partir de un nombre: «Kit de inicio» → «kit_de_inicio». */
    static String slug(String name, String fallback) {
        String base = name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        if (base.isEmpty()) base = fallback;
        return base.length() > 24 ? base.substring(0, 24) : base;
    }

    /** ¿Tiene «objetos» alguno? (para no recorrer arrays que no lo son). */
    static boolean hasItems(JsonObject o) {
        if (!o.has("objetos") || !o.get("objetos").isJsonArray()) return false;
        for (JsonElement e : o.getAsJsonArray("objetos")) if (!PadItemPicker.stack(e).isEmpty()) return true;
        return false;
    }
}
