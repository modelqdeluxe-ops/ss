package net.tierrasfantasticas.tfclient.pad.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.shop.TFShop;
import net.tierrasfantasticas.tfclient.shop.TFShopConfig;
import net.tierrasfantasticas.tfclient.shop.TFShopConfig.Category;
import net.tierrasfantasticas.tfclient.shop.TFShopConfig.Entry;

/**
 * La tienda del servidor (/tf shop) dentro del pad. Pestañas: COMPRAR (las categorías; cada una abre su rejilla de
 * objetos con el precio debajo y cada objeto, su ficha con las cantidades) y VENDER (lo que llevas encima que la tienda
 * compra, con su precio). Pestañas internas: "" categorías, "v" vender, "c:&lt;n&gt;" una categoría,
 * "i:&lt;n&gt;:&lt;m&gt;:b|s" un objeto (comprar o vender).
 */
public final class PadShop {
    public static final PadServer.App APP = new App();

    private static final int TEXT = 0x18265C;

    private PadShop() {}

    private static final class App implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            if (!TFShopConfig.enabled && !player.hasPermissions(3)) {
                return PadView.of("tienda").empty("Tienda cerrada.").build();
            }
            List<Category> cats = TFShop.visible(player);
            String[] t = tab.split(":");
            if (t[0].equals("c") && t.length > 1) {
                Category c = cat(cats, t[1]);
                if (c != null) return category(player, cats.indexOf(c), c);
            }
            if (t[0].equals("i") && t.length > 2) {
                Category c = cat(cats, t[1]);
                Entry e = c == null ? null : entry(c, t[2]);
                if (e != null) return item(player, cats.indexOf(c), c, c.entries().indexOf(e), e, t.length > 3 && t[3].equals("s"));
            }
            if (t[0].equals("v")) return sell(player);
            return categories(player, cats);
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            List<Category> cats = TFShop.visible(player);
            String[] a = action.split(":");
            TFShop.toPad = true;
            try {
                switch (a[0]) {
                    case "volver" -> {
                        return a.length > 1 ? a[1].replace('/', ':') : "";
                    }
                    case "todo" -> {
                        if (!PadServer.confirm(player, "tienda.todo")) return null;
                        TFShop.sellAll(player);
                        TFPadNet.sendState(player);
                        return null;
                    }
                    case "comprar", "vender", "vendertodo" -> {
                        Category c = a.length > 2 ? cat(cats, a[1]) : null;
                        Entry e = c == null ? null : entry(c, a[2]);
                        if (e == null) return null;
                        int lots = 0;
                        if (a.length > 3) lots = parse(a[3]);
                        else if (!text.isBlank()) lots = parse(text);
                        if (!a[0].equals("vendertodo") && lots <= 0) {
                            TFPadNet.notice(player, "Escribe cuántos lotes quieres (1 a " + TFShopConfig.maxLots + ").");
                            return null;
                        }
                        lots = Math.min(lots, TFShopConfig.maxLots);
                        if (a[0].equals("comprar")) TFShop.buy(player, e, lots);
                        else TFShop.sell(player, e, a[0].equals("vendertodo") ? 0 : lots, false);
                        TFPadNet.sendState(player);
                        return null;
                    }
                    default -> {
                        return null;
                    }
                }
            } finally {
                TFShop.toPad = false;
            }
        }
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Category cat(List<Category> cats, String index) {
        int i = parse(index);
        return i >= 0 && i < cats.size() ? cats.get(i) : null;
    }

    private static Entry entry(Category c, String index) {
        int i = parse(index);
        return i >= 0 && i < c.entries().size() ? c.entries().get(i) : null;
    }

    private static String plain(String s) {
        String p = ChatFormatting.stripFormatting(TFShop.colors(s));
        return p == null ? "" : p;
    }

    /** El objeto tal como se ve, con la cantidad de un lote (como mucho 64 en el dibujo). */
    private static ItemStack shown(Entry e, long units) {
        return e.stack((int) Math.max(1, Math.min(64, units)));
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static PadView.Builder base(String selected) {
        return PadView.of("tienda").tab("", "COMPRAR").tab("v", "VENDER").selected(selected);
    }

    private static PadView categories(ServerPlayer player, List<Category> cats) {
        PadView.Builder b = base("");
        for (int i = 0; i < cats.size(); i++) {
            Category c = cats.get(i);
            List<String> lines = new ArrayList<>();
            for (String d : c.description()) {
                String p = plain(d);
                if (!p.isBlank()) lines.add(p);
                if (lines.size() >= 1) break;
            }
            int n = c.entries().size();
            b.row(new PadView.Row(new ItemStack(c.icon()), plain(c.name()), c.color() == 0 ? TEXT : c.color() & 0xFFFFFF, lines, -1,
                    n + (n == 1 ? " objeto" : " objetos"), null, null).clickable("tab:c:" + i));
        }
        if (cats.isEmpty()) b.empty("Tienda vacía.");
        return b.build();
    }

    private static PadView category(ServerPlayer player, int ci, Category c) {
        PadView.Builder b = PadView.of("tienda").header(plain(c.name())).cards();
        for (int i = 0; i < c.entries().size(); i++) {
            Entry e = c.entries().get(i);
            if (!player.hasPermissions(e.permission())) continue;
            boolean buy = e.buyable();
            ItemStack shown = shown(e, e.amount());
            String name = e.name().isBlank() ? shown.getHoverName().getString() : plain(e.name());
            String sub = buy ? PadView.money(e.buy()) : e.sellable() ? "+" + PadView.money(e.sell()) : "";
            b.card(shown, name, buy ? 0xF6B628 : 0x40C850, sub, "tab:i:" + ci + ":" + i + (buy ? ":b" : ":s"), false);
        }
        if (c.entries().isEmpty()) b.empty("Vacía.");
        b.footer(PadView.Btn.of("ATRÁS", "volver", PadView.BLUE));
        return b.build();
    }

    /**
     * La ficha de un objeto: arriba, su cabecera grande (el objeto, cuánto trae cada lote, cuántos llevas, tu saldo y lo
     * que te queda hoy si hay límite, con el precio del lote en la etiqueta); debajo, una tarjeta por cantidad («8 lotes», con lo que cuesta o
     * lo que cobras); las que no puedes pagar o vender salen apagadas y dicen por qué. Comprar: otra cantidad abajo.
     */
    private static PadView item(ServerPlayer player, int ci, Category c, int ei, Entry e, boolean selling) {
        if (selling && !e.sellable()) selling = false;
        if (!selling && !e.buyable()) selling = true;
        String key = "i:" + ci + ":" + ei + ":";
        PadView.Builder b = PadView.of("tienda");
        if (e.buyable()) b.tab(key + "b", "COMPRAR");
        if (e.sellable()) b.tab(key + "s", "VENDER");
        b.selected(key + (selling ? "s" : "b"));
        int have = TFShop.count(player.getInventory(), e);
        long balance = TFEconomy.balance(player.server, player.getUUID()).orElse(-1);
        List<String> lines = new ArrayList<>();
        // dos líneas como mucho (lo que dice el objeto, en su tooltip): así las tarjetas caben debajo sin desplazar
        lines.add((e.amount() == 1 ? "Por unidad" : "Lote de " + e.amount()) + " · llevas " + have + ".");
        long daily = selling ? e.sellDaily() : e.buyDaily();
        long dayLeftNow = daily > 0 ? Math.max(0, TFShop.left(player.getUUID(), (selling ? "v:" : "c:") + e.key(), daily)) : -1;
        String today = dayLeftNow >= 0 ? "Hoy puedes " + (selling ? "vender " : "comprar ") + dayLeftNow + " más." : "";
        if (!selling && balance >= 0) lines.add("Tienes " + TFEconomy.format(balance) + "." + (today.isEmpty() ? "" : " " + today));
        else if (!today.isEmpty()) lines.add(today);
        String badge = selling ? "+" + PadView.moneyExact(e.sell()) : PadView.moneyExact(e.buy());
        b.hero(new PadView.Row(shown(e, e.amount()), TFShop.name(e), selling ? 0x40C850 : 0xF6B628, lines, -1, badge, null, null));

        String cat = ci + ":" + ei;
        if (selling) {
            long dayLeft = TFShop.left(player.getUUID(), "v:" + e.key(), e.sellDaily());
            int canLots = (int) Math.min(have / e.amount(), dayLeft / e.amount());
            for (int step : TFShopConfig.steps) {
                if (step > TFShopConfig.maxLots) continue;
                long units = (long) step * e.amount();
                boolean ok = step <= canLots;
                String sub = ok ? "+" + PadView.money(e.sell() * step) : units > have ? "No tienes " + units : "Límite de hoy";
                b.card(shown(e, units), amount(e, step), 0x40C850, sub, ok ? "vender:" + cat + ":" + step : "", false);
            }
            b.footer(PadView.Btn.of("ATRÁS", "volver:c/" + ci, PadView.BLUE));
            b.footer(canLots > 0 ? PadView.Btn.of("VENDER " + canLots * e.amount(), "vendertodo:" + cat, PadView.GREEN) : PadView.Btn.off("NO TIENES"));
        } else {
            long dayLeft = TFShop.left(player.getUUID(), "c:" + e.key(), e.buyDaily());
            for (int step : TFShopConfig.steps) {
                if (step > TFShopConfig.maxLots) continue;
                long cost = e.buy() * step;
                boolean afford = balance < 0 || cost <= balance;
                boolean ok = afford && (long) step * e.amount() <= dayLeft;
                String sub = ok ? PadView.money(cost) : !afford ? "Faltan " + PadShop.price(cost - balance) : "Límite de hoy";
                b.card(shown(e, (long) step * e.amount()), amount(e, step), 0xF6B628, sub, ok ? "comprar:" + cat + ":" + step : "", false);
            }
            b.input("comprar:" + cat, "OTRA CANTIDAD (LOTES)", 3, "COMPRAR");
            b.footer(PadView.Btn.of("ATRÁS", "volver:c/" + ci, PadView.BLUE));
        }
        return b.build();
    }

    /** El nombre de cada opción: «16 uds.» si se vende de uno en uno; si no, «8 lotes · 128». */
    private static String amount(Entry e, int step) {
        long units = (long) step * e.amount();
        if (e.amount() == 1) return units + (units == 1 ? " unidad" : " uds.");
        return step + (step == 1 ? " lote · " : " lotes · ") + units;
    }


    /** Lo que llevas encima que la tienda compra, agrupado por objeto. */
    private static PadView sell(ServerPlayer player) {
        PadView.Builder b = base("v");
        Map<Entry, int[]> found = new LinkedHashMap<>();
        List<Category> cats = TFShop.visible(player);
        for (int ci = 0; ci < cats.size(); ci++) {
            Category c = cats.get(ci);
            for (int ei = 0; ei < c.entries().size(); ei++) {
                Entry e = c.entries().get(ei);
                if (!e.sellable() || !player.hasPermissions(e.permission()) || found.containsKey(e)) continue;
                int have = TFShop.count(player.getInventory(), e);
                if (have >= e.amount()) found.put(e, new int[] {ci, ei, have});
            }
        }
        long total = 0;
        for (Map.Entry<Entry, int[]> f : found.entrySet()) {
            Entry e = f.getKey();
            int[] v = f.getValue();
            long dayLeft = TFShop.left(player.getUUID(), "v:" + e.key(), e.sellDaily());
            int lots = (int) Math.min(v[2] / e.amount(), dayLeft / e.amount());
            long pay = e.sell() * lots;
            total += pay;
            List<String> lines = List.of("Llevas " + v[2] + " · " + TFEconomy.format(e.sell()) + (e.amount() == 1 ? " c/u" : " por " + e.amount()));
            PadView.Btn btn = lots > 0 ? PadView.Btn.of("VENDER", "vendertodo:" + v[0] + ":" + v[1], PadView.GREEN) : PadView.Btn.off("LÍMITE");
            b.row(new PadView.Row(shown(e, v[2]), TFShop.name(e), TEXT, lines, -1, lots > 0 ? "+" + PadView.money(pay) : "", btn, null)
                    .clickable("tab:i:" + v[0] + ":" + v[1] + ":s"));
        }
        if (found.isEmpty()) {
            b.empty("No llevas nada que la tienda compre.");
        } else {
            b.header("Puedes ganar " + TFEconomy.format(total) + " con lo que llevas.");
            if (TFShopConfig.sellAllButton) {
                boolean sure = PadServer.confirming(player, "tienda.todo");
                b.footer(PadView.Btn.of(sure ? "¿SEGURO?" : "VENDER TODO", "todo", PadView.GOLD));
            }
        }
        return b.build();
    }

    /** Precio corto para debajo de una casilla: 950, 1,2K, 35K, 1,5M. */
    static String price(long v) {
        if (v < 1000) return Long.toString(v);
        if (v < 10_000) return trim(v / 1000.0) + "K";
        if (v < 1_000_000) return (v / 1000) + "K";
        return trim(v / 1_000_000.0) + "M";
    }

    private static String trim(double d) {
        String s = String.format(java.util.Locale.ROOT, "%.1f", Math.floor(d * 10) / 10);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s.replace('.', ',');
    }
}
