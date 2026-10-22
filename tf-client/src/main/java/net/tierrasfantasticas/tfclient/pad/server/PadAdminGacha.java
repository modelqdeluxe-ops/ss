package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
 * GACHAPÓN en el pad de administrador (1.3.114, el dueño: «en el TF Pad admin no veo configuración del gacha»): todo
 * config/tfclient/gachapon.json desde el juego. Arriba: activado, el precio de una tirada y de la múltiple (en
 * Fantastic Gacha Coins), la garantía (cada cuántas tiradas sale seguro una rareza) y la probabilidad de cada rareza.
 * Debajo, cada premio con su rareza y su peso dentro de ella: cambiar la cantidad, el peso o la rareza, quitarlo y
 * añadir premios (Fantastic Coins, Fantastic Gacha Coins o un objeto). Cada cambio se guarda y vale al momento.
 */
public final class PadAdminGacha {
    public static final PadServer.App APP = new App();
    private static final String APP_ID = "a_gacha";
    private static final int TEXT = 0x18265C;

    private PadAdminGacha() {}

    static JsonObject read() {
        JsonObject o = TFJson.read(PadGacha.configFile());
        if (o == null) {
            PadGacha.loadConfig();
            o = TFJson.read(PadGacha.configFile());
        }
        return o == null ? new JsonObject() : o;
    }

    static void write(JsonObject o) {
        TFJson.write(PadGacha.configFile(), o);
        PadGacha.loadConfig();
    }

    private static JsonArray arr(JsonObject o, String k) {
        if (!o.has(k) || !o.get(k).isJsonArray()) o.add(k, new JsonArray());
        return o.getAsJsonArray(k);
    }

    private static JsonObject obj(JsonObject o, String k) {
        if (!o.has(k) || !o.get(k).isJsonObject()) o.add(k, new JsonObject());
        return o.getAsJsonObject(k);
    }

    private static String rarityName(JsonObject o, String id) {
        for (JsonElement e : arr(o, "rarezas")) {
            if (e.isJsonObject() && TFJson.str(e.getAsJsonObject(), "id", "").equals(id)) return TFJson.str(e.getAsJsonObject(), "nombre", id);
        }
        return id;
    }

    private static int rarityColor(JsonObject o, String id) {
        for (JsonElement e : arr(o, "rarezas")) {
            if (e.isJsonObject() && TFJson.str(e.getAsJsonObject(), "id", "").equals(id)) {
                try {
                    return Integer.parseInt(TFJson.str(e.getAsJsonObject(), "color", "#8FA3BF").replace("#", ""), 16);
                } catch (NumberFormatException ex) {
                    return 0x8FA3BF;
                }
            }
        }
        return 0x8FA3BF;
    }

    private static String pct(double w, double total) {
        return total <= 0 ? "0 %" : String.format(java.util.Locale.ROOT, "%.1f %%", 100.0 * w / total).replace('.', ',');
    }

    /** Los campos de arriba: clave (a.b = el campo b del objeto a), título, explicación, icono y límites. */
    private record Field(String key, String title, String help, ItemStack icon, long min, long max) {}

    private static final List<Field> FIELDS = List.of(
            new Field("precio", "Precio de una tirada", "En Fantastic Gacha Coins.", PadKits.coinIcon(true), 1, 10_000),
            new Field("tiradaMultiple.tiradas", "Tirada múltiple: tiradas", "Cuántas de golpe (de 1 a 10).", new ItemStack(Items.HOPPER), 1, 10),
            new Field("tiradaMultiple.precio", "Tirada múltiple: precio", "En Fantastic Gacha Coins.", PadKits.coinIcon(true), 1, 100_000),
            new Field("garantia.cada", "Garantía: cada", "Cada cuántas tiradas sale seguro la rareza de abajo (0 = nunca).",
                    new ItemStack(Items.TOTEM_OF_UNDYING), 0, 1000));

    private static Field field(String key) {
        for (Field f : FIELDS) if (f.key.equals(key)) return f;
        return null;
    }

    private static JsonObject parent(JsonObject o, String key) {
        int dot = key.indexOf('.');
        return dot < 0 ? o : obj(o, key.substring(0, dot));
    }

    private static String leaf(String key) {
        return key.substring(key.indexOf('.') + 1);
    }

    static final class App implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String rawTab) {
            String tab = PadItemPicker.tab(player, APP_ID, rawTab);
            JsonObject o = read();
            if (tab.startsWith("pi:")) {
                String rarity = tab.substring(3);
                return PadItemPicker.builder(player, APP_ID, tab, "Premio " + rarityName(o, rarity) + ": pulsa el objeto (y la cantidad arriba).", true, "")
                        .footer(PadView.Btn.of("ATRÁS", "tab:", PadView.BLUE)).build();
            }
            String f = PadAdminUi.field(player, APP_ID, "");
            PadView.Builder b = PadView.of(APP_ID).selected("").compact()
                    .header("Gachapón: cada cambio se guarda en config/tfclient/gachapon.json y vale al momento. La probabilidad de un "
                            + "premio es la de su rareza × su peso dentro de ella.");
            b.row(PadAdminUi.toggle(new ItemStack(Items.LIME_DYE), "Gachapón activado", "Si está apagado no se puede tirar.",
                    TFJson.bool(o, "activado", true), "Activado.", "Apagado.", "toggle"));
            for (Field fd : FIELDS) {
                b.row(PadAdminUi.value(fd.icon, fd.title, fd.help, PadView.number(TFJson.num(parent(o, fd.key), leaf(fd.key), 0)),
                        "f:" + fd.key, f.equals(fd.key)));
            }
            String pity = TFJson.str(obj(o, "garantia"), "rareza", "epico");
            b.row(PadAdminUi.cycle(new ItemStack(Items.NETHER_STAR), "Garantía: rareza", "La que sale segura (o mejor).", rarityName(o, pity),
                    "pity:-", "pity:+"));
            // las rarezas y su probabilidad
            b.row(PadAdminUi.section("RAREZAS (PROBABILIDAD)"));
            JsonArray rs = arr(o, "rarezas");
            double total = 0;
            for (JsonElement e : rs) if (e.isJsonObject()) total += TFJson.dec(e.getAsJsonObject(), "peso", 0);
            for (int i = 0; i < rs.size(); i++) {
                if (!rs.get(i).isJsonObject()) continue;
                JsonObject r = rs.get(i).getAsJsonObject();
                String key = "r" + i;
                b.row(new PadView.Row(new ItemStack(Items.AMETHYST_SHARD), TFJson.str(r, "nombre", "?"), rarityColor(o, TFJson.str(r, "id", "")),
                        List.of("Peso " + TFJson.dec(r, "peso", 0) + " · " + pct(TFJson.dec(r, "peso", 0), total) + " de las tiradas."), -1, "",
                        PadView.Btn.of(f.equals(key) ? "ESCRIBE ABAJO" : "PESO", "f:" + key, f.equals(key) ? PadView.GOLD : PadView.BLUE), null)
                        .clickable("f:" + key).selected(f.equals(key)));
            }
            // los premios, por rareza
            JsonArray ps = arr(o, "premios");
            for (JsonElement re : rs) {
                if (!re.isJsonObject()) continue;
                String rid = TFJson.str(re.getAsJsonObject(), "id", "");
                b.row(PadAdminUi.section("PREMIOS · " + rarityName(o, rid).toUpperCase(java.util.Locale.ROOT)));
                double rt = 0;
                for (JsonElement e : ps) if (e.isJsonObject() && TFJson.str(e.getAsJsonObject(), "rareza", "").equals(rid)) rt += TFJson.dec(e.getAsJsonObject(), "peso", 0);
                for (int i = 0; i < ps.size(); i++) {
                    if (!ps.get(i).isJsonObject()) continue;
                    JsonObject p = ps.get(i).getAsJsonObject();
                    if (!TFJson.str(p, "rareza", "").equals(rid)) continue;
                    PadPrize pz = PadPrize.read(p, "gachapon.json");
                    if (pz == null) continue;
                    String kc = "c" + i, kw = "w" + i;
                    boolean ed = f.equals(kc) || f.equals(kw);
                    b.row(new PadView.Row(pz.stack(), pz.shownName(), TEXT,
                            List.of("Peso " + TFJson.dec(p, "peso", 0) + " (" + pct(TFJson.dec(p, "peso", 0), rt) + " de la rareza)"
                                    + (f.equals(kc) ? " · escribe la cantidad abajo" : f.equals(kw) ? " · escribe el peso abajo" : "")), -1,
                            "×" + PadView.number(pz.amount()), PadView.Btn.of("CANTIDAD", "f:" + kc, PadView.BLUE),
                            PadView.Btn.of("PESO", "f:" + kw, PadView.BLUE)).selected(ed));
                    b.row(new PadView.Row(ItemStack.EMPTY, "", TEXT, List.of(), -1, "",
                            PadView.Btn.of("« RAREZA", "rar:" + i + ":-", PadView.GRAY), PadView.Btn.of("QUITAR", "quitar:" + i, PadView.RED)));
                }
                b.row(new PadView.Row(new ItemStack(Items.CHEST), "Añadir a " + rarityName(o, rid), TEXT, List.of("Monedas, Gacha Coins u objeto."), -1, "",
                        PadView.Btn.of("COINS", "add:" + rid + ":monedas", PadView.GREEN), PadView.Btn.of("OBJETO", "tab:pi:" + rid, PadView.GREEN)));
            }
            if (!f.isEmpty()) {
                b.input("v", f.startsWith("c") ? "CANTIDAD" : f.startsWith("w") || f.startsWith("r") ? "PESO" : "NÚMERO", 16, "GUARDAR");
                b.footer(PadView.Btn.of("CANCELAR", "f:", PadView.GRAY));
            }
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String rawTab, String action, String text) {
            String tab = PadItemPicker.tab(player, APP_ID, rawTab);
            JsonObject o = read();
            if (tab.startsWith("pi:")) {
                ItemStack chosen = PadItemPicker.action(player, APP_ID, action, text);
                if (chosen != null) {
                    if (chosen.isEmpty()) return null;
                    JsonObject p = PadPrize.of("objeto", Math.max(1, chosen.getCount()), String.valueOf(ForgeRegistries.ITEMS.getKey(chosen.getItem())));
                    p.addProperty("rareza", tab.substring(3));
                    p.addProperty("peso", 5);
                    arr(o, "premios").add(p);
                    write(o);
                    TFPadNet.notice(player, "Premio añadido: " + chosen.getCount() + "× " + chosen.getHoverName().getString() + " (peso 5).");
                    return "";
                }
            }
            String[] a = action.split(":", 3);
            String arg = a.length > 1 ? a[1] : "";
            switch (a[0]) {
                case "toggle" -> {
                    o.addProperty("activado", !TFJson.bool(o, "activado", true));
                    write(o);
                }
                case "f" -> PadAdminUi.editField(player, APP_ID, "", arg.equals(PadAdminUi.field(player, APP_ID, "")) ? "" : arg);
                case "v" -> {
                    String f = PadAdminUi.field(player, APP_ID, "");
                    if (f.isEmpty() || text.isBlank()) return null;
                    Field fd = field(f);
                    if (fd != null) {
                        long n = PadAdminUi.number(player, text, fd.min, fd.max);
                        if (n < 0) return null;
                        parent(o, f).addProperty(leaf(f), n);
                    } else {
                        int i = PadAdminUi.parse(f.substring(1));
                        JsonArray list = arr(o, f.startsWith("r") ? "rarezas" : "premios");
                        if (i < 0 || i >= list.size() || !list.get(i).isJsonObject()) return null;
                        JsonObject t = list.get(i).getAsJsonObject();
                        if (f.startsWith("c")) {
                            long n = PadAdminUi.number(player, text, 1, 1_000_000_000L);
                            if (n < 0) return null;
                            t.addProperty("cantidad", n);
                        } else {
                            double w;
                            try {
                                w = Double.parseDouble(text.trim().replace(',', '.'));
                            } catch (NumberFormatException e) {
                                TFPadNet.notice(player, "Escribe un número (por ejemplo 5 o 2,5).");
                                return null;
                            }
                            if (w < 0 || w > 1_000_000) {
                                TFPadNet.notice(player, "El peso tiene que estar entre 0 y 1.000.000.");
                                return null;
                            }
                            t.addProperty("peso", w);
                        }
                    }
                    write(o);
                    PadAdminUi.stopEditing(player, APP_ID);
                    TFPadNet.notice(player, "Guardado.");
                }
                case "pity" -> {
                    JsonArray rs = arr(o, "rarezas");
                    if (rs.isEmpty()) return null;
                    String cur = TFJson.str(obj(o, "garantia"), "rareza", "");
                    int i = 0;
                    for (int k = 0; k < rs.size(); k++) if (TFJson.str(rs.get(k).getAsJsonObject(), "id", "").equals(cur)) i = k;
                    i = Math.floorMod(i + (arg.equals("-") ? -1 : 1), rs.size());
                    obj(o, "garantia").addProperty("rareza", TFJson.str(rs.get(i).getAsJsonObject(), "id", ""));
                    write(o);
                }
                case "rar" -> {
                    int i = PadAdminUi.parse(arg);
                    JsonArray ps = arr(o, "premios"), rs = arr(o, "rarezas");
                    if (i < 0 || i >= ps.size() || rs.isEmpty()) return null;
                    JsonObject p = ps.get(i).getAsJsonObject();
                    String cur = TFJson.str(p, "rareza", "");
                    int k = 0;
                    for (int j = 0; j < rs.size(); j++) if (TFJson.str(rs.get(j).getAsJsonObject(), "id", "").equals(cur)) k = j;
                    p.addProperty("rareza", TFJson.str(rs.get(Math.floorMod(k - 1, rs.size())).getAsJsonObject(), "id", ""));
                    write(o);
                }
                case "quitar" -> {
                    int i = PadAdminUi.parse(arg);
                    JsonArray ps = arr(o, "premios");
                    if (i < 0 || i >= ps.size()) return null;
                    ps.remove(i);
                    write(o);
                }
                case "add" -> {
                    JsonObject p = PadPrize.of("monedas", 50, null);
                    p.addProperty("rareza", arg);
                    p.addProperty("peso", 5);
                    arr(o, "premios").add(p);
                    write(o);
                }
                default -> {
                }
            }
            return null;
        }
    }
}
