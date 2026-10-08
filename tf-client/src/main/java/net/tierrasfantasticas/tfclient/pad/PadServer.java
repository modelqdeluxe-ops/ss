package net.tierrasfantasticas.tfclient.pad;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.pad.server.PadClans;
import net.tierrasfantasticas.tfclient.pad.server.PadHelp;
import net.tierrasfantasticas.tfclient.pad.server.PadKits;
import net.tierrasfantasticas.tfclient.pad.server.PadMissions;
import net.tierrasfantasticas.tfclient.pad.server.PadPlayers;
import net.tierrasfantasticas.tfclient.pad.server.PadRanking;
import net.tierrasfantasticas.tfclient.pad.server.PadStats;
import net.tierrasfantasticas.tfclient.pad.server.PadTitles;
import net.tierrasfantasticas.tfclient.pad.server.PadTravel;

/**
 * Las apps del pad que viven en el servidor: cada una sabe dibujarse (PadView) y responder a sus botones. Al pulsar
 * un botón, la app hace lo suyo y se le manda al jugador la vista nueva. También carga y guarda los datos de todas.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class PadServer {
    /** Una app del servidor. */
    public interface App {
        PadView view(ServerPlayer player, String tab);

        /** Hace lo del botón. Devuelve la pestaña que hay que dibujar después (null = la misma). */
        default String action(ServerPlayer player, String tab, String action, String text) {
            return null;
        }
    }

    /** Datos que se guardan en el mundo. */
    public interface Store {
        void load(MinecraftServer server);

        void save(MinecraftServer server);

        boolean dirty();
    }

    private static final Map<String, App> APPS = new LinkedHashMap<>();
    private static final List<Store> STORES = new ArrayList<>();
    private static final Map<UUID, long[]> RATE = new HashMap<>();
    private static MinecraftServer server;
    private static int ticks;

    static {
        APPS.put("misiones", PadMissions.MISSIONS);
        APPS.put("cazas", PadMissions.HUNTS);
        APPS.put("kits", PadKits.APP);
        APPS.put("viajes", PadTravel.TRAVEL);
        APPS.put("explorar", PadTravel.EXPLORE);
        APPS.put("clanes", PadClans.APP);
        APPS.put("titulos", PadTitles.APP);
        APPS.put("jugadores", PadPlayers.APP);
        APPS.put("ranking", PadRanking.APP);
        APPS.put("ayuda", PadHelp.APP);
        STORES.add(PadStats.STORE);
        STORES.add(PadMissions.STORE);
        STORES.add(PadKits.STORE);
        STORES.add(PadTravel.STORE);
        STORES.add(PadClans.STORE);
        STORES.add(PadTitles.STORE);
        STORES.add(PadHelp.STORE);
        STORES.add(PadCommunityServer.STORE);
    }

    private PadServer() {}

    public static MinecraftServer server() {
        return server;
    }

    static void open(ServerPlayer player, String app) {
        App a = APPS.get(app);
        if (a != null) send(player, a, "");
    }

    static void action(ServerPlayer player, String app, String tab, String action, String text) {
        App a = APPS.get(app);
        if (a == null || !allowed(player)) return;
        if (action.startsWith("tab:")) {
            send(player, a, action.substring(4));
            return;
        }
        String next = null;
        try {
            next = a.action(player, tab, action, text == null ? "" : text);
        } catch (Exception e) {
            TFClient.LOGGER.error("TF Pad: la app {} falló con la acción {}", app, action, e);
            TFPadNet.notice(player, "Algo salió mal. Vuelve a probar en un momento.");
        }
        if (!player.hasDisconnected()) send(player, a, next == null ? tab : next);
    }

    /** Vuelve a mandar la vista de una app (por ejemplo, cuando cambia algo mientras la tiene abierta). */
    public static void refresh(ServerPlayer player, String app, String tab) {
        App a = APPS.get(app);
        if (a != null) send(player, a, tab);
    }

    private static void send(ServerPlayer player, App a, String tab) {
        try {
            TFPadNet.sendView(player, a.view(player, tab));
        } catch (Exception e) {
            TFClient.LOGGER.error("TF Pad: no se pudo dibujar una app", e);
        }
    }

    /** Como mucho 12 pulsaciones por segundo y jugador (botones repetidos o clientes raros). */
    private static boolean allowed(ServerPlayer player) {
        long now = System.currentTimeMillis();
        long[] r = RATE.computeIfAbsent(player.getUUID(), k -> new long[] {now, 0});
        if (now - r[0] > 1000) {
            r[0] = now;
            r[1] = 0;
        }
        return ++r[1] <= 12;
    }

    // ---------------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        server = event.getServer();
        for (Store s : STORES) {
            try {
                s.load(server);
            } catch (Exception e) {
                TFClient.LOGGER.error("TF Pad: no se pudieron cargar unos datos", e);
            }
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        for (Store s : STORES) {
            try {
                s.save(event.getServer());
            } catch (Exception e) {
                TFClient.LOGGER.error("TF Pad: no se pudieron guardar unos datos", e);
            }
        }
        server = null;
        RATE.clear();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null) return;
        if (++ticks % 200 != 0) return;
        for (Store s : STORES) {
            if (!s.dirty()) continue;
            try {
                s.save(server);
            } catch (Exception e) {
                TFClient.LOGGER.error("TF Pad: no se pudieron guardar unos datos", e);
            }
        }
    }
}
