/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad.server;

import java.util.ArrayList;
import java.util.HashMap;
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
                        // las tarjetas mandan cuántas veces la cantidad del objeto; en el campo se escriben unidades
                        if (a.length > 3) {
                            lots = parse(a[3]);
                        } else if (!a[0].equals("vendertodo")) {
                            long units = units(text);
                            long max = (long) TFShopConfig.maxLots * e.amount();
                            if (units <= 0 || units % e.amount() != 0 || units > max) {
                                TFPadNet.notice(player, askUnits(e, units));
                                return null;
                            }
                            lots = (int) (units / e.amount());
                        }
                        if (!a[0].equals("vendertodo") && lots <= 0) return null;
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

    /** Las unidades escritas en «otra cantidad» («1024», «1.024» o «1 024»); 0 si no es un número. */
    private static long units(String text) {
        String t = text.trim().replace(".", "").replace(" ", "").replace("_", "");
        if (t.isEmpty() || t.length() > 12 || !t.chars().allMatch(Character::isDigit)) return 0;
        return Long.parseLong(t);
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

    /**
     * El objeto tal como se ve, con estas unidades: la cantidad es la de verdad (128, 1.024…, aunque el objeto se apile
     * de 64 en 64 o de 16 en 16), y el pad dibuja ese número encima (PadView la manda aparte; el objeto no va a ningún
     * inventario).
     */
    private static ItemStack shown(Entry e, long units) {
        return e.stack((int) Math.max(1, Math.min(Integer.MAX_VALUE, units)));
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static PadView.Builder base(String selected) {
        return PadView.of("tienda").tab("", "COMPRAR").tab("v", "VENDER").selected(selected);
    }

    /** Lo que se compra de una categoría (con permiso): COMPRAR solo enseña esto; lo que solo se vende va en VENDER. */
    private static int buyable(ServerPlayer player, Category c) {
        int n = 0;
        for (Entry e : c.entries()) if (e.buyable() && player.hasPermissions(e.permission())) n++;
        return n;
    }

    private static PadView categories(ServerPlayer player, List<Category> cats) {
        PadView.Builder b = base("");
        for (int i = 0; i < cats.size(); i++) {
            Category c = cats.get(i);
            int n = buyable(player, c);
            if (n == 0) continue;
            List<String> lines = new ArrayList<>();
            for (String d : c.description()) {
                String p = plain(d);
                if (!p.isBlank()) lines.add(p);
                if (lines.size() >= 1) break;
            }
            b.row(new PadView.Row(new ItemStack(c.icon()), plain(c.name()), c.color() == 0 ? TEXT : c.color() & 0xFFFFFF, lines, -1,
                    n + (n == 1 ? " objeto" : " objetos"), null, null).clickable("tab:c:" + i));
        }
        b.empty("No hay nada a la venta. Para vender lo tuyo, mira VENDER.");
        return b.build();
    }

    private static PadView category(ServerPlayer player, int ci, Category c) {
        PadView.Builder b = PadView.of("tienda").header(plain(c.name())).cards();
        for (int i = 0; i < c.entries().size(); i++) {
            Entry e = c.entries().get(i);
            if (!player.hasPermissions(e.permission()) || !e.buyable()) continue;
            ItemStack shown = shown(e, e.amount());
            String name = e.name().isBlank() ? shown.getHoverName().getString() : plain(e.name());
            b.card(shown, name, 0xF6B628, coins(e.buy()), "tab:i:" + ci + ":" + i + ":b", false);
        }
        b.empty("Aquí no hay nada a la venta.");
        b.footer(PadView.Btn.of("ATRÁS", "volver", PadView.BLUE));
        return b.build();
    }

    /**
     * La ficha de un objeto, de compra o de venta (no se mezclan): arriba, su cabecera grande (el objeto, de cuántas unidades es el precio, cuántas
     * llevas, tu saldo y lo que te queda hoy si hay límite, con ese precio en la etiqueta); debajo, una tarjeta por cantidad («128 unidades», con el
     * objeto marcado con esas 128 y lo que cuesta o lo que cobras); las que no puedes pagar o vender salen apagadas y dicen por qué (el límite de
     * hoy primero: aunque tuvieras más, hoy no se puede). Comprar: otra cantidad (en unidades) abajo.
     */
    private static PadView item(ServerPlayer player, int ci, Category c, int ei, Entry e, boolean selling) {
        if (selling && !e.sellable()) selling = false;
        if (!selling && !e.buyable()) selling = true;
        // comprar y vender no se mezclan: la ficha es de compra (desde COMPRAR) o de venta (desde VENDER)
        PadView.Builder b = PadView.of("tienda").selected("i:" + ci + ":" + ei + ":" + (selling ? "s" : "b"));
        int have = TFShop.count(player.getInventory(), e);
        long balance = TFEconomy.balance(player.server, player.getUUID()).orElse(-1);
        // lo que deja hoy el límite diario, en veces la cantidad del objeto (límite de 50 y de 16 en 16: 3 veces, 48 unidades)
        long daily = selling ? e.sellDaily() : e.buyDaily();
        long dayLots = TFShop.left(player.getUUID(), (selling ? "v:" : "c:") + e.key(), daily) / e.amount();
        List<String> lines = new ArrayList<>();
        // dos líneas como mucho (lo que dice el objeto, en su tooltip): así las tarjetas caben debajo sin desplazar
        lines.add((e.amount() == 1 ? "Precio por unidad" : "Precio por " + units(e.amount())) + " · llevas " + PadView.number(have) + ".");
        String verb = selling ? "vender" : "comprar";
        String today = daily <= 0 ? "" : dayLots <= 0 ? "Hoy ya no puedes " + verb + " más."
                : "Hoy puedes " + verb + " " + units(dayLots * e.amount()) + " más.";
        if (!selling && balance >= 0) lines.add("Tienes " + TFEconomy.format(balance) + "." + (today.isEmpty() ? "" : " " + today));
        else if (!today.isEmpty()) lines.add(today);
        String badge = selling ? "+" + PadView.moneyExact(e.sell()) : PadView.moneyExact(e.buy());
        b.hero(new PadView.Row(shown(e, e.amount()), TFShop.name(e), selling ? 0x40C850 : 0xF6B628, lines, -1, badge, null, null));

        String cat = ci + ":" + ei;
        if (selling) {
            long haveLots = have / e.amount();
            long canLots = Math.min(haveLots, dayLots);
            for (int step : TFShopConfig.steps) {
                if (step > TFShopConfig.maxLots) continue;
                long units = (long) step * e.amount();
                boolean ok = step <= canLots;
                String sub = ok ? "+" + coins(e.sell() * step) : step > dayLots ? "Límite de hoy" : "Faltan " + missing(units - have);
                b.card(shown(e, units), units(units), 0x40C850, sub, ok ? "vender:" + cat + ":" + step : "", false);
            }
            b.footer(PadView.Btn.of("ATRÁS", "volver:v", PadView.BLUE));
            // VENDER N vende todo lo que se puede (lo que llevas, de N en N, hasta el límite de hoy): justo esas N unidades
            if (canLots > 0) b.footer(PadView.Btn.of("VENDER " + PadView.number(canLots * e.amount()), "vendertodo:" + cat, PadView.GREEN));
            else if (haveLots > 0) b.footer(PadView.Btn.off("LÍMITE DE HOY"));
            else b.footer(PadView.Btn.off(have == 0 ? "NO TIENES" : "FALTAN " + PadView.number(e.amount() - have)));
        } else {
            for (int step : TFShopConfig.steps) {
                if (step > TFShopConfig.maxLots) continue;
                long units = (long) step * e.amount();
                long cost = e.buy() * step;
                boolean afford = balance < 0 || cost <= balance;
                boolean inLimit = step <= dayLots;
                String sub = afford && inLimit ? coins(cost) : !inLimit ? "Límite de hoy" : "Faltan " + missing(cost - balance);
                b.card(shown(e, units), units(units), 0xF6B628, sub, afford && inLimit ? "comprar:" + cat + ":" + step : "", false);
            }
            long maxUnits = (long) TFShopConfig.maxLots * e.amount();
            b.input("comprar:" + cat, e.amount() == 1 ? "OTRA CANTIDAD (UNIDADES)" : "OTRA CANTIDAD (DE " + e.amount() + " EN " + e.amount() + ")",
                    PadView.number(maxUnits).length(), "COMPRAR");
            b.footer(PadView.Btn.of("ATRÁS", "volver:c/" + ci, PadView.BLUE));
        }
        return b.build();
    }

    /**
     * Un precio en una etiqueta: exacto («¤1.299.999») hasta 9.999.999, que cabe en la tarjeta más estrecha, porque es
     * justo lo que se paga o se cobra (PadView.money lo acortaría hacia abajo desde 100.000: «¤1,2M»); más, corto.
     */
    private static String coins(long v) {
        return v < 10_000_000 ? PadView.moneyExact(v) : PadView.money(v);
    }

    /**
     * Lo que falta (monedas o unidades) en una etiqueta de tarjeta: exacto hasta 9.999 («Faltan 1.845» cabe en la
     * tarjeta más estrecha); desde ahí, corto y redondeado hacia arriba («Faltan 19K» si faltan 18.500), nunca de menos.
     */
    private static String missing(long v) {
        return v < 10_000 ? PadView.number(v) : PadView.shortUp(v);
    }

    /** «1 unidad», «16 unidades», «1.024 unidades»: la tienda siempre habla en unidades. */
    private static String units(long n) {
        return PadView.number(n) + (n == 1 ? " unidad" : " unidades");
    }

    /**
     * Qué escribir en «otra cantidad» (unidades, de N en N si el objeto va de N en N), según lo que se escribió: más del
     * máximo, una cantidad que no va de N en N (con las dos buenas más cercanas) o nada que se entienda.
     */
    private static String askUnits(Entry e, long typed) {
        long n = e.amount(), max = (long) TFShopConfig.maxLots * n;
        if (typed > max) return "Como mucho " + units(max) + " de una vez.";
        if (typed > 0 && typed % n != 0) {
            long down = typed / n * n, up = down + n;
            return "Se compra de " + n + " en " + n + ": escribe " + (down > 0 ? PadView.number(down) + " o " : "") + PadView.number(up) + ".";
        }
        if (n == 1) return "Escribe cuántas unidades quieres (1 a " + PadView.number(max) + ").";
        if (max == n) return "Escribe " + n + ": se compra de " + n + " en " + n + ".";
        return "Escribe cuántas unidades quieres, de " + n + " en " + n + " (" + n + ", " + PadView.number(2 * n) + "… hasta " + PadView.number(max) + ").";
    }

    /**
     * Lo que llevas encima que la tienda compra, agrupado por objeto. «Puedes ganar…» es justo lo que da VENDER TODO:
     * se cuenta igual que TFShop.sellAll (en el mismo orden, gastando lo que llevas; si dos objetos de la tienda son el
     * mismo, lo que vende el primero ya no lo vende el segundo).
     */
    private static PadView sell(ServerPlayer player) {
        PadView.Builder b = base("v");
        Map<Entry, int[]> found = new LinkedHashMap<>();
        Map<String, Integer> spent = new HashMap<>();
        long total = 0;
        List<Category> cats = TFShop.visible(player);
        for (int ci = 0; ci < cats.size(); ci++) {
            Category c = cats.get(ci);
            for (int ei = 0; ei < c.entries().size(); ei++) {
                Entry e = c.entries().get(ei);
                if (!e.sellable() || !player.hasPermissions(e.permission())) continue;
                int have = TFShop.count(player.getInventory(), e);
                String same = e.itemId() + "|" + e.nbt();
                int left = Math.max(0, have - spent.getOrDefault(same, 0));
                long lots = Math.min(left / e.amount(), TFShop.left(player.getUUID(), "v:" + e.key(), e.sellDaily()) / e.amount());
                if (lots > 0) {
                    total += e.sell() * lots;
                    spent.merge(same, (int) (lots * e.amount()), Integer::sum);
                }
                if (have >= e.amount() && !found.containsKey(e)) found.put(e, new int[] {ci, ei, have});
            }
        }
        for (Map.Entry<Entry, int[]> f : found.entrySet()) {
            Entry e = f.getKey();
            int[] v = f.getValue();
            long dayLeft = TFShop.left(player.getUUID(), "v:" + e.key(), e.sellDaily());
            long lots = Math.min(v[2] / e.amount(), dayLeft / e.amount());
            long pay = e.sell() * lots;
            List<String> lines = List.of("Llevas " + PadView.number(v[2]) + " · " + TFEconomy.format(e.sell())
                    + (e.amount() == 1 ? " la unidad" : " por " + units(e.amount())));
            PadView.Btn btn = lots > 0 ? PadView.Btn.of("VENDER", "vendertodo:" + v[0] + ":" + v[1], PadView.GREEN) : PadView.Btn.off("LÍMITE");
            b.row(new PadView.Row(shown(e, v[2]), TFShop.name(e), TEXT, lines, -1, lots > 0 ? "+" + coins(pay) : "", btn, null)
                    .clickable("tab:i:" + v[0] + ":" + v[1] + ":s"));
        }
        if (found.isEmpty()) {
            b.empty("No llevas nada que la tienda compre.");
        } else {
            b.header(total > 0 ? "Puedes ganar " + TFEconomy.format(total) + " con lo que llevas." : "Hoy ya no puedes vender más de lo que llevas.");
            if (TFShopConfig.sellAllButton && total > 0) {
                boolean sure = PadServer.confirming(player, "tienda.todo");
                b.footer(PadView.Btn.of(sure ? "¿SEGURO?" : "VENDER TODO", "todo", PadView.GOLD));
            }
        }
        return b.build();
    }

    /** Precio corto para debajo de una casilla: 950, 1,2K, 35K, 1,5M. */
    static String price(long v) {
        return PadView.shortNumber(v);
    }
}
