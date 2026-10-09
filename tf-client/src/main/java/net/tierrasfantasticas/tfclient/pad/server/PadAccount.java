package net.tierrasfantasticas.tfclient.pad.server;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.items.TFItems;
import net.tierrasfantasticas.tfclient.jobs.TFJobs;
import net.tierrasfantasticas.tfclient.market.TFMarket;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.server.TFBridge;
import net.tierrasfantasticas.tfclient.server.TFRanks;

/**
 * Monedero (saldo, lo que cobras de tu oficio y del GTS, mandar monedas a otro jugador y atajos a Tienda, GTS y
 * Oficios) y Mi rango (tu rango, lo que trae y los demás rangos con sus ventajas, como en la web).
 */
public final class PadAccount {
    public static final PadServer.App WALLET = new Wallet();
    public static final PadServer.App RANK = new Rank();

    private static final int TEXT = 0x18265C;

    private PadAccount() {}

    private static ItemStack coin() {
        return new ItemStack(TFItems.COIN.get());
    }

    // ---------------------------------------------------------------------------------------------------------------

    private record Transfer(String name, java.util.UUID uuid, long amount) {}

    private static final class Wallet implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            PadView.Builder b = PadView.of("monedero");
            var balance = TFEconomy.balance(player.server, player.getUUID());
            b.row(new PadView.Row(coin(), "Tu saldo", TEXT, List.of("Oficios, misiones, cazas, tienda y GTS."), -1,
                    balance.isPresent() ? PadView.money(balance.getAsLong()) : "—", null, null));
            double pending = TFJobs.pendingCoins(player.getUUID());
            if (pending >= 1) {
                b.row(new PadView.Row(new ItemStack(Items.CLOCK), "Por cobrar del oficio", TEXT, List.of("Llega en el próximo pago."), -1,
                        "+" + PadView.money((long) Math.floor(pending)), null, null));
            }
            int selling = TFMarket.listingsOf(player.getUUID()).size();
            long value = TFMarket.listingsOf(player.getUUID()).stream().mapToLong(TFMarket.Listing::price).sum();
            if (selling > 0) {
                b.row(new PadView.Row(new ItemStack(Items.EMERALD), "A la venta en el GTS", TEXT,
                        List.of(selling + (selling == 1 ? " cosa." : " cosas.") + " Cobras al momento cuando se venden."), -1,
                        PadView.money(value), PadView.Btn.of("VER", "§open:gts", PadView.BLUE), null));
            }
            Transfer t = PadServer.get(player, "monedero.envio", null);
            if (t != null) {
                b.row(new PadView.Row(new ItemStack(Items.PAPER), "¿Mandar " + TFEconomy.format(t.amount) + " a " + t.name + "?", 0xC27A10,
                        List.of("No se puede deshacer."), -1, "", PadView.Btn.of("MANDAR", "mandar", PadView.GREEN),
                        PadView.Btn.of("NO", "cancelar", PadView.RED)).selected(true));
            }
            b.row(new PadView.Row(new ItemStack(Items.CHEST), "Tienda", TEXT, List.of("Compra y vende con el servidor."), -1, "",
                    PadView.Btn.of("ABRIR", "§open:tienda", PadView.BLUE), null).clickable("§open:tienda"));
            b.row(new PadView.Row(new ItemStack(Items.IRON_PICKAXE), "Oficios", TEXT, List.of("Gana monedas trabajando."), -1, "",
                    PadView.Btn.of("ABRIR", "§open:oficios", PadView.BLUE), null).clickable("§open:oficios"));
            b.input("mandar", "MANDAR: JUGADOR CANTIDAD", 32, "PREPARAR");
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            switch (action) {
                case "cancelar" -> PadServer.put(player, "monedero.envio", null);
                case "mandar" -> {
                    if (!text.isBlank()) {
                        prepare(player, text.trim());
                        return null;
                    }
                    Transfer t = PadServer.get(player, "monedero.envio", null);
                    PadServer.put(player, "monedero.envio", null);
                    if (t == null) return null;
                    if (!TFEconomy.take(player, t.amount)) {
                        TFPadNet.notice(player, "No tienes " + TFEconomy.format(t.amount) + ".");
                        return null;
                    }
                    if (!TFEconomy.give(player.server, t.uuid, t.name, t.amount)) {
                        TFEconomy.give(player.server, player.getUUID(), player.getGameProfile().getName(), t.amount);
                        TFPadNet.notice(player, "No se pudo mandar. Te devolvimos las monedas.");
                        return null;
                    }
                    TFPadNet.notice(player, "Mandaste " + TFEconomy.format(t.amount) + " a " + t.name + ".");
                    TFPadNet.sendState(player);
                    ServerPlayer other = player.server.getPlayerList().getPlayer(t.uuid);
                    if (other != null) {
                        TFPadNet.notice(other, player.getGameProfile().getName() + " te mandó " + TFEconomy.format(t.amount) + ".");
                        TFPadNet.sendState(other);
                    }
                }
                default -> {
                }
            }
            return null;
        }

        private static void prepare(ServerPlayer player, String text) {
            if (!net.tierrasfantasticas.tfclient.pad.PadConfig.on("monedero.enviar")) {
                TFPadNet.notice(player, "Envíos desactivados.");
                return;
            }
            String[] parts = text.split("\\s+");
            if (parts.length != 2) {
                TFPadNet.notice(player, "Jugador y cantidad: Steve 500");
                return;
            }
            long amount = TFMarket.parsePrice(parts[1]);
            if (amount <= 0) {
                TFPadNet.notice(player, "Cantidad no válida (500, 5k).");
                return;
            }
            long max = net.tierrasfantasticas.tfclient.pad.PadConfig.get("monedero.maximo");
            if (amount > max) {
                TFPadNet.notice(player, "Como mucho " + TFEconomy.format(max) + " por envío.");
                return;
            }
            Optional<GameProfile> profile = player.server.getProfileCache() == null ? Optional.empty() : player.server.getProfileCache().get(parts[0]);
            ServerPlayer online = player.server.getPlayerList().getPlayerByName(parts[0]);
            GameProfile target = online != null ? online.getGameProfile() : profile.orElse(null);
            if (target == null || target.getId() == null) {
                TFPadNet.notice(player, "No conozco a «" + parts[0] + "»: tiene que haber entrado alguna vez.");
                return;
            }
            if (target.getId().equals(player.getUUID())) {
                TFPadNet.notice(player, "No puedes mandarte monedas a ti mismo.");
                return;
            }
            var balance = TFEconomy.balance(player.server, player.getUUID());
            if (balance.isPresent() && balance.getAsLong() < amount) {
                TFPadNet.notice(player, "Solo tienes " + TFEconomy.format(balance.getAsLong()) + ".");
                return;
            }
            PadServer.put(player, "monedero.envio", new Transfer(target.getName(), target.getId(), amount));
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static final class Rank implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            TFRanks.Rank mine = TFRanks.full(TFRanks.of(player.getUUID()));
            List<TFRanks.Rank> all = new ArrayList<>(TFRanks.list());
            if (tab.startsWith("r:")) {
                for (TFRanks.Rank r : all) if (r.id().equals(tab.substring(2))) return detail(player, r, mine);
            }
            PadView.Builder b = PadView.of("rango");
            b.header(mine == null ? "Sin rango. Tienda: " + TFBridge.storeHost()
                    : "Tu rango: " + mine.name() + ".");
            for (TFRanks.Rank r : all) {
                boolean on = mine != null && mine.id().equals(r.id());
                List<String> lines = new ArrayList<>();
                if (!r.perks().isEmpty()) lines.add(r.perks().get(0));
                if (r.homes() > 0) lines.add(r.homes() + " hogares");
                b.row(new PadView.Row(icon(r), r.name(), r.hex() >= 0 ? r.hex() : TEXT, lines, -1, on ? "TU RANGO" : "", null, null)
                        .clickable("tab:r:" + r.id()).selected(on));
            }
            if (all.isEmpty()) b.empty("Sin rangos.");
            return b.build();
        }

        private static PadView detail(ServerPlayer player, TFRanks.Rank r, TFRanks.Rank mine) {
            boolean on = mine != null && mine.id().equals(r.id());
            PadView.Builder b = PadView.of("rango");
            b.row(new PadView.Row(icon(r), r.name(), r.hex() >= 0 ? r.hex() : TEXT,
                    List.of(on ? "Es tu rango." : "Se consigue en " + TFBridge.storeHost() + "."), -1, on ? "TU RANGO" : "", null, null).selected(on));
            List<String> perks = new ArrayList<>(r.perks());
            if (r.homes() > 0 && perks.stream().noneMatch(p -> p.toLowerCase().contains("hogar"))) perks.add(r.homes() + " hogares.");
            if (!perks.isEmpty()) {
                List<String> lines = new ArrayList<>();
                for (String p : perks) lines.add("· " + p);
                b.text("Lo que trae", 0xC27A10, lines);
            }
            b.footer(PadView.Btn.of("ATRÁS", "tab:", PadView.BLUE));
            return b.build();
        }

        private static ItemStack icon(TFRanks.Rank r) {
            return new ItemStack(switch (Math.max(0, Math.min(6, r.tier()))) {
                case 0, 1 -> Items.IRON_INGOT;
                case 2 -> Items.GOLD_INGOT;
                case 3 -> Items.EMERALD;
                case 4 -> Items.DIAMOND;
                case 5 -> Items.NETHERITE_INGOT;
                default -> Items.NETHER_STAR;
            });
        }
    }
}
