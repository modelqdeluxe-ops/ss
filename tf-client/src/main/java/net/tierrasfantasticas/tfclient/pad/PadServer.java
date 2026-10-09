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
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.pad.server.PadAccount;
import net.tierrasfantasticas.tfclient.pad.server.PadAdmin;
import net.tierrasfantasticas.tfclient.pad.server.PadClaims;
import net.tierrasfantasticas.tfclient.pad.server.PadClans;
import net.tierrasfantasticas.tfclient.pad.server.PadHelp;
import net.tierrasfantasticas.tfclient.pad.server.PadHomes;
import net.tierrasfantasticas.tfclient.pad.server.PadJobs;
import net.tierrasfantasticas.tfclient.pad.server.PadKits;
import net.tierrasfantasticas.tfclient.pad.server.PadMarket;
import net.tierrasfantasticas.tfclient.pad.server.PadMissions;
import net.tierrasfantasticas.tfclient.pad.server.PadPlayers;
import net.tierrasfantasticas.tfclient.pad.server.PadRanking;
import net.tierrasfantasticas.tfclient.pad.server.PadShop;
import net.tierrasfantasticas.tfclient.pad.server.PadStats;
import net.tierrasfantasticas.tfclient.pad.server.PadTitles;
import net.tierrasfantasticas.tfclient.pad.server.PadTravel;
import net.tierrasfantasticas.tfclient.pad.server.PadWardrobe;

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
        APPS.put("oficios", PadJobs.APP);
        APPS.put("tienda", PadShop.APP);
        APPS.put("gts", PadMarket.APP);
        APPS.put("hogares", PadHomes.APP);
        APPS.put("armario", PadWardrobe.WARDROBE);
        APPS.put("efectos", PadWardrobe.EFFECTS);
        APPS.put("monedero", PadAccount.WALLET);
        APPS.put("rango", PadAccount.RANK);
        APPS.put("protecciones", PadClaims.APP);
        APPS.put("misiones", PadMissions.MISSIONS);
        APPS.put("cazas", PadMissions.HUNTS);
        APPS.put("kits", PadKits.APP);
        APPS.put("viajes", PadTravel.TRAVEL);
        APPS.put("explorar", PadTravel.EXPLORE);
        APPS.put("clanes", PadClans.APP);
        APPS.put("jugadores", PadPlayers.APP);
        APPS.put("ranking", PadRanking.APP);
        // pad de administrador (los a_* solo los abre el staff)
        APPS.put("a_apps", PadAdmin.APPS);
        APPS.put("a_ajustes", PadAdmin.SETTINGS);
        APPS.put("a_tienda", PadAdmin.SHOP);
        APPS.put("a_kits", PadAdmin.KITS);
        APPS.put("a_viajes", PadAdmin.TRAVEL);
        APPS.put("a_gts", PadAdmin.MARKET);
        APPS.put("a_comunidad", PadAdmin.COMMUNITY);
        APPS.put("a_oficios", PadAdmin.JOBS);
        STORES.add(PadStats.STORE);
        STORES.add(PadMissions.STORE);
        STORES.add(PadKits.STORE);
        STORES.add(PadTravel.STORE);
        STORES.add(PadHomes.STORE);
        STORES.add(net.tierrasfantasticas.tfclient.pad.server.PadPlayers.STORE);
        STORES.add(PadClans.STORE);
        STORES.add(PadTitles.STORE);
        STORES.add(PadHelp.STORE);
        STORES.add(PadCommunityServer.STORE);
    }

    private PadServer() {}

    public static MinecraftServer server() {
        return server;
    }

    static void open(ServerPlayer player, String app, String tab) {
        App a = APPS.get(app);
        if (a == null || !allowed(player) || !usable(player, app)) return;
        SESSIONS.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).keySet().removeIf(k -> k.startsWith(app + "."));
        send(player, a, tab == null ? "" : tab);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Lo que cada jugador tiene a medias en una app (la cantidad a comprar, el objeto elegido para vender, «¿seguro?»…)
    // ---------------------------------------------------------------------------------------------------------------

    private static final Map<UUID, Map<String, Object>> SESSIONS = new HashMap<>();

    @SuppressWarnings("unchecked")
    public static <T> T get(ServerPlayer player, String key, T fallback) {
        Map<String, Object> m = SESSIONS.get(player.getUUID());
        Object v = m == null ? null : m.get(key);
        return v == null ? fallback : (T) v;
    }

    public static void put(ServerPlayer player, String key, Object value) {
        Map<String, Object> m = SESSIONS.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        if (value == null) m.remove(key);
        else m.put(key, value);
    }

    /** Doble clic para lo que no tiene vuelta atrás: el primero pide confirmar (6 s), el segundo devuelve true. */
    public static boolean confirm(ServerPlayer player, String key) {
        long now = System.currentTimeMillis();
        Long at = get(player, "§confirm:" + key, null);
        if (at != null && now - at < 6000) {
            put(player, "§confirm:" + key, null);
            return true;
        }
        SESSIONS.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).keySet().removeIf(k -> k.startsWith("§confirm:"));
        put(player, "§confirm:" + key, now);
        return false;
    }

    /** ¿Está esperando el segundo clic de esta acción? (para pintar el botón como «¿SEGURO?»). */
    public static boolean confirming(ServerPlayer player, String key) {
        Long at = get(player, "§confirm:" + key, null);
        return at != null && System.currentTimeMillis() - at < 6000;
    }

    static void action(ServerPlayer player, String app, String tab, String action, String text) {
        App a = APPS.get(app);
        if (a == null || !allowed(player) || !usable(player, app)) return;
        if (action.startsWith("tab:")) {
            send(player, a, action.substring(4));
            return;
        }
        String next = null;
        try {
            next = a.action(player, tab, action, text == null ? "" : text);
        } catch (Exception e) {
            TFClient.LOGGER.error("TF Pad: la app {} falló con la acción {}", app, action, e);
            TFPadNet.notice(player, "Error. Si se repite, avisa al staff.");
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

    /** Las apps del pad de administrador («a_…») son del staff; las demás, si el staff no las apagó. */
    static boolean usable(ServerPlayer player, String app) {
        if (app.startsWith("a_")) return isAdmin(player);
        if (!PadConfig.appOn(app)) {
            TFPadNet.notice(player, "App desactivada.");
            return false;
        }
        return true;
    }

    /** Staff que puede usar el pad de administrador (nivel de permisos 3, como /tf web). */
    public static boolean isAdmin(ServerPlayer player) {
        return player.hasPermissions(3);
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
        PadConfig.load();
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
        SESSIONS.clear();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        RATE.remove(event.getEntity().getUUID());
        SESSIONS.remove(event.getEntity().getUUID());
        PadCommunityServer.logout(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null) return;
        if (++ticks % 200 != 0) return;
        PadCommunityServer.dropStaleUploads();
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
