/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.net.IDN;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.util.TFChat;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Puente entre el servidor de Minecraft y la web de Tierras Fantásticas (solo en servidores dedicados).
 *
 * <p>Cada pocos segundos manda a la web los jugadores conectados (nombre + UUID), los que han entrado alguna vez
 * (para que la tienda compruebe el nombre antes de cobrar), las entregas ya hechas, los cambios de rango del staff
 * y los de la tienda de monedas. Recibe las compras pendientes de los jugadores que están dentro y el rango de cada
 * uno para su nametag. Es el servidor el que llama a la web: no hace falta RCON ni abrir puertos.
 *
 * <p>Las entregas van al UUID del comprador, no a su nombre: los comandos traen {player} y {uuid} y se rellenan
 * aquí con el nombre que tiene ahora ese jugador. Cada entrega tiene un número; el puente guarda los que ya ejecutó
 * para no entregar nunca dos veces. Al entregar, todo el servidor se entera con un anuncio.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class TFBridge {
    /** Versión del protocolo: 2 = los comandos llegan con {player}/{uuid} y la entrega va por UUID. */
    private static final int PROTOCOL = 2;
    private static final int MAX_REMEMBERED = 5000;
    private static final int SEEN_PER_POLL = 500;
    private static final long ERROR_LOG_EVERY_MS = 5 * 60 * 1000L;

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    /** Las consultas a la web van en su propio hilo para no frenar el servidor. */
    private static ExecutorService HTTP = newHttp();
    /**
     * (1.3.139, el dueño: «a veces la clave del puente se desconecta; debe estar siempre conectada») Una consulta que no
     * vuelve (el DNS o la red colgados no tienen tiempo máximo) dejaba el puente parado hasta reiniciar: a los 90 s se
     * da por perdida, se usa otro hilo y lo que llevaba vuelve a la cola cuando acabe (si acaba).
     */
    private static final long STUCK_MS = 90_000L;
    private static long inFlightSince;
    private static int pollGen;

    private static ExecutorService newHttp() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "TF Bridge");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static MinecraftServer server;
    private static int ticks;
    private static boolean inFlight;
    private static boolean connected;
    private static long lastErrorLog;
    private static String lastError = "";
    /** Confirmaciones pendientes de mandar a la web. */
    private static final Deque<JsonObject> done = new ArrayDeque<>();
    /** Entregas ya ejecutadas (se guardan en disco). */
    private static final Set<Long> executed = new LinkedHashSet<>();
    /** Jugadores que han entrado alguna vez y aún no se han mandado a la web. */
    private static final Deque<JsonObject> seen = new ConcurrentLinkedDeque<>();
    /** Cambios de rango hechos con /tf web rango, pendientes de mandar. */
    private static final Deque<JsonObject> rankChanges = new ArrayDeque<>();
    /** Cambios de la tienda de monedas para la web, en orden. */
    private static final Deque<JsonObject> shopOps = new ArrayDeque<>();
    private static final int SHOP_PER_POLL = 50;
    /** Lo que los jugadores cambian desde el TF Pad (armario y VFX), para la web. */
    private static final Deque<JsonObject> padWardrobe = new ArrayDeque<>();
    private static final Deque<JsonObject> padVfx = new ArrayDeque<>();
    private static final Deque<JsonObject> padSkills = new ArrayDeque<>();
    /** Jugadores con el Armario del pad abierto hace poco: la web manda lo que tienen. uuid → hasta cuándo (ms). */
    private static final java.util.Map<java.util.UUID, Long> padWant = new java.util.HashMap<>();
    /** Efectos programados (fuegos artificiales escalonados). */
    private static final List<Scheduled> effects = new ArrayList<>();
    private static long clock;
    /** Dirección de la tienda que se muestra en el anuncio. */
    private static String storeHost = "";

    private record Scheduled(long at, Runnable action) {}

    private TFBridge() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        ticks = 0;
        inFlight = false;
        connected = false;
        try {
            TFServerConfig.load();
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Bridge: error al leer la config del puente", t);
        }
        try {
            loadExecuted();
        } catch (Throwable t) {
            // (1.3.139) antes esto dejaba el puente sin arrancar hasta reiniciar
            TFClient.LOGGER.error("TF Bridge: no se pudo leer la lista de entregas hechas (se sigue igual)", t);
        }
        if (TFServerConfig.enabled()) {
            TFClient.LOGGER.info("TF Bridge: conectando con {} cada {} s", TFServerConfig.url(), TFServerConfig.intervalSeconds());
            loadKnownPlayers(server);
        } else {
            TFClient.LOGGER.info("TF Bridge: desactivado (bridge.enabled=false en {})", TFServerConfig.file());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        server = null;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (server == null || !(event.getEntity() instanceof ServerPlayer player)) return;
        seen.addLast(seenEntry(player.getGameProfile().getName(), player.getUUID(), System.currentTimeMillis()));
        // Mientras llega la siguiente consulta, mantiene el rango que ya tenía.
        TFRanks.Rank rank = TFRanks.of(player.getUUID());
        if (rank != null) TFRanks.set(server, player, rank);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TFRanks.forget(event.getEntity().getUUID());
    }

    /** /tf web rango: la web guarda el nuevo rango del jugador (null = sin rango). */
    public static void queueRankChange(UUID uuid, String rankId) {
        JsonObject change = new JsonObject();
        change.addProperty("uuid", uuid.toString());
        if (rankId == null) change.add("rank", com.google.gson.JsonNull.INSTANCE);
        else change.addProperty("rank", rankId);
        rankChanges.addLast(change);
        soon();
    }

    /** /tf web tienda: un cambio de la tienda de monedas para la web. */
    public static void queueShop(JsonObject op) {
        shopOps.addLast(op);
        soon();
    }

    private static final java.util.regex.Pattern GIVE = java.util.regex.Pattern.compile("^give (\\S+) ([a-z0-9_.-]+:[a-z0-9_./-]+)(?: (\\d+))?$");

    /** Ejecuta un comando como la consola y devuelve los errores (vacío si fue bien). */
    public static List<String> run(MinecraftServer srv, String command) {
        // (1.3.117) LuckPerms, por la consola de Bukkit y comprobado (TFLuckPerms)
        if (TFLuckPerms.isLuckPerms(command)) return TFLuckPerms.run(srv, command);
        // (1.3.125, el dueño: «las piedras de protección de los rangos no están siendo entregadas») En Mohist el /give
        // puede ser el de Bukkit, que no conoce los objetos de los mods: «give <jugador> <mod:objeto> [n]» de un mod se
        // da aquí directamente (al inventario o, si no cabe, a sus pies).
        java.util.regex.Matcher give = GIVE.matcher(command.trim());
        // (1.3.129, el dueño: «no se reembolsaron los objetos extra del rango») también los de Minecraft: así todos llevan
        // la marca de su pedido y un reembolso los quita
        if (give.matches()) {
            ServerPlayer target = srv.getPlayerList().getPlayerByName(give.group(1));
            net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                    net.minecraft.resources.ResourceLocation.tryParse(give.group(2)));
            if (target != null && item != null && item != net.minecraft.world.item.Items.AIR) {
                int count = give.group(3) == null ? 1 : Math.max(1, Math.min(6400, Integer.parseInt(give.group(3))));
                while (count > 0) {
                    int n = Math.min(count, item.getMaxStackSize());
                    net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item, n);
                    net.tierrasfantasticas.tfclient.items.TFRevocations.stampAny(stack); // ligado a su pedido (reembolsos)
                    if (!target.getInventory().add(stack) || !stack.isEmpty()) target.drop(stack, false);
                    count -= n;
                }
                return new ArrayList<>();
            }
        }
        ErrorCollector collector = new ErrorCollector();
        try {
            CommandSourceStack source = srv.createCommandSourceStack().withSource(collector);
            srv.getCommands().performPrefixedCommand(source, command);
        } catch (Throwable t) {
            collector.errors.add(t.toString());
        }
        return collector.errors;
    }

    /** ¿Está LuckPerms? (su comando /lp está registrado en Minecraft o su API está cargada: plugin de Bukkit o mod). */
    public static boolean hasLuckPerms(MinecraftServer srv) {
        var root = srv.getCommands().getDispatcher().getRoot();
        return root.getChild("lp") != null || root.getChild("luckperms") != null || TFLuckPerms.present();
    }

    /** Una pieza del armario puesta (o quitada, con id null) desde el pad. */
    public static void padWardrobe(java.util.UUID uuid, String slot, String set, String id) {
        JsonObject o = new JsonObject();
        o.addProperty("uuid", uuid.toString());
        o.addProperty("slot", slot);
        if (id != null) {
            o.addProperty("set", set);
            o.addProperty("id", id);
        } else {
            o.add("id", com.google.gson.JsonNull.INSTANCE);
        }
        padWardrobe.addLast(o);
        soon();
    }

    /** Un efecto de kill o paquete de skills equipado (o quitado, con id null) desde el pad. */
    public static void padVfx(java.util.UUID uuid, String kind, String id) {
        JsonObject o = new JsonObject();
        o.addProperty("uuid", uuid.toString());
        o.addProperty("kind", kind);
        if (id != null) o.addProperty("id", id);
        else o.add("id", com.google.gson.JsonNull.INSTANCE);
        padVfx.addLast(o);
        soon();
    }

    /** La clase de skills activa elegida (o quitada, con null) desde el pad. */
    public static void padSkill(java.util.UUID uuid, String cls) {
        JsonObject o = new JsonObject();
        o.addProperty("uuid", uuid.toString());
        if (cls != null) o.addProperty("clase", cls);
        else o.add("clase", com.google.gson.JsonNull.INSTANCE);
        padSkills.addLast(o);
        soon();
    }

    /** Pide a la web lo que tiene este jugador en el armario (durante un minuto, en cada consulta). */
    public static void padWant(java.util.UUID uuid) {
        boolean fresh = padWant.put(uuid, System.currentTimeMillis() + 60_000) == null;
        if (fresh) soon();
    }

    /** La dirección de la tienda web (sin https://), para enseñarla. */
    public static String storeHost() {
        String host = storeHost.isEmpty() ? TFServerConfig.url().replaceAll("^https?://", "") : storeHost;
        return java.net.IDN.toUnicode(host.replaceAll("/.*$", ""));
    }

    /** ¿Hay conexión con la web? */
    public static boolean connected() {
        return connected && TFServerConfig.enabled();
    }

    /** Adelanta la siguiente consulta a la web (en un segundo). */
    private static void soon() {
        ticks = Math.max(ticks, TFServerConfig.intervalSeconds() * 20 - 20);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null) return;
        clock++;
        if (!effects.isEmpty()) runEffects();
        if (!TFServerConfig.enabled()) return;
        // La primera consulta, a los 5 segundos de arrancar; después, cada intervalo.
        if (++ticks < TFServerConfig.intervalSeconds() * 20 && !(ticks == 100 && !connected)) return;
        ticks = 0;
        if (inFlight && System.currentTimeMillis() - inFlightSince > STUCK_MS) {
            logError("la consulta a la web no volvió en " + STUCK_MS / 1000 + " s; se reintenta con otra");
            HTTP.shutdownNow();
            HTTP = newHttp();
            pollGen++;
            inFlight = false;
            connected = false;
        }
        if (inFlight) return;
        try {
            poll();
        } catch (Throwable t) {
            inFlight = false;
            logError("error preparando la consulta: " + t);
        }
    }

    private static void poll() {
        MinecraftServer srv = server;
        JsonObject body = new JsonObject();
        JsonArray players = new JsonArray();
        for (ServerPlayer player : srv.getPlayerList().getPlayers()) {
            JsonObject p = new JsonObject();
            p.addProperty("name", player.getGameProfile().getName());
            p.addProperty("uuid", player.getUUID().toString());
            // Saldo de monedas, para enseñarlo en la web junto al nombre (si la economía deja verlo)
            java.util.OptionalLong coins = net.tierrasfantasticas.tfclient.economy.TFEconomy.balance(srv, player.getUUID());
            if (coins.isPresent()) p.addProperty("coins", coins.getAsLong());
            players.add(p);
        }
        body.addProperty("protocol", PROTOCOL);
        body.add("players", players);
        body.addProperty("max", srv.getMaxPlayers());
        JsonArray acks = new JsonArray();
        List<JsonObject> sending = new ArrayList<>(done);
        done.clear();
        sending.forEach(acks::add);
        body.add("done", acks);

        List<JsonObject> sendingSeen = new ArrayList<>();
        for (JsonObject p; sendingSeen.size() < SEEN_PER_POLL && (p = seen.pollFirst()) != null; ) sendingSeen.add(p);
        List<JsonObject> sendingRanks = new ArrayList<>(rankChanges);
        rankChanges.clear();
        List<JsonObject> sendingShop = new ArrayList<>();
        for (JsonObject op; sendingShop.size() < SHOP_PER_POLL && (op = shopOps.pollFirst()) != null; ) sendingShop.add(op);
        body.add("seen", array(sendingSeen));
        body.add("ranks", array(sendingRanks));
        if (!sendingShop.isEmpty()) body.add("shop", array(sendingShop));
        List<JsonObject> sendingWardrobe = new ArrayList<>(padWardrobe);
        List<JsonObject> sendingVfx = new ArrayList<>(padVfx);
        List<JsonObject> sendingSkills = new ArrayList<>(padSkills);
        padWardrobe.clear();
        padVfx.clear();
        padSkills.clear();
        if (!sendingWardrobe.isEmpty() || !sendingVfx.isEmpty() || !sendingSkills.isEmpty()) {
            JsonObject pad = new JsonObject();
            pad.add("wardrobe", array(sendingWardrobe));
            pad.add("vfx", array(sendingVfx));
            pad.add("skills", array(sendingSkills));
            body.add("pad", pad);
        }
        long nowMs = System.currentTimeMillis();
        padWant.values().removeIf(until -> until < nowMs);
        if (!padWant.isEmpty()) {
            JsonArray want = new JsonArray();
            padWant.keySet().forEach(u -> want.add(u.toString()));
            body.add("padWant", want);
        }
        // (1.3.120) los jugadores reiniciados al empezar limpio (TFDataReset): la web también los reinicia
        JsonObject webReset = TFJson.read(TFJson.worldFile(srv, TFDataReset.WEB_RESET));
        if (webReset != null && webReset.has("uuids") && webReset.get("uuids").isJsonArray()) body.add("reset", webReset.getAsJsonArray("uuids"));
        // Si la consulta falla, todo vuelve a la cola para la siguiente.
        Runnable requeue = () -> {
            sending.forEach(done::addLast);
            sendingSeen.forEach(seen::addLast);
            sendingRanks.forEach(rankChanges::addLast);
            for (int i = sendingShop.size() - 1; i >= 0; i--) shopOps.addFirst(sendingShop.get(i));
            for (int i = sendingWardrobe.size() - 1; i >= 0; i--) padWardrobe.addFirst(sendingWardrobe.get(i));
            for (int i = sendingVfx.size() - 1; i >= 0; i--) padVfx.addFirst(sendingVfx.get(i));
            for (int i = sendingSkills.size() - 1; i >= 0; i--) padSkills.addFirst(sendingSkills.get(i));
        };

        String url = TFServerConfig.url() + "/bridge/poll";
        String secret = TFServerConfig.secret();
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);

        inFlight = true;
        inFlightSince = System.currentTimeMillis();
        int gen = ++pollGen;
        HTTP.execute(() -> {
            int status = -1;
            String text = null;
            Throwable error = null;
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + secret);
                connection.setRequestProperty("User-Agent", "TF-Client-Bridge/" + TFClient.VERSION);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(payload);
                }
                status = connection.getResponseCode();
                InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                if (in != null) {
                    try (in) {
                        text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
                connection.disconnect();
            } catch (Throwable t) {
                error = t;
            }
            int finalStatus = status;
            String finalText = text;
            Throwable finalError = error;
            srv.execute(() -> {
                if (gen != pollGen) {
                    // se dio por perdida: lo que llevaba vuelve a la cola (la siguiente lo manda otra vez)
                    requeue.run();
                    return;
                }
                inFlight = false;
                try {
                    handleResponse(srv, finalStatus, finalText, finalError, requeue);
                } catch (Throwable t) {
                    requeue.run();
                    logError("respuesta no válida de la web: " + t);
                }
            });
        });
    }

    private static JsonArray array(List<JsonObject> list) {
        JsonArray out = new JsonArray();
        list.forEach(out::add);
        return out;
    }

    private static void handleResponse(MinecraftServer srv, int status, String text, Throwable error, Runnable requeue) {
        if (error != null || status < 0) {
            requeue.run();
            connected = false;
            logError("no se pudo conectar con " + TFServerConfig.url() + " (" + (error == null ? "sin respuesta" : error) + ")");
            return;
        }
        if (status != 200) {
            requeue.run();
            connected = false;
            if (status == 401) {
                logError("la web rechaza la clave. Copia bridge.secret de " + TFServerConfig.file().getFileName()
                        + " en Cloudflare como Secret BRIDGE_SECRET");
            } else if (status == 503) {
                logError("la web aún no tiene la clave del puente: añade el Secret BRIDGE_SECRET en Cloudflare");
            } else {
                logError("la web respondió " + status);
            }
            return;
        }
        if (!connected) {
            connected = true;
            lastError = "";
            TFClient.LOGGER.info("TF Bridge: conectado con la web {}", TFServerConfig.url());
            // La tienda de monedas de la web es la del servidor: se manda entera al conectar.
            shopOps.clear();
            net.tierrasfantasticas.tfclient.shop.TFCoinShop.snapshot().forEach(shopOps::addLast);
            soon();
        }

        JsonObject json = JsonParser.parseString(text).getAsJsonObject();
        if (json.has("resetDone")) {
            try {
                Files.deleteIfExists(TFJson.worldFile(srv, TFDataReset.WEB_RESET));
                TFClient.LOGGER.warn("TF Bridge: la web reinició {} jugadores (empezar limpio)", json.get("resetDone").getAsInt());
            } catch (Exception e) {
                TFClient.LOGGER.warn("TF Bridge: no se pudo borrar {}: {}", TFDataReset.WEB_RESET, e.toString());
            }
        }
        // (1.3.127, el dueño: «el test no sirve, no me llegan los items ni el rango») si algo de lo de antes falla (efectos,
        // skills, armario…), las entregas se hacen igual: antes la excepción cortaba aquí y no llegaba ninguna
        try {
            if (json.has("store") && !json.get("store").isJsonNull()) storeHost = json.get("store").getAsString();
            if (json.has("rankList")) {
                TFRanks.setList(json.getAsJsonArray("rankList"));
                // Lo que trae cada rango (permisos de LuckPerms y hogares de EssentialsX), si cambió
                TFRankPerms.onRankList(srv);
            }
            if (json.has("roulette") && json.get("roulette").isJsonObject()) {
                net.tierrasfantasticas.tfclient.shop.TFRoulette.setConfig(json.getAsJsonObject("roulette"));
            }
            // VFX equipados en la web (efecto de kill y paquete de skills) de los conectados
            // (1.3.114) los efectos de kill que tiene cada conectado (se venden en la web)
            if (json.has("vfxOwned") && json.get("vfxOwned").isJsonArray()) {
                net.tierrasfantasticas.tfclient.vfx.VfxServer.applyOwned(!json.has("vfxFree") || json.get("vfxFree").getAsBoolean(),
                        json.getAsJsonArray("vfxOwned"));
            }
            if (json.has("vfx") && json.get("vfx").isJsonArray()) {
                net.tierrasfantasticas.tfclient.vfx.VfxServer.applyWeb(srv, json.getAsJsonArray("vfx"));
            }
            // Clase de skills de cada conectado (la compra en la web)
            if (json.has("skills") && json.get("skills").isJsonArray()) {
                net.tierrasfantasticas.tfclient.skills.SkillServer.applyWeb(srv, json.getAsJsonArray("skills"));
            }
            // Armario de la web (lo que se ve en cada hueco) de los conectados
            if (json.has("wardrobe") && json.get("wardrobe").isJsonArray()) {
                net.tierrasfantasticas.tfclient.items.TFWardrobe.applyWeb(srv, json.getAsJsonArray("wardrobe"));
            }
            // Lo que tienen en el armario los que lo abrieron en el pad
            if (json.has("padData") && json.get("padData").isJsonArray()) {
                net.tierrasfantasticas.tfclient.pad.server.PadWardrobe.receive(srv, json.getAsJsonArray("padData"));
            }
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Bridge: error al aplicar la respuesta de la web (las entregas siguen)", t);
        }
        JsonArray deliveries = json.has("deliveries") ? json.getAsJsonArray("deliveries") : new JsonArray();
        for (JsonElement element : deliveries) {
            // (1.3.127) una entrega que falla no impide las demás
            try {
                deliver(srv, element.getAsJsonObject());
            } catch (Throwable t) {
                TFClient.LOGGER.error("TF Bridge: no se pudo hacer la entrega {}", element, t);
            }
        }
        // (1.3.117) los rangos de los conectados, después de las entregas: la compra de un rango ya pone su grupo de
        // LuckPerms, y TFRanks.update solo arregla al que le falte (TFLuckPerms.ensure)
        if (json.has("ranks")) TFRanks.update(srv, json.getAsJsonArray("ranks"));
    }

    private static String text(JsonObject o, String key, String def) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
    }

    private static void deliver(MinecraftServer srv, JsonObject delivery) {
        long id = delivery.get("id").getAsLong();
        if (executed.contains(id)) {
            // Ya se ejecutó (la confirmación se perdió): solo volvemos a confirmarla.
            ack(id, true, null);
            return;
        }
        // Reembolso: se retira lo de ese pedido aunque el jugador no esté conectado.
        if ("revocacion".equals(text(delivery, "kind", ""))) {
            revoke(srv, id, delivery);
            return;
        }
        // Por UUID: aunque el jugador se cambie el nombre o alguien use uno parecido, llega al que pagó.
        String uuidText = text(delivery, "uuid", null);
        ServerPlayer player = null;
        if (uuidText != null) {
            try {
                player = srv.getPlayerList().getPlayer(UUID.fromString(uuidText));
            } catch (IllegalArgumentException ignored) {
                // UUID no válido: no se entrega.
            }
        } else {
            player = srv.getPlayerList().getPlayerByName(text(delivery, "player", ""));
        }
        if (player == null) return; // Salió justo ahora: la web la reenviará cuando vuelva.
        String name = player.getGameProfile().getName();
        String uuid = player.getUUID().toString();

        // Se apunta antes de ejecutar: si el servidor se cae a mitad, nunca se entrega dos veces.
        remember(id);
        // Pedidos pagados con monedas desde la web (ruleta o tienda de monedas): se cobran aquí.
        String kind = delivery.has("kind") ? text(delivery, "kind", "") : "";
        boolean coinSpin = kind.equals("ruleta-monedas") || kind.equals("tienda-monedas");
        List<String> errors = new ArrayList<>();
        net.tierrasfantasticas.tfclient.shop.TFRoulette.startCapture();
        // Lo que se entregue lleva la marca del pedido: si se reembolsa, se sabe qué quitar.
        net.tierrasfantasticas.tfclient.items.TFRevocations.setCurrentOrder(text(delivery, "order", null));
        try {
            for (JsonElement cmd : delivery.getAsJsonArray("commands")) {
                String command = cmd.getAsString().replace("{player}", name).replace("{uuid}", uuid);
                List<String> failed = run(srv, command);
                // Quitar un grupo de rango que no tenía no es un error de la entrega.
                if (!failed.isEmpty() && !command.contains(" parent remove ")) {
                    // Con monedas: a la web le llega el motivo tal cual («no tienes bastantes monedas»)
                    errors.add(coinSpin ? String.join("; ", failed) : command + " → " + String.join("; ", failed));
                    // (1.3.114) si no se pudo cobrar, no se da nada (lo de después del cobro iba gratis)
                    if (coinSpin) break;
                }
            }
        } finally {
            net.tierrasfantasticas.tfclient.items.TFRevocations.setCurrentOrder(null);
        }
        JsonArray prizes = net.tierrasfantasticas.tfclient.shop.TFRoulette.stopCapture();
        if (coinSpin) {
            if (errors.isEmpty()) ack(id, true, null, prizes);
            else ack(id, false, String.join(" | ", errors), null);
            return;
        }

        String product = text(delivery, "product", "tu compra");
        int quantity = delivery.has("quantity") ? delivery.get("quantity").getAsInt() : 1;
        TFRanks.Rank rank = delivery.has("rank") && delivery.get("rank").isJsonObject() ? TFRanks.Rank.parse(delivery.getAsJsonObject("rank")) : null;
        if (errors.isEmpty()) {
            TFClient.LOGGER.info("TF Bridge: entregado {}{} a {} ({}) (entrega {})", product, quantity > 1 ? " x" + quantity : "", name, uuid, id);
            ack(id, true, null);
        } else {
            TFClient.LOGGER.error("TF Bridge: fallo al entregar {} a {} (entrega {}): {}", product, name, id, errors);
            ack(id, false, String.join(" | ", errors));
        }
        // El rango comprado se ve al momento en su nametag (la web lo confirmará en la siguiente consulta).
        if (rank != null && (TFRanks.of(player.getUUID()) == null || TFRanks.of(player.getUUID()).tier() < rank.tier())) {
            TFRanks.set(srv, player, rank);
        }
        celebrate(srv, player, delivery, product, quantity, rank);
    }

    /** Retirada de una compra reembolsada: los comandos van por UUID (sirven con el jugador desconectado). */
    private static void revoke(MinecraftServer srv, long id, JsonObject delivery) {
        String uuid = text(delivery, "uuid", "");
        String name = text(delivery, "player", uuid);
        remember(id);
        List<String> errors = new ArrayList<>();
        for (JsonElement cmd : delivery.getAsJsonArray("commands")) {
            String command = cmd.getAsString().replace("{player}", name).replace("{uuid}", uuid);
            List<String> failed = run(srv, command);
            // Quitar o devolver un grupo de LuckPerms que ya estaba así no es un error.
            if (!failed.isEmpty() && !command.startsWith("lp ")) errors.add(command + " → " + String.join("; ", failed));
        }
        if (errors.isEmpty()) {
            TFClient.LOGGER.warn("TF Bridge: retirada la compra reembolsada {} de {} ({}) (entrega {})", text(delivery, "order", "?"), name, uuid, id);
            ack(id, true, null);
        } else {
            TFClient.LOGGER.error("TF Bridge: fallo al retirar la compra {} de {} (entrega {}): {}", text(delivery, "order", "?"), name, id, errors);
            ack(id, false, String.join(" | ", errors));
        }
    }

    // --- El anuncio: todo el servidor se entera de la compra ---

    private static int color(JsonObject delivery, TFRanks.Rank rank) {
        if (rank != null && rank.hex() >= 0) return rank.hex();
        String hex = text(delivery, "color", "#f4c95d");
        return hex.matches("#[0-9a-fA-F]{6}") ? Integer.parseInt(hex.substring(1), 16) : 0xF4C95D;
    }

    private static void celebrate(MinecraftServer srv, ServerPlayer buyer, JsonObject delivery, String product, int quantity, TFRanks.Rank rank) {
        String name = buyer.getGameProfile().getName();
        int rgb = color(delivery, rank);
        Style accent = Style.EMPTY.withColor(TextColor.fromRgb(rgb));
        String what = quantity > 1 ? product + " ×" + quantity : product;

        // Al comprador: una sola línea en el chat (sin título en pantalla). Si es una ruleta, con lo que le tocó.
        MutableComponent got = Component.literal(what).withStyle(accent);
        String prizes = prizeList(delivery);
        if (!prizes.isEmpty()) got.append(Component.literal(" (" + prizes + ")").withStyle(ChatFormatting.GRAY));
        TFChat.send(buyer, "Tienda", ChatFormatting.GOLD, "Recibiste ", got, ". ¡Gracias por tu compra!");
        buyer.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.7f, 1.0f);

        if (!TFServerConfig.broadcast()) return;

        // A los demás: también una línea («[Tienda] Steve compró Rango Mortal»), con clic a la tienda si se sabe.
        MutableComponent line = TFChat.line("Tienda", ChatFormatting.GOLD, TFChat.hl(name), " compró ",
                Component.literal(what).withStyle(accent), ".");
        if (!storeHost.isEmpty()) {
            line.withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://" + storeHost))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal(IDN.toUnicode(storeHost.replaceAll("/.*$", ""))).withStyle(ChatFormatting.GOLD))));
        }
        for (ServerPlayer player : srv.getPlayerList().getPlayers()) {
            if (player != buyer) player.sendSystemMessage(line);
        }
        srv.sendSystemMessage(line); // en la consola, como antes
        if (TFServerConfig.fireworks()) fireworks(buyer, rgb, rank != null ? 3 + Math.min(rank.tier(), 4) : 3);
    }

    /** Los premios de una ruleta pagada en la web (campo «prizes» de la entrega), en corto: «A, B, C y 2 más». */
    private static String prizeList(JsonObject delivery) {
        if (!delivery.has("prizes") || !delivery.get("prizes").isJsonArray()) return "";
        List<String> names = new ArrayList<>();
        for (JsonElement e : delivery.getAsJsonArray("prizes")) {
            if (e.isJsonPrimitive()) names.add(e.getAsString());
            else if (e.isJsonObject() && e.getAsJsonObject().has("name")) names.add(e.getAsJsonObject().get("name").getAsString());
        }
        if (names.size() <= 3) return String.join(", ", names);
        return String.join(", ", names.subList(0, 3)) + " y " + (names.size() - 3) + " más";
    }

    /** Fuegos artificiales del color de la compra alrededor del comprador, escalonados durante unos segundos. */
    private static void fireworks(ServerPlayer buyer, int rgb, int count) {
        for (int i = 0; i < count; i++) {
            final int n = i;
            effects.add(new Scheduled(clock + 1 + i * 12L, () -> {
                if (buyer.isRemoved() || buyer.hasDisconnected()) return;
                ServerLevel level = buyer.serverLevel();
                double angle = n * 2.4;
                double x = buyer.getX() + Math.cos(angle) * 2.5;
                double z = buyer.getZ() + Math.sin(angle) * 2.5;
                // Solo con cielo abierto: el cohete explota lejos de todos (si choca con un techo, haría daño).
                if (!level.canSeeSky(BlockPos.containing(x, buyer.getY() + 1, z))) return;
                FireworkRocketEntity rocket = new FireworkRocketEntity(level, x, buyer.getY() + 0.5, z, rocket(rgb, n));
                level.addFreshEntity(rocket);
            }));
        }
    }

    private static ItemStack rocket(int rgb, int n) {
        ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET);
        CompoundTag fireworks = stack.getOrCreateTagElement("Fireworks");
        fireworks.putByte("Flight", (byte) 1);
        CompoundTag explosion = new CompoundTag();
        // 0 bola pequeña, 1 bola grande, 2 estrella, 4 estallido.
        byte[] shapes = {1, 2, 4, 1, 2, 4, 1};
        explosion.putByte("Type", shapes[n % shapes.length]);
        explosion.putIntArray("Colors", new int[] {rgb, n % 2 == 0 ? 0xF8DD8E : 0xFFFFFF});
        explosion.putIntArray("FadeColors", new int[] {0xFFFFFF});
        explosion.putBoolean("Trail", true);
        explosion.putBoolean("Flicker", n % 2 == 1);
        ListTag explosions = new ListTag();
        explosions.add(explosion);
        fireworks.put("Explosions", explosions);
        return stack;
    }

    private static void runEffects() {
        List<Scheduled> due = new ArrayList<>();
        for (Iterator<Scheduled> it = effects.iterator(); it.hasNext(); ) {
            Scheduled s = it.next();
            if (s.at() <= clock) {
                due.add(s);
                it.remove();
            }
        }
        for (Scheduled s : due) {
            try {
                s.action().run();
            } catch (Throwable t) {
                TFClient.LOGGER.warn("TF Bridge: efecto de la compra falló: {}", t.toString());
            }
        }
    }

    // --- Jugadores que han entrado alguna vez: la tienda solo vende a nombres que el servidor conoce ---

    private static JsonObject seenEntry(String name, UUID uuid, long at) {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("uuid", uuid.toString());
        o.addProperty("at", at);
        return o;
    }

    /**
     * Lee usercache.json (nombre ↔ UUID) y se queda con los que tienen datos de jugador en el mundo: esos han entrado
     * de verdad (usercache también guarda nombres buscados con comandos). La fecha es la de su archivo de datos.
     */
    private static void loadKnownPlayers(MinecraftServer srv) {
        Path cache = Path.of("usercache.json");
        Path data = srv.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        HTTP.execute(() -> {
            if (!Files.exists(cache)) return;
            int count = 0;
            try (Reader reader = Files.newBufferedReader(cache, StandardCharsets.UTF_8)) {
                for (JsonElement element : JsonParser.parseReader(reader).getAsJsonArray()) {
                    JsonObject entry = element.getAsJsonObject();
                    String name = text(entry, "name", null);
                    String uuidText = text(entry, "uuid", null);
                    if (name == null || uuidText == null) continue;
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(uuidText);
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                    Path file = data.resolve(uuid + ".dat");
                    if (!Files.exists(file)) continue;
                    seen.addLast(seenEntry(name, uuid, Files.getLastModifiedTime(file).toMillis()));
                    count++;
                }
                TFClient.LOGGER.info("TF Bridge: {} jugadores conocidos para la tienda", count);
            } catch (Throwable t) {
                TFClient.LOGGER.warn("TF Bridge: no se pudo leer usercache.json: {}", t.toString());
            }
        });
    }

    private static void ack(long id, boolean ok, String error) {
        ack(id, ok, error, null);
    }

    /** Confirma una entrega a la web; prizes son los premios de la ruleta con monedas (la web los enseña). */
    private static void ack(long id, boolean ok, String error, JsonArray prizes) {
        JsonObject item = new JsonObject();
        item.addProperty("id", id);
        item.addProperty("ok", ok);
        if (error != null) item.addProperty("error", error.length() > 480 ? error.substring(0, 480) : error);
        if (prizes != null && !prizes.isEmpty()) item.add("prizes", prizes);
        done.addLast(item);
        // Confirmamos enseguida en vez de esperar al siguiente intervalo.
        soon();
    }

    private static void logError(String message) {
        long now = System.currentTimeMillis();
        if (!message.equals(lastError) || now - lastErrorLog > ERROR_LOG_EVERY_MS) {
            TFClient.LOGGER.warn("TF Bridge: {}", message);
            lastError = message;
            lastErrorLog = now;
        }
    }

    // --- Entregas ejecutadas, en disco ---

    private static Path executedFile() {
        return net.tierrasfantasticas.tfclient.util.TFConfigDir.file("puente-entregas.txt", "tfclient-bridge-entregas.txt");
    }

    private static void loadExecuted() throws IOException {
        executed.clear();
        Path file = executedFile();
        if (!Files.exists(file)) return;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            try {
                executed.add(Long.parseLong(line.trim()));
            } catch (NumberFormatException ignored) {
                // Línea vacía o no válida.
            }
        }
    }

    private static void remember(long id) {
        executed.add(id);
        try {
            if (executed.size() > MAX_REMEMBERED) {
                List<Long> keep = new ArrayList<>(executed).subList(executed.size() - MAX_REMEMBERED / 2, executed.size());
                executed.clear();
                executed.addAll(keep);
                List<String> lines = new ArrayList<>();
                keep.forEach(n -> lines.add(Long.toString(n)));
                Files.write(executedFile(), lines, StandardCharsets.UTF_8);
            } else {
                Files.writeString(executedFile(), id + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            TFClient.LOGGER.warn("TF Bridge: no se pudo guardar la entrega {}: {}", id, e.getMessage());
        }
    }

    /** Recoge los mensajes de error de un comando (los de éxito no se muestran). */
    private static final class ErrorCollector implements CommandSource {
        final List<String> errors = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component component) {
            errors.add(component.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return false;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }
}
