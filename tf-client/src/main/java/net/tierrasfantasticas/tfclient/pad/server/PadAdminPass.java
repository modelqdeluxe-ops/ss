package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * TF PASS en el pad de administrador (1.3.114, el dueño: «en el TF Pad admin no veo configuración del TF Pass»): todo
 * config/tfclient/pase.json desde el juego. Arriba, la temporada (número, nombre, días, si se renueva sola), los niveles,
 * la XP por nivel y la que dan las diarias, las semanales y las cazas; debajo, cada nivel con sus premios. En un nivel:
 * cambiar la cantidad de cada premio, quitarlo y añadir Fantastic Coins, Fantastic Gacha Coins o un objeto (de tu
 * inventario o de todos los del juego). Cada cambio se guarda y se aplica al momento.
 */
public final class PadAdminPass {
    public static final PadServer.App APP = new App();
    private static final String APP_ID = "a_pase";
    private static final int TEXT = 0x18265C;

    private PadAdminPass() {}

    static JsonObject read() {
        JsonObject o = TFJson.read(PadPass.configFile());
        if (o == null) {
            PadPass.loadConfig();
            o = TFJson.read(PadPass.configFile());
        }
        return o == null ? new JsonObject() : o;
    }

    static void write(JsonObject o) {
        TFJson.write(PadPass.configFile(), o);
        PadPass.loadConfig();
    }

    /** Los premios del nivel n (los crea si no hay). */
    private static JsonArray prizes(JsonObject o, int n) {
        if (!o.has("premios") || !o.get("premios").isJsonArray()) o.add("premios", new JsonArray());
        for (JsonElement e : o.getAsJsonArray("premios")) {
            if (e.isJsonObject() && TFJson.num(e.getAsJsonObject(), "nivel", 0) == n) {
                JsonObject l = e.getAsJsonObject();
                if (!l.has("premios") || !l.get("premios").isJsonArray()) l.add("premios", new JsonArray());
                return l.getAsJsonArray("premios");
            }
        }
        JsonObject l = new JsonObject();
        l.addProperty("nivel", n);
        l.add("premios", new JsonArray());
        o.getAsJsonArray("premios").add(l);
        return l.getAsJsonArray("premios");
    }

    private static List<PadPrize> list(JsonArray a) {
        List<PadPrize> out = new ArrayList<>();
        for (JsonElement e : a) {
            PadPrize p = PadPrize.read(e, "pase.json");
            if (p != null) out.add(p);
        }
        return out;
    }

    /** «+50 Fantastic Coins · 3 diamantes» (o «Sin premio»). */
    static String summary(List<PadPrize> ps) {
        if (ps.isEmpty()) return "Sin premio";
        StringBuilder b = new StringBuilder();
        for (PadPrize p : ps) {
            if (b.length() > 0) b.append(" · ");
            b.append(p.shownName());
        }
        return b.toString();
    }

    private record Field(String key, String title, String help, String hint, ItemStack icon, long min, long max) {}

    private static final List<Field> FIELDS = List.of(
            new Field("temporada.numero", "Temporada", "Su número: al cambiarlo, todos empiezan el pase de cero.", "NÚMERO DE LA TEMPORADA",
                    new ItemStack(Items.CLOCK), 1, 9999),
            new Field("temporada.nombre", "Nombre de la temporada", "El que se ve arriba del pase.", "NOMBRE", new ItemStack(Items.NAME_TAG), 0, 0),
            new Field("temporada.dias", "Días que dura", "Al acabar, empieza sola la siguiente (si se renueva).", "DÍAS (1 A 365)",
                    new ItemStack(Items.COMPASS), 1, 365),
            new Field("niveles", "Niveles", "Cuántos tiene el pase.", "NIVELES (1 A 200)", new ItemStack(Items.EXPERIENCE_BOTTLE), 1, 200),
            new Field("xpPorNivel", "XP por nivel", "La que hace falta para subir cada nivel.", "XP POR NIVEL", new ItemStack(Items.LAPIS_LAZULI), 1, 1_000_000),
            new Field("xp.diaria", "XP por misión diaria", "La que da cada misión diaria del pad.", "XP", new ItemStack(Items.PAPER), 0, 1_000_000),
            new Field("xp.semanal", "XP por misión semanal", "La que da cada misión semanal.", "XP", new ItemStack(Items.WRITABLE_BOOK), 0, 1_000_000),
            new Field("xp.caza", "XP por caza", "La que da cada caza cumplida.", "XP", new ItemStack(Items.IRON_SWORD), 0, 1_000_000));

    private static Field field(String key) {
        for (Field f : FIELDS) if (f.key.equals(key)) return f;
        return null;
    }

    /** El objeto donde va la clave («temporada.dias» → el de «temporada», creado si falta) y la última parte. */
    private static JsonObject parent(JsonObject o, String key) {
        int dot = key.indexOf('.');
        if (dot < 0) return o;
        String k = key.substring(0, dot);
        if (!o.has(k) || !o.get(k).isJsonObject()) o.add(k, new JsonObject());
        return o.getAsJsonObject(k);
    }

    private static String leaf(String key) {
        return key.substring(key.indexOf('.') + 1);
    }

    static final class App implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String rawTab) {
            String tab = PadItemPicker.tab(player, APP_ID, rawTab);
            JsonObject o = read();
            if (tab.startsWith("l:") || tab.startsWith("pi:")) {
                int n = level(tab);
                if (n > 0 && tab.startsWith("pi:")) {
                    return PadItemPicker.builder(player, APP_ID, tab, "Premio del nivel " + n + ": pulsa el objeto (y la cantidad arriba).", true, "")
                            .footer(PadView.Btn.of("ATRÁS", "tab:l:" + n, PadView.BLUE)).build();
                }
                if (n > 0) return levelView(player, o, n);
            }
            String f = PadAdminUi.field(player, APP_ID, "");
            JsonObject season = TFJson.obj(o, "temporada");
            PadView.Builder b = PadView.of(APP_ID).selected("").compact()
                    .header("TF Pass · temporada " + TFJson.num(season, "numero", 1) + " «" + TFJson.str(season, "nombre", "") + "»: "
                            + TFJson.num(o, "niveles", 50) + " niveles. Cada cambio se guarda en config/tfclient/pase.json y vale al momento.");
            b.row(PadAdminUi.toggle(new ItemStack(Items.LIME_DYE), "Pase activado", "Si está apagado, el pase no sale ni da XP.",
                    TFJson.bool(o, "activado", true), "Activado.", "Apagado.", "toggle:activado"));
            b.row(PadAdminUi.toggle(new ItemStack(Items.REPEATER), "Renovar sola", "Al acabar, empieza otra temporada con los mismos premios.",
                    TFJson.bool(season, "renovar", true), "Sí: empieza la siguiente.", "No: el pase se para al acabar.", "toggle:temporada.renovar"));
            for (Field fd : FIELDS) {
                JsonObject p = parent(o, fd.key);
                String value = fd.max == 0 ? TFJson.str(p, leaf(fd.key), "") : PadView.number(TFJson.num(p, leaf(fd.key), 0));
                b.row(PadAdminUi.value(fd.icon, fd.title, fd.help, value, "f:" + fd.key, f.equals(fd.key)));
            }
            b.row(PadAdminUi.value(new ItemStack(Items.MAP), "Empieza el", "La fecha del primer día (AAAA-MM-DD).",
                    TFJson.str(season, "inicio", ""), "f:temporada.inicio", f.equals("temporada.inicio")));
            b.row(PadAdminUi.section("PREMIOS POR NIVEL"));
            int levels = (int) Math.max(1, Math.min(200, TFJson.num(o, "niveles", 50)));
            for (int n = 1; n <= levels; n++) {
                List<PadPrize> ps = list(prizes(o, n));
                ItemStack icon = ps.isEmpty() ? new ItemStack(Items.GRAY_DYE) : ps.get(0).stack();
                b.row(new PadView.Row(icon, "Nivel " + n, TEXT, List.of(summary(ps)), -1, "",
                        PadView.Btn.of("EDITAR", "tab:l:" + n, PadView.BLUE), null).clickable("tab:l:" + n));
            }
            if (!f.isEmpty()) {
                Field fd = field(f);
                b.input("v", fd != null ? fd.hint : "AAAA-MM-DD", 64, "GUARDAR");
                b.footer(PadView.Btn.of("CANCELAR", "f:", PadView.GRAY));
            }
            return b.build();
        }

        private static int level(String tab) {
            try {
                return Integer.parseInt(tab.substring(tab.indexOf(':') + 1));
            } catch (Exception e) {
                return -1;
            }
        }

        private PadView levelView(ServerPlayer player, JsonObject o, int n) {
            String target = "l:" + n;
            String f = PadAdminUi.field(player, APP_ID, target);
            JsonArray a = prizes(o, n);
            List<PadPrize> ps = list(a);
            PadView.Builder b = PadView.of(APP_ID).selected(target).compact();
            b.hero(new PadView.Row(ps.isEmpty() ? new ItemStack(Items.GRAY_DYE) : ps.get(0).stack(), "Nivel " + n, 0x2EAF5C,
                    List.of(summary(ps), "Lo que cobra quien llega a este nivel."), -1, "", null, null));
            for (int i = 0; i < ps.size(); i++) {
                PadPrize p = ps.get(i);
                boolean editing = f.equals("p" + i);
                b.row(PadAdminUi.value(p.stack(), p.shownName(), "Pulsa para cambiar la cantidad.", "×" + PadView.number(p.amount()),
                        "f:p" + i, editing, PadView.Btn.of("QUITAR", "quitar:" + i, PadView.RED)));
            }
            if (ps.isEmpty()) b.row(PadAdminUi.note("Este nivel no da nada. Añade un premio con los botones de abajo."));
            b.row(PadAdminUi.button(PadKits.coinIcon(false), "Añadir Fantastic Coins", "50 al principio; luego cambia la cantidad.", "",
                    PadView.Btn.of("AÑADIR", "add:monedas", PadView.GREEN)));
            b.row(PadAdminUi.button(PadKits.coinIcon(true), "Añadir Fantastic Gacha Coins", "Las del Gachapón: 1 al principio.", "",
                    PadView.Btn.of("AÑADIR", "add:verdes", PadView.GREEN)));
            b.row(PadAdminUi.button(new ItemStack(Items.CHEST), "Añadir un objeto", "De tu inventario o de todos los del juego.", "",
                    PadView.Btn.of("ELEGIR", "tab:pi:" + n, PadView.GREEN)));
            b.footer(PadView.Btn.of("ATRÁS", "tab:", PadView.BLUE));
            if (n > 1) b.footer(PadView.Btn.of("« NIVEL " + (n - 1), "tab:l:" + (n - 1), PadView.GRAY));
            if (n < TFJson.num(o, "niveles", 50)) b.footer(PadView.Btn.of("NIVEL " + (n + 1) + " »", "tab:l:" + (n + 1), PadView.GRAY));
            if (f.startsWith("p")) b.input("v", "CANTIDAD", 12, "GUARDAR");
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String rawTab, String action, String text) {
            String tab = PadItemPicker.tab(player, APP_ID, rawTab);
            JsonObject o = read();
            int n = tab.startsWith("l:") || tab.startsWith("pi:") ? level(tab) : -1;
            // el selector de objetos (un premio nuevo del nivel)
            if (tab.startsWith("pi:") && n > 0) {
                ItemStack chosen = PadItemPicker.action(player, APP_ID, action, text);
                if (chosen != null) {
                    if (chosen.isEmpty()) return null;
                    prizes(o, n).add(PadPrize.of("objeto", Math.max(1, chosen.getCount()),
                            String.valueOf(ForgeRegistries.ITEMS.getKey(chosen.getItem()))));
                    write(o);
                    TFPadNet.notice(player, "Premio añadido al nivel " + n + ": " + chosen.getCount() + "× " + chosen.getHoverName().getString() + ".");
                    return "l:" + n;
                }
            }
            String[] a = action.split(":", 2);
            String arg = a.length > 1 ? a[1] : "";
            String target = n > 0 ? "l:" + n : "";
            switch (a[0]) {
                case "toggle" -> {
                    JsonObject p = parent(o, arg);
                    p.addProperty(leaf(arg), !TFJson.bool(p, leaf(arg), true));
                    write(o);
                }
                case "f" -> PadAdminUi.editField(player, APP_ID, target, arg.equals(PadAdminUi.field(player, APP_ID, target)) ? "" : arg);
                case "v" -> {
                    String f = PadAdminUi.field(player, APP_ID, target);
                    if (f.isEmpty() || text.isBlank()) return null;
                    String v = text.trim();
                    if (n > 0 && f.startsWith("p")) {
                        int i = PadAdminUi.parse(f.substring(1));
                        JsonArray ps = prizes(o, n);
                        long c = PadAdminUi.number(player, v, 1, 1_000_000_000L);
                        if (i < 0 || i >= ps.size() || c < 0) return null;
                        ps.get(i).getAsJsonObject().addProperty("cantidad", c);
                    } else if (f.equals("temporada.inicio")) {
                        try {
                            java.time.LocalDate.parse(v);
                        } catch (Exception e) {
                            TFPadNet.notice(player, "Escribe la fecha así: 2026-10-22.");
                            return null;
                        }
                        parent(o, f).addProperty("inicio", v);
                    } else {
                        Field fd = field(f);
                        if (fd == null) return null;
                        if (fd.max == 0) {
                            parent(o, f).addProperty(leaf(f), v);
                        } else {
                            long c = PadAdminUi.number(player, v, fd.min, fd.max);
                            if (c < 0) return null;
                            parent(o, f).addProperty(leaf(f), c);
                        }
                    }
                    write(o);
                    PadAdminUi.stopEditing(player, APP_ID);
                    TFPadNet.notice(player, "Guardado.");
                }
                case "quitar" -> {
                    int i = PadAdminUi.parse(arg);
                    JsonArray ps = n > 0 ? prizes(o, n) : null;
                    if (ps == null || i < 0 || i >= ps.size()) return null;
                    ps.remove(i);
                    write(o);
                }
                case "add" -> {
                    if (n <= 0) return null;
                    prizes(o, n).add(PadPrize.of(arg.equals("verdes") ? "verdes" : "monedas", arg.equals("verdes") ? 1 : 50, null));
                    write(o);
                }
                default -> {
                }
            }
            return null;
        }
    }
}
