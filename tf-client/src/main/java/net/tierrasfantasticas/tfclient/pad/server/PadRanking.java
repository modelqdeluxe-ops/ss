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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.economy.TFWallet;
import net.tierrasfantasticas.tfclient.jobs.TFJobsConfig;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.server.TFBots;

/** Ranking: los 10 primeros en misiones, cazas, likes, exploraciones y (con las monedas del TF Client) monedas. */
public final class PadRanking {
    public static final PadServer.App APP = new App();
    /** Colores del podio: oro, plata y bronce. */
    private static final int[] PODIUM = {0xF6B628, 0xA9B8CC, 0xD08A4E};

    private PadRanking() {}

    static final class App implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            boolean coins = TFEconomy.mode() == TFEconomy.Mode.TF;
            String t = tab.isEmpty() ? "misiones" : tab;
            PadView.Builder b = PadView.of("ranking").tab("misiones", "MISIONES").tab("cazas", "CAZAS").tab("likes", "LIKES")
                    .tab("exploraciones", "EXPLORAR");
            if (coins) b.tab("monedas", "MONEDAS");
            b.selected(t).empty("Sin datos.");
            List<Map.Entry<UUID, Long>> top;
            String unit;
            switch (t) {
                case "cazas" -> {
                    top = PadStats.top(PadStats.HUNTS, 10);
                    unit = " cazas";
                }
                case "likes" -> {
                    top = PadStats.top(PadStats.LIKES, 10);
                    unit = " likes";
                }
                case "exploraciones" -> {
                    top = PadStats.top(PadStats.EXPLORES, 10);
                    unit = " exploraciones";
                }
                case "monedas" -> {
                    top = coins ? TFWallet.get(player.getServer()).top(10) : List.of();
                    unit = "";
                }
                default -> {
                    top = PadStats.top(PadStats.MISSIONS, 10);
                    unit = " misiones";
                }
            }
            // (1.3.117, el dueño: «los bots no tienen ranking; y no veo su nombre», un «2º ?») los bots que han entrado,
            // con lo poco que han hecho (TFBots.rankValue), y cada nombre de donde se sepa
            if (coins || !t.equals("monedas")) top = withBots(top, t);
            // 1.4: el podio: los tres primeros en tarjetas con su cabeza (oro, plata y bronce, el primero en medio), y
            // del cuarto en adelante, en filas
            int podium = Math.min(3, top.size());
            int[] order = podium == 3 ? new int[] {1, 0, 2} : podium == 2 ? new int[] {0, 1} : new int[] {0};
            for (int k = 0; k < podium && top.size() > 0; k++) {
                int i = order[k];
                UUID u = top.get(i).getKey();
                long v = top.get(i).getValue();
                boolean me = u.equals(player.getUUID());
                String name = name(player, u);
                String value = t.equals("monedas") ? PadView.money(v) : v + unit;
                b.card(PadClans.head(u, name), (i + 1) + "º " + name + (me ? " (tú)" : ""), PODIUM[i], value,
                        i == 0 ? PadView.TONE_GOLD : PadView.TONE_BLUE, -1, i == 0 ? "TOP 1" : "", "", me);
            }
            for (int i = podium; i < top.size(); i++) {
                UUID u = top.get(i).getKey();
                long v = top.get(i).getValue();
                ItemStack medal = new ItemStack(i == 0 ? Items.GOLD_INGOT : i == 1 ? Items.IRON_INGOT : i == 2 ? Items.COPPER_INGOT : Items.PAPER);
                String value = t.equals("monedas") ? PadView.moneyExact(v) : v + unit;
                boolean me = u.equals(player.getUUID());
                // el valor, en su etiqueta a la derecha (oro con la moneda si son monedas; azul si no)
                b.row(new PadView.Row(medal, (i + 1) + ". " + name(player, u) + (me ? " (tú)" : ""), me ? 0xC27A10 : 0x18265C,
                        List.of(), -1, value, null, null).selected(me));
            }
            return b.build();
        }
    }

    /** Los 10 primeros contando los bots que han entrado alguna vez; fuera los que no tienen nombre (no se sabe quién son). */
    private static List<Map.Entry<UUID, Long>> withBots(List<Map.Entry<UUID, Long>> real, String what) {
        List<Map.Entry<UUID, Long>> all = new ArrayList<>();
        for (Map.Entry<UUID, Long> e : real) if (TFBots.ofId(e.getKey()) == null) all.add(e);
        List<String> jobs = new ArrayList<>(TFJobsConfig.jobs.keySet());
        for (TFBots.Bot bot : TFBots.known()) {
            long v = TFBots.rankValue(bot, what, jobs, TFJobsConfig.maxJobs);
            if (v > 0) all.add(Map.entry(bot.id(), v));
        }
        all.removeIf(e -> name(null, e.getKey()) == null);
        all.sort(Comparator.comparingLong((Map.Entry<UUID, Long> e) -> e.getValue()).reversed());
        return all.size() > 10 ? all.subList(0, 10) : all;
    }

    /** El nombre: el del pad, el del jugador conectado, el del bot o el de la caché de perfiles del servidor (null si no). */
    private static String name(ServerPlayer viewer, UUID u) {
        String n = PadStats.nameOf(u);
        if (!"?".equals(n)) return n;
        TFBots.Bot bot = TFBots.ofId(u);
        if (bot != null) return bot.name();
        MinecraftServer server = viewer != null ? viewer.getServer() : PadServer.server();
        if (server == null) return null;
        ServerPlayer p = server.getPlayerList().getPlayer(u);
        if (p != null) return p.getGameProfile().getName();
        var cache = server.getProfileCache();
        var prof = cache == null ? java.util.Optional.<com.mojang.authlib.GameProfile>empty() : cache.get(u);
        return prof.map(com.mojang.authlib.GameProfile::getName).orElse(null);
    }
}
