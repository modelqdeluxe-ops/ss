package net.tierrasfantasticas.tfclient.pad.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.tierrasfantasticas.tfclient.jobs.TFJobsConfig;
import net.tierrasfantasticas.tfclient.jobs.TFJobsData;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.server.TFRanks;

/**
 * Jugadores: los conectados (con su rango, clan y título) y la ficha de cada uno: oficios, misiones, cazas, fotos,
 * likes y exploraciones. Como el «escáner de jugadores» de otros servidores.
 */
public final class PadPlayers {
    public static final PadServer.App APP = new App();

    private PadPlayers() {}

    static final class App implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            if (tab.startsWith("ficha:")) {
                try {
                    return profile(player, UUID.fromString(tab.substring(6)));
                } catch (IllegalArgumentException ignored) {
                    // pestaña rara: a la lista
                }
            }
            List<ServerPlayer> online = new ArrayList<>(player.getServer().getPlayerList().getPlayers());
            online.sort(Comparator.comparing((ServerPlayer p) -> !p.getUUID().equals(player.getUUID()))
                    .thenComparing(p -> p.getGameProfile().getName(), String.CASE_INSENSITIVE_ORDER));
            PadView.Builder b = PadView.of("jugadores").header(online.size() + (online.size() == 1 ? " jugador conectado." : " jugadores conectados."));
            for (ServerPlayer p : online) {
                String name = p.getGameProfile().getName();
                List<String> lines = new ArrayList<>();
                TFRanks.Rank rank = TFRanks.of(p.getUUID());
                String clan = PadClans.clanNameOf(p.getUUID());
                String title = PadTitles.equippedName(p.getUUID());
                StringBuilder line = new StringBuilder(rank == null ? "Sin rango" : rank.name());
                if (!clan.isEmpty()) line.append(" · ").append(clan);
                if (!title.isEmpty()) line.append(" · «").append(title).append("»");
                lines.add(line.toString());
                boolean me = p.getUUID().equals(player.getUUID());
                b.row(new PadView.Row(PadClans.head(p.getUUID(), name), me ? name + " (tú)" : name, 0x18265C, lines, -1, "",
                        PadView.Btn.of("FICHA", "ficha:" + p.getUUID(), PadView.BLUE), null));
            }
            return b.build();
        }

        private PadView profile(ServerPlayer viewer, UUID uuid) {
            String name = PadStats.nameOf(uuid);
            ServerPlayer online = viewer.getServer().getPlayerList().getPlayer(uuid);
            if (online != null) name = online.getGameProfile().getName();
            TFRanks.Rank rank = TFRanks.of(uuid);
            PadView.Builder b = PadView.of("jugadores").selected("ficha:" + uuid)
                    .header(name + (online != null ? " · conectado" : " · desconectado"));
            b.row(info(PadClans.head(uuid, name), "Rango", rank == null ? "Sin rango" : rank.name()));
            String clan = PadClans.clanNameOf(uuid);
            b.row(info(new ItemStack(Items.WHITE_BANNER), "Clan", clan.isEmpty() ? "Sin clan" : clan));
            String title = PadTitles.equippedName(uuid);
            b.row(info(new ItemStack(Items.NAME_TAG), "Título", title.isEmpty() ? "Ninguno" : "«" + title + "»"));
            // los tres oficios con más nivel
            var jobs = TFJobsData.get(viewer.getServer()).player(uuid).jobs;
            List<Map.Entry<String, TFJobsData.JobProgress>> top = new ArrayList<>(jobs.entrySet());
            top.sort(Comparator.comparingInt((Map.Entry<String, TFJobsData.JobProgress> e) -> e.getValue().level).reversed());
            StringBuilder js = new StringBuilder();
            for (int i = 0; i < Math.min(3, top.size()); i++) {
                TFJobsConfig.Job job = TFJobsConfig.job(top.get(i).getKey());
                if (js.length() > 0) js.append(" · ");
                js.append(job == null ? top.get(i).getKey() : job.name()).append(" ").append(top.get(i).getValue().level);
            }
            b.row(info(new ItemStack(Items.IRON_PICKAXE), "Oficios", js.length() == 0 ? "Aún sin oficio" : js.toString()));
            b.row(info(new ItemStack(Items.WRITABLE_BOOK), "Misiones y cazas",
                    PadStats.get(uuid, PadStats.MISSIONS) + " misiones · " + PadStats.get(uuid, PadStats.HUNTS) + " cazas"));
            b.row(info(new ItemStack(Items.PAINTING), "Comunidad",
                    PadStats.get(uuid, PadStats.PHOTOS) + " fotos · " + PadStats.get(uuid, PadStats.LIKES) + " likes recibidos"));
            b.row(info(new ItemStack(Items.SPYGLASS), "Aventura",
                    PadStats.get(uuid, PadStats.EXPLORES) + " exploraciones · " + PadStats.get(uuid, PadStats.TRAVELS) + " viajes"));
            b.footer(PadView.Btn.of("VOLVER", "volver", PadView.BLUE));
            return b.build();
        }

        private static PadView.Row info(ItemStack icon, String title, String value) {
            return new PadView.Row(icon, title, 0x4A6694, List.of(value), -1, "", null, null);
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            if (action.startsWith("ficha:")) return action;
            if (action.equals("volver")) return "";
            return null;
        }
    }
}
