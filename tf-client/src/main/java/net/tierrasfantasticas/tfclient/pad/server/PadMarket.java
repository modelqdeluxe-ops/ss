package net.tierrasfantasticas.tfclient.pad.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.market.TFMarket;
import net.tierrasfantasticas.tfclient.market.TFMarket.Listing;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;

/**
 * El GTS dentro del pad, sin chat ni ventanas aparte. MERCADO: todo lo publicado en rejilla con su precio (y un
 * buscador); cada casilla abre su ficha con COMPRAR (o RETIRAR si es tuyo). VENDER: tu inventario en rejilla; eliges
 * un objeto, escribes el precio y PUBLICAR. MIS VENTAS: lo tuyo a la venta. RECOGER: lo que no se vendió.
 * Pestañas: "" mercado, "v" vender, "m" mis ventas, "r" recoger, "l:&lt;id&gt;" una publicación.
 */
public final class PadMarket {
    public static final PadServer.App APP = new App();

    private static final int TEXT = 0x18265C, GOLD = 0xC27A10, GREEN = 0x1E9E46, MUTED = 0x7E8CA8;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private PadMarket() {}

    private static final class App implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            if (tab.startsWith("l:")) {
                Listing l = TFMarket.find(tab.substring(2));
                if (l != null) return listing(player, l);
                tab = "";
            }
            return switch (tab) {
                case "v" -> sell(player);
                case "m" -> mine(player);
                case "r" -> returns(player);
                default -> market(player);
            };
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            String[] a = action.split(":", 2);
            String arg = a.length > 1 ? a[1] : "";
            TFMarket.toPad = true;
            try {
                switch (a[0]) {
                    case "buscar" -> {
                        PadServer.put(player, "gts.q", text.isBlank() ? null : text.trim().toLowerCase(Locale.ROOT));
                        return "";
                    }
                    case "limpiar" -> {
                        PadServer.put(player, "gts.q", null);
                        return "";
                    }
                    case "volver" -> {
                        return arg;
                    }
                    case "comprar" -> {
                        Listing l = TFMarket.find(arg);
                        if (l == null) {
                            TFPadNet.notice(player, "Eso ya no está a la venta.");
                            return "";
                        }
                        if (!PadServer.confirm(player, "gts.comprar:" + arg)) return null;
                        if (TFMarket.buy(player, arg)) {
                            TFPadNet.sendState(player);
                            return "";
                        }
                        return null;
                    }
                    case "retirar" -> {
                        if (!PadServer.confirm(player, "gts.retirar:" + arg)) return null;
                        TFMarket.withdraw(player, arg);
                        return tab.startsWith("l:") ? "m" : null;
                    }
                    case "elegir" -> {
                        int slot = parse(arg);
                        if (slot < 0 || slot >= player.getInventory().items.size()) return null;
                        ItemStack stack = player.getInventory().items.get(slot);
                        String why = TFMarket.whyNot(stack);
                        if (why != null) {
                            TFPadNet.notice(player, why);
                            return null;
                        }
                        PadServer.put(player, "gts.slot", slot);
                        PadServer.put(player, "gts.snap", stack.copy());
                        return null;
                    }
                    case "publicar" -> {
                        Integer slot = PadServer.get(player, "gts.slot", null);
                        if (slot == null) {
                            TFPadNet.notice(player, "Primero pulsa lo que quieres vender.");
                            return null;
                        }
                        long price = TFMarket.parsePrice(text);
                        String error = TFMarket.publish(player, slot, PadServer.get(player, "gts.snap", null), price);
                        if (error != null) {
                            TFPadNet.notice(player, error);
                            return null;
                        }
                        PadServer.put(player, "gts.slot", null);
                        PadServer.put(player, "gts.snap", null);
                        return "m";
                    }
                    case "recoger" -> {
                        TFMarket.collect(player, parse(arg));
                        return null;
                    }
                    case "recogertodo" -> {
                        for (int i = TFMarket.returnsOf(player.getUUID()).size() - 1; i >= 0; i--) TFMarket.collect(player, i);
                        TFPadNet.notice(player, "Lo tienes todo en el inventario (lo que no cabía, a tus pies).");
                        return null;
                    }
                    default -> {
                        return null;
                    }
                }
            } finally {
                TFMarket.toPad = false;
            }
        }
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static PadView.Builder base(ServerPlayer player, String selected) {
        int mine = TFMarket.listingsOf(player.getUUID()).size();
        int back = TFMarket.returnsOf(player.getUUID()).size();
        return PadView.of("gts").tab("", "MERCADO").tab("v", "VENDER").tab("m", mine > 0 ? "MIS VENTAS (" + mine + ")" : "MIS VENTAS")
                .tab("r", back > 0 ? "RECOGER (" + back + ")" : "RECOGER").selected(selected);
    }

    private static String left(Listing l) {
        long ms = l.expiresAt() - System.currentTimeMillis();
        if (ms <= 0) return "caduca ya";
        long days = ms / DAY_MS;
        if (days >= 1) return "quedan " + days + (days == 1 ? " día" : " días");
        long hours = Math.max(1, ms / 3_600_000L);
        return "quedan " + hours + " h";
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static PadView market(ServerPlayer player) {
        PadView.Builder b = base(player, "");
        String q = PadServer.get(player, "gts.q", null);
        int n = 0;
        for (Listing l : TFMarket.listings()) {
            if (q != null && !l.item().getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)
                    && !l.sellerName().toLowerCase(Locale.ROOT).contains(q)) continue;
            boolean own = l.seller().equals(player.getUUID());
            b.cell(l.item(), PadShop.price(l.price()), own ? GREEN : GOLD, "tab:l:" + l.id(), own);
            n++;
        }
        if (q != null) {
            b.header("Buscando «" + q + "»: " + n + (n == 1 ? " resultado." : " resultados."));
            b.footer(PadView.Btn.of("LIMPIAR", "limpiar", PadView.GRAY));
        }
        b.input("buscar", "BUSCAR OBJETO O JUGADOR", 24, "BUSCAR");
        b.empty(q != null ? "Nadie vende eso ahora mismo." : "Aún no hay nada a la venta. ¡Sé el primero en VENDER!");
        return b.build();
    }

    private static PadView listing(ServerPlayer player, Listing l) {
        boolean own = l.seller().equals(player.getUUID());
        PadView.Builder b = PadView.of("gts");
        List<String> lines = new ArrayList<>();
        lines.add("Lo vende " + l.sellerName() + " · " + left(l) + ".");
        long balance = TFEconomy.balance(player.server, player.getUUID()).orElse(-1);
        if (!own && balance >= 0) lines.add(balance >= l.price() ? "Tienes " + TFEconomy.format(balance) + "."
                : "Te faltan " + TFEconomy.format(l.price() - balance) + ".");
        b.row(new PadView.Row(l.item(), TFMarket.describe(l.item()), TEXT, lines, -1, TFEconomy.format(l.price()), null, null));
        // lo que dice el objeto (encantamientos, descripción…), como en su tooltip
        List<String> tip = new ArrayList<>();
        var lines2 = l.item().getTooltipLines(player, net.minecraft.world.item.TooltipFlag.NORMAL);
        for (int i = 1; i < lines2.size() && tip.size() < 6; i++) {
            String s = lines2.get(i).getString();
            if (!s.isBlank()) tip.add(s);
        }
        if (!tip.isEmpty()) b.text("", TEXT, tip);
        b.footer(PadView.Btn.of("ATRÁS", "volver:", PadView.BLUE));
        if (own) {
            boolean sure = PadServer.confirming(player, "gts.retirar:" + l.id());
            b.footer(PadView.Btn.of(sure ? "¿SEGURO?" : "RETIRAR", "retirar:" + l.id(), PadView.RED));
        } else {
            boolean sure = PadServer.confirming(player, "gts.comprar:" + l.id());
            boolean can = balance < 0 || balance >= l.price();
            b.footer(can ? PadView.Btn.of(sure ? "¿SEGURO?" : "COMPRAR", "comprar:" + l.id(), PadView.GOLD) : PadView.Btn.off("SIN SALDO"));
        }
        return b.build();
    }

    private static PadView sell(ServerPlayer player) {
        PadView.Builder b = base(player, "v");
        int listed = TFMarket.listingsOf(player.getUUID()).size();
        Integer chosen = PadServer.get(player, "gts.slot", null);
        ItemStack snap = PadServer.get(player, "gts.snap", null);
        var items = player.getInventory().items;
        if (chosen != null && (chosen >= items.size() || snap == null || !ItemStack.matches(items.get(chosen), snap))) {
            chosen = null;
            PadServer.put(player, "gts.slot", null);
        }
        if (listed >= TFMarket.MAX_LISTINGS) {
            b.header("Ya tienes " + TFMarket.MAX_LISTINGS + " cosas a la venta. Retira alguna o espera a que se venda.");
        } else if (chosen == null) {
            b.header("Pulsa lo que quieres vender. Estará " + TFMarket.DAYS + " días; si nadie lo compra, vuelve a RECOGER.");
        } else {
            b.header("Vendes " + TFMarket.describe(items.get(chosen)) + ": precio (5k, 2m) y PUBLICAR.");
        }
        // primero la barra rápida (0-8), luego la mochila (9-35), como se ven en el inventario
        int shown = 0;
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i);
            if (s.isEmpty()) continue;
            boolean ok = TFMarket.whyNot(s) == null;
            b.cell(s, ok ? "" : "NO", MUTED, "elegir:" + i, chosen != null && chosen == i);
            shown++;
        }
        if (shown == 0) b.empty("Tu inventario está vacío.");
        if (chosen != null && listed < TFMarket.MAX_LISTINGS) b.input("publicar", "PRECIO", 14, "PUBLICAR");
        return b.build();
    }

    private static PadView mine(ServerPlayer player) {
        PadView.Builder b = base(player, "m");
        List<Listing> list = TFMarket.listingsOf(player.getUUID());
        for (Listing l : list) {
            boolean sure = PadServer.confirming(player, "gts.retirar:" + l.id());
            b.row(new PadView.Row(l.item(), TFMarket.describe(l.item()), TEXT, List.of(left(l) + "."), -1, TFEconomy.format(l.price()),
                    PadView.Btn.of(sure ? "¿SEGURO?" : "RETIRAR", "retirar:" + l.id(), PadView.RED), null).clickable("tab:l:" + l.id()));
        }
        if (!list.isEmpty()) b.header(list.size() + " de " + TFMarket.MAX_LISTINGS + " publicaciones. Cobras al momento cuando alguien compra.");
        b.empty("No tienes nada a la venta. Publica algo en VENDER.");
        return b.build();
    }

    private static PadView returns(ServerPlayer player) {
        PadView.Builder b = base(player, "r");
        List<ItemStack> list = TFMarket.returnsOf(player.getUUID());
        for (int i = 0; i < list.size(); i++) {
            ItemStack s = list.get(i);
            b.row(new PadView.Row(s, TFMarket.describe(s), TEXT, List.of("No se vendió a tiempo."), -1, "",
                    PadView.Btn.of("RECOGER", "recoger:" + i, PadView.GREEN), null));
        }
        if (list.size() > 1) b.footer(PadView.Btn.of("RECOGER TODO", "recogertodo", PadView.GREEN));
        b.empty("No tienes nada que recoger.");
        return b.build();
    }
}
