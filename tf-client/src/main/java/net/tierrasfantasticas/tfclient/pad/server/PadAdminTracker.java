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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.rastreo.TFPurchases;
import net.tierrasfantasticas.tfclient.rastreo.TFSearch;
import net.tierrasfantasticas.tfclient.rastreo.TFTracker;

/**
 * (1.3.142, el dueño: «una app de rastreador en el pad de admin: cada jugador que compró algo, y al picarlo todos sus
 * objetos comprados con su dirección, coordenadas, si las tiene puestas, cofres, lo que sea; que funcione desde el
 * minuto 1») RASTREADOR:
 * <ul>
 *   <li>COMPRADORES: los que compraron en la web (lista de la web, TFPurchases), con cuántos objetos tienen ellos, en
 *   cofres, con OTRO jugador o sin encontrar. Primero los que tienen algo con otro.</li>
 *   <li>Al pulsar uno («j:uuid»): cada compra con todos sus objetos y dónde está cada uno (TFSearch): puesto, en la mano,
 *   inventario, cofre de ender, accesorios, un cofre o lo que sea con sus coordenadas, en el suelo… IR lleva allí;
 *   TRAER lo quita de donde esté y se lo da a su dueño (a un desconectado se lo quita al entrar). Y lo último que se
 *   hizo con sus cosas (TFTracker).</li>
 *   <li>MOVIMIENTOS: lo último que se sacó, metió o rompió en contenedores (TFTracker).</li>
 *   <li>Buscar («s:texto»): por jugador, pedido u objeto, comprado o ligado.</li>
 * </ul>
 */
public final class PadAdminTracker {
    public static final String APP_ID = "a_rastreo";
    public static final PadServer.App APP = new App();
    private static final int TEXT = PadAdminUi.TEXT, MUTED = PadAdminUi.MUTED;
    private static final int GREEN = 0x1E7C2C, RED = 0xC8323C, GOLD = 0xC27A10;
    /** Quién tiene la app abierta (y en qué pestaña): al acabar un escaneo se le refresca. */
    private static final Map<UUID, String> VIEWERS = new ConcurrentHashMap<>();

    private PadAdminTracker() {}

    /** Un comprador: su uuid (o su nombre si la web no lo tenía), su nombre y sus compras. */
    record Buyer(String key, String name, String uuid, List<TFPurchases.Purchase> purchases) {}

    /** Cómo están los objetos de alguien: con él, en contenedores/suelo, con otro jugador, sin encontrar. */
    record Tally(int mine, int stored, int others, int missing) {}

    static List<Buyer> buyers(MinecraftServer server) {
        Map<String, Buyer> out = new LinkedHashMap<>();
        for (TFPurchases.Purchase p : TFPurchases.all(server)) {
            String key = !p.uuid().isEmpty() ? p.uuid().toLowerCase(Locale.ROOT) : "n:" + p.player().toLowerCase(Locale.ROOT);
            out.computeIfAbsent(key, k -> new Buyer(k, p.player(), p.uuid().toLowerCase(Locale.ROOT), new ArrayList<>())).purchases.add(p);
        }
        return new ArrayList<>(out.values());
    }

    /** Lo encontrado de una compra (por su pedido; los de antes sin pedido, por su dueño). */
    static List<TFSearch.Found> foundOf(TFPurchases.Purchase p) {
        List<TFSearch.Found> out = new ArrayList<>();
        TFSearch.Scan scan = TFSearch.last();
        if (scan == null) return out;
        for (TFSearch.Found f : scan.found()) if (f.order().equals(p.order())) out.add(f);
        return out;
    }

    /** ¿Lo tiene el comprador (encima, en su cofre de ender o puesto)? */
    static boolean withBuyer(TFSearch.Found f, String buyerUuid, String buyerName) {
        return f.withPlayer() && (f.holderUuid().equalsIgnoreCase(buyerUuid) || f.holder().equalsIgnoreCase(buyerName));
    }

    static Tally tally(Buyer b) {
        int mine = 0, stored = 0, others = 0, missing = 0;
        for (TFPurchases.Purchase p : b.purchases) {
            List<TFSearch.Found> found = foundOf(p);
            for (TFSearch.Found f : found) {
                if (withBuyer(f, b.uuid, b.name)) mine += f.count();
                else if (f.withPlayer()) others += f.count();
                else stored += f.count();
            }
            for (TFPurchases.Expected e : p.items()) {
                int have = 0;
                for (TFSearch.Found f : found) if (f.item().equals(e.id())) have += f.count();
                if (have < e.count() && isGear(e.id())) missing += e.count() - have;
            }
        }
        return new Tally(mine, stored, others, missing);
    }

    /** Las piezas de los sets (lo que no se gasta): lo demás (manzanas, perlas…) se come o se usa. */
    static boolean isGear(String id) {
        return id.startsWith("tfclient:");
    }

    static ItemStack icon(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        return item == null || item == Items.AIR ? new ItemStack(Items.PAPER) : new ItemStack(item);
    }

    static String date(String iso) {
        // «2026-10-25T14:03:11.123Z» → «25/10/2026 14:03 (UTC)»
        if (iso == null || iso.length() < 16) return iso == null ? "" : iso;
        return iso.substring(8, 10) + "/" + iso.substring(5, 7) + "/" + iso.substring(0, 4) + " " + iso.substring(11, 16) + " UTC";
    }

    static String scanLine() {
        TFSearch.Scan s = TFSearch.last();
        if (TFSearch.running()) return "Buscando… " + TFSearch.progress();
        if (s == null) return "Sin buscar aún: BUSCAR AHORA.";
        return "Buscado hace " + ago(s.at()) + ": " + s.found().size() + " objetos.";
    }

    static String ago(long at) {
        long s = Math.max(0, (System.currentTimeMillis() - at) / 1000);
        return s < 60 ? s + " s" : s < 3600 ? s / 60 + " min" : s / 3600 + " h";
    }

    /** Al acabar un escaneo, a los que tienen la app abierta se les vuelve a mandar. */
    static void refreshViewers(MinecraftServer server) {
        for (Map.Entry<UUID, String> e : VIEWERS.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) PadServer.refresh(p, APP_ID, e.getValue());
        }
    }

    static void scan(MinecraftServer server) {
        TFSearch.start(server, true, () -> refreshViewers(server));
    }

    // ---------------------------------------------------------------------------------------------------------------

    static final class App implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            MinecraftServer server = player.getServer();
            VIEWERS.put(player.getUUID(), tab);
            TFPurchases.askSoon();
            // la primera vez (o si hace mucho), se busca solo
            TFSearch.Scan s = TFSearch.last();
            if (!TFSearch.running() && (s == null || System.currentTimeMillis() - s.at() > 10 * 60_000L)) scan(server);
            PadView.Builder b = PadView.of(APP_ID).tab("", "COMPRADORES").tab("mov", "MOVIMIENTOS");
            if (tab.startsWith("j:")) return buyer(b, player, tab.substring(2));
            if (tab.startsWith("s:")) return search(b, tab.substring(2));
            if (tab.equals("mov")) return moves(b, server);
            return list(b, server);
        }

        private PadView list(PadView.Builder b, MinecraftServer server) {
            b.selected("");
            List<Buyer> all = buyers(server);
            b.header(all.size() + (all.size() == 1 ? " comprador. " : " compradores. ") + scanLine());
            b.input("buscar", "Buscar jugador, pedido u objeto", 32, "BUSCAR");
            Map<String, Tally> t = new HashMap<>();
            for (Buyer x : all) t.put(x.key, tally(x));
            all.sort(Comparator.comparingInt((Buyer x) -> t.get(x.key).others() > 0 ? 0 : t.get(x.key).missing() > 0 ? 1 : 2));
            for (Buyer x : all) {
                Tally tl = t.get(x.key);
                List<String> names = new ArrayList<>();
                for (TFPurchases.Purchase p : x.purchases) if (names.size() < 3) names.add(p.product());
                String more = x.purchases.size() > 3 ? " y " + (x.purchases.size() - 3) + " más" : "";
                List<String> lines = new ArrayList<>();
                lines.add(x.purchases.size() + (x.purchases.size() == 1 ? " compra: " : " compras: ") + String.join(", ", names) + more);
                lines.add("Con él " + tl.mine() + " · en cofres o el suelo " + tl.stored() + (tl.others() > 0 ? " · CON OTROS " + tl.others() : "")
                        + (tl.missing() > 0 ? " · sin encontrar " + tl.missing() : ""));
                int color = tl.others() > 0 ? RED : tl.missing() > 0 ? GOLD : GREEN;
                String badge = tl.others() > 0 ? "CON OTROS" : tl.missing() > 0 ? "FALTAN" : "BIEN";
                UUID u = uuidOrNull(x.uuid);
                b.row(new PadView.Row(u != null ? PadClans.head(u, x.name) : new ItemStack(Items.PLAYER_HEAD), x.name, color, lines, -1, badge,
                        PadView.Btn.of("VER", "j:" + x.key, PadView.BLUE), null).clickable("j:" + x.key));
            }
            b.empty("Aún no hay compras de la web (llegan por el puente; ¿está conectado?).");
            b.footer(PadView.Btn.of(TFSearch.running() ? "BUSCANDO…" : "BUSCAR AHORA", "escanear", PadView.GOLD));
            return b.build();
        }

        private PadView buyer(PadView.Builder b, ServerPlayer viewer, String key) {
            b.selected("");
            MinecraftServer server = viewer.getServer();
            Buyer x = null;
            for (Buyer c : buyers(server)) if (c.key.equals(key)) x = c;
            if (x == null) {
                b.header("No está en la lista de compras.");
                return b.build();
            }
            Tally tl = tally(x);
            UUID u = uuidOrNull(x.uuid);
            b.header(scanLine());
            b.hero(new PadView.Row(u != null ? PadClans.head(u, x.name) : new ItemStack(Items.PLAYER_HEAD), x.name, tl.others() > 0 ? RED : TEXT,
                    List.of(x.purchases.size() + " compras · con él " + tl.mine() + " · en cofres o el suelo " + tl.stored()
                            + (tl.others() > 0 ? " · CON OTROS " + tl.others() : "") + (tl.missing() > 0 ? " · sin encontrar " + tl.missing() : ""),
                            (u != null && server.getPlayerList().getPlayer(u) != null ? "Conectado ahora · " : "") + "UUID " + x.uuid), -1, "", null, null));
            for (TFPurchases.Purchase p : x.purchases) {
                List<TFSearch.Found> found = foundOf(p);
                boolean anyAway = false;
                for (TFSearch.Found f : found) if (!withBuyer(f, x.uuid, x.name)) anyAway = true;
                String first = p.items().isEmpty() ? "minecraft:paper" : p.items().get(0).id();
                List<String> lines = new ArrayList<>();
                lines.add(date(p.paidAt()) + " · pedido " + p.order() + (p.quantity() > 1 ? " · ×" + p.quantity() : ""));
                if (!p.giftFrom().isEmpty()) lines.add("Regalo de " + p.giftFrom());
                if (p.refundedOrDisputed()) lines.add("REEMBOLSADA O EN DISPUTA (" + (p.refunded().isEmpty() ? p.status() : p.refunded()) + ")");
                boolean sure = PadServer.confirming(viewer, APP_ID + ".traerp:" + p.order());
                b.row(new PadView.Row(icon(first), p.product(), p.refundedOrDisputed() ? MUTED : GOLD, lines, -1, p.items().size() + " objetos",
                        anyAway ? PadView.Btn.of(sure ? "¿SEGURO?" : "TRAER TODO", "traerp:" + p.order(), PadView.RED) : null, null));
                // cada objeto de la compra (y lo encontrado de ese pedido que no estaba en la lista)
                Map<String, Integer> expected = new LinkedHashMap<>();
                for (TFPurchases.Expected e : p.items()) expected.merge(e.id(), e.count(), Integer::sum);
                for (TFSearch.Found f : found) expected.putIfAbsent(f.item(), 0);
                for (Map.Entry<String, Integer> e : expected.entrySet()) itemRow(b, viewer, x, p, e.getKey(), e.getValue(), found);
            }
            // lo ligado a él sin pedido (entregas de antes de marcar los pedidos, o de /tf web sets give a mano)
            TFSearch.Scan scan = TFSearch.last();
            if (scan != null && !x.uuid.isEmpty()) {
                Map<String, List<TFSearch.Found>> loose = new LinkedHashMap<>();
                for (TFSearch.Found f : scan.found()) {
                    if (f.order().isEmpty() && f.ownerUuid().equalsIgnoreCase(x.uuid)) loose.computeIfAbsent(f.item(), k -> new ArrayList<>()).add(f);
                }
                if (!loose.isEmpty()) b.text("Ligados a él sin pedido de la web", TEXT, List.of("Objetos de sets con su nombre que no traen la marca de una compra."));
                for (Map.Entry<String, List<TFSearch.Found>> e : loose.entrySet()) {
                    List<String> lines = new ArrayList<>();
                    boolean away = false, other = false;
                    for (TFSearch.Found f : e.getValue()) {
                        if (!withBuyer(f, x.uuid, x.name)) {
                            away = true;
                            other |= f.withPlayer();
                        }
                        if (lines.size() < 3) lines.add((withBuyer(f, x.uuid, x.name) || !f.withPlayer() ? "" : "LO TIENE OTRO: ") + TFSearch.where(f));
                    }
                    String id = x.uuid + "|" + e.getKey();
                    boolean sure = PadServer.confirming(viewer, APP_ID + ".traerl:" + id);
                    b.row(new PadView.Row(icon(e.getKey()), TFSearch.itemName(e.getKey()), other ? RED : away ? TEXT : GREEN, lines, -1,
                            other ? "OTRO" : "", away ? PadView.Btn.of(sure ? "¿SEGURO?" : "TRAER", "traerl:" + id, PadView.RED) : null, null));
                }
            }
            // lo último que se hizo con sus cosas
            List<String> orders = new ArrayList<>();
            for (TFPurchases.Purchase p : x.purchases) orders.add(p.order());
            String name = x.name;
            List<String[]> moves = TFTracker.recent(server, 14, f -> orders.contains(f[9]) || f[8].equalsIgnoreCase(name), 10);
            if (!moves.isEmpty()) {
                List<String> lines = new ArrayList<>();
                for (int i = moves.size() - 1; i >= 0; i--) lines.add(TFTracker.describe(moves.get(i)));
                b.text("Lo último que se hizo con sus cosas", TEXT, lines);
            }
            b.footer(PadView.Btn.of(TFSearch.running() ? "BUSCANDO…" : "BUSCAR AHORA", "escanear", PadView.GOLD));
            return b.build();
        }

        private void itemRow(PadView.Builder b, ServerPlayer viewer, Buyer x, TFPurchases.Purchase p, String item, int want,
                             List<TFSearch.Found> found) {
            List<TFSearch.Found> copies = new ArrayList<>();
            for (TFSearch.Found f : found) if (f.item().equals(item)) copies.add(f);
            int have = 0;
            boolean withOther = false, away = false;
            int goTo = -1;
            for (int i = 0; i < copies.size(); i++) {
                TFSearch.Found f = copies.get(i);
                have += f.count();
                if (!withBuyer(f, x.uuid, x.name)) {
                    away = true;
                    if (f.withPlayer()) withOther = true;
                    else if (goTo < 0 && !f.dim().isEmpty()) goTo = i;
                }
            }
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < Math.min(3, copies.size()); i++) {
                TFSearch.Found f = copies.get(i);
                String who = withBuyer(f, x.uuid, x.name) ? "" : f.withPlayer() ? "LO TIENE OTRO: " : "";
                lines.add(who + (copies.size() > 1 || f.count() > 1 ? f.count() + "× " : "") + TFSearch.where(f));
            }
            if (copies.size() > 3) lines.add("… y " + (copies.size() - 3) + " sitios más");
            boolean gear = isGear(item);
            if (copies.isEmpty()) lines.add(gear ? "No está en ningún sitio del mundo guardado (ni en jugadores)." : "No está: gastado o usado (o sin la marca de la compra).");
            int color = withOther ? RED : copies.isEmpty() ? (gear ? GOLD : MUTED) : away ? TEXT : GREEN;
            String badge = (want > 0 ? have + "/" + want : have + "") + (withOther ? " OTRO" : "");
            String id = p.order() + "|" + item;
            PadView.Btn go = goTo >= 0 ? PadView.Btn.of("IR", "ir:" + id + "|" + goTo, PadView.BLUE) : null;
            boolean sure = PadServer.confirming(viewer, APP_ID + ".traer:" + id);
            PadView.Btn bring = away ? PadView.Btn.of(sure ? "¿SEGURO?" : "TRAER", "traer:" + id, PadView.RED) : null;
            b.row(new PadView.Row(icon(item), TFSearch.itemName(item) + (want > 1 ? " ×" + want : ""), color, lines, -1, badge,
                    bring != null ? bring : go, bring != null ? go : null));
        }

        private PadView search(PadView.Builder b, String text) {
            b.selected("");
            String q = text.toLowerCase(Locale.ROOT).trim();
            TFSearch.Scan s = TFSearch.last();
            b.header("«" + text + "» · " + scanLine());
            b.input("buscar", "Buscar jugador, pedido u objeto", 32, "BUSCAR");
            if (s != null && !q.isEmpty()) {
                int n = 0;
                for (TFSearch.Found f : s.found()) {
                    String name = TFSearch.itemName(f.item()).toLowerCase(Locale.ROOT);
                    if (!(f.ownerName().toLowerCase(Locale.ROOT).contains(q) || f.holder().toLowerCase(Locale.ROOT).contains(q)
                            || f.order().toLowerCase(Locale.ROOT).contains(q) || f.item().contains(q) || name.contains(q))) continue;
                    if (++n > 120) break;
                    List<String> lines = new ArrayList<>();
                    lines.add(TFSearch.where(f));
                    lines.add((f.ownerName().isEmpty() ? "" : "De " + f.ownerName() + " · ") + (f.order().isEmpty() ? "ligado, sin pedido" : "pedido " + f.order()));
                    boolean other = f.withPlayer() && !f.ownerName().isEmpty() && !f.holder().equalsIgnoreCase(f.ownerName());
                    b.row(new PadView.Row(icon(f.item()), TFSearch.itemName(f.item()) + " ×" + f.count(), other ? RED : TEXT, lines, -1,
                            other ? "OTRO" : "", null, null));
                }
            }
            b.empty(s == null ? "Aún no se ha buscado en el mundo: pulsa BUSCAR AHORA." : "Nada con «" + text + "».");
            b.footer(PadView.Btn.of(TFSearch.running() ? "BUSCANDO…" : "BUSCAR AHORA", "escanear", PadView.GOLD));
            return b.build();
        }

        private PadView moves(PadView.Builder b, MinecraftServer server) {
            b.selected("mov");
            b.header("Lo último que se sacó, metió o rompió en cofres y demás contenedores (los objetos comprados, con su dueño).");
            List<String[]> moves = TFTracker.recent(server, 3, f -> true, 80);
            for (int i = moves.size() - 1; i >= 0; i--) {
                String[] f = moves.get(i);
                boolean other = !f[8].isEmpty() && !f[8].equalsIgnoreCase(f[1]) && !f[1].equals("staff");
                int color = other && f[6].equals("SACA") ? RED : f[6].equals("SACA") || f[6].equals("ROMPE") ? GOLD : TEXT;
                b.row(new PadView.Row(icon(f[7]), f[1] + " " + (f[6].equals("SACA") ? "sacó" : f[6].equals("METE") ? "metió" : f[6].equals("ROMPE") ? "rompió con" : "recuperó")
                        + " " + f[10] + "× " + TFSearch.itemName(f[7]), color,
                        List.of((f[8].isEmpty() ? "" : "De " + f[8] + (other ? " (NO ES SUYO)" : "") + " · ") + TFSearch.blockName(f[5]) + " " + f[3] + " " + f[4],
                                f[0].split(" ")[0] + " " + (f[0].split(" ").length > 1 ? f[0].split(" ")[1] : "")), -1, "", null, null));
            }
            b.empty("Aún no hay movimientos (se apuntan desde la 1.3.142).");
            return b.build();
        }

        // -----------------------------------------------------------------------------------------------------------

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            MinecraftServer server = player.getServer();
            if (action.equals("escanear")) {
                if (!TFSearch.running()) scan(server);
                TFPadNet.notice(player, "Buscando en todo el mundo… se actualiza solo al acabar.");
                return null;
            }
            if (action.equals("buscar")) return text == null || text.isBlank() ? "" : "s:" + text.trim();
            if (action.startsWith("j:")) return action;
            if (action.startsWith("ir:")) {
                String[] a = action.substring(3).split("\\|");
                if (a.length < 3) return null;
                TFSearch.Found f = nth(a[0], a[1], Integer.parseInt(a[2]));
                ServerLevel level = f == null ? null : levelOf(server, f.dim());
                if (level == null) {
                    TFPadNet.notice(player, "No se sabe en qué mundo está.");
                    return null;
                }
                TFPadNet.close(player);
                player.teleportTo(level, f.x() + 0.5, f.y() + 1, f.z() + 0.5, player.getYRot(), player.getXRot());
                TFPadNet.notice(player, TFSearch.describe(f));
                return null;
            }
            if (action.startsWith("traerl:")) {
                if (!PadServer.confirm(player, APP_ID + "." + action)) return null;
                String[] a = action.substring(7).split("\\|", 2);
                UUID to = uuidOrNull(a[0]);
                if (to == null || a.length < 2) return null;
                int[] r = TFSearch.recover(server, new TFSearch.Crit("", a[0], a[1]), to);
                TFPadNet.notice(player, "Recuperados " + r[0] + (r[1] > 0 ? "; " + r[1] + " al entrar el que los tiene" : "") + ".");
                scan(server);
                return null;
            }
            if (action.startsWith("traer:") || action.startsWith("traerp:")) {
                boolean all = action.startsWith("traerp:");
                String rest = action.substring(all ? 7 : 6);
                if (!PadServer.confirm(player, APP_ID + "." + action)) return null;
                String[] a = rest.split("\\|", 2);
                TFPurchases.Purchase p = TFPurchases.get(server, a[0]);
                UUID to = p == null ? null : uuidOrNull(p.uuid());
                if (to == null && p != null) {
                    // sin UUID en la web: el dueño que llevan los propios objetos
                    for (TFSearch.Found f : foundOf(p)) if (!f.ownerUuid().isEmpty()) to = uuidOrNull(f.ownerUuid());
                }
                if (to == null) {
                    TFPadNet.notice(player, "No se sabe de quién es (sin UUID).");
                    return null;
                }
                int[] r = TFSearch.recover(server, new TFSearch.Crit(a[0], "", all || a.length < 2 ? "" : a[1]), to);
                TFPadNet.notice(player, "Recuperados " + r[0] + " (ya los tiene su dueño)" + (r[1] > 0 ? "; " + r[1] + " se le quitarán al entrar al que los tiene" : "")
                        + (r[2] > 0 ? "; " + r[2] + " sitios sin poder (otro mundo o ya no estaba)" : "") + ".");
                scan(server);
                return null;
            }
            return null;
        }
    }

    static TFSearch.Found nth(String order, String item, int n) {
        TFSearch.Scan s = TFSearch.last();
        if (s == null) return null;
        List<TFSearch.Found> copies = new ArrayList<>();
        for (TFSearch.Found f : s.found()) if (f.order().equals(order) && f.item().equals(item)) copies.add(f);
        return n >= 0 && n < copies.size() ? copies.get(n) : null;
    }

    static ServerLevel levelOf(MinecraftServer server, String dim) {
        for (ServerLevel l : server.getAllLevels()) if (l.dimension().location().toString().equals(dim)) return l;
        return null;
    }

    static UUID uuidOrNull(String s) {
        try {
            return s == null || s.isEmpty() ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
