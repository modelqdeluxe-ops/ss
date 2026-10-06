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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
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

/**
 * Puente entre el servidor de Minecraft y la web de Tierras Fantásticas (solo en servidores dedicados).
 *
 * <p>Cada pocos segundos manda a la web los jugadores conectados (nombre + UUID), los que han entrado alguna vez
 * (para que la tienda compruebe el nombre antes de cobrar), las entregas ya hechas, los códigos de /tf vincular y
 * los cambios de rango del staff. Recibe las compras pendientes de los jugadores que están dentro y el rango de cada
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
    private static final long LINK_COOLDOWN_MS = 5000;
    private static final long ERROR_LOG_EVERY_MS = 5 * 60 * 1000L;

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    /** Las consultas a la web van en su propio hilo para no frenar el servidor. */
    private static final ExecutorService HTTP = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "TF Bridge");
        thread.setDaemon(true);
        return thread;
    });

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
    /** /tf vincular pendientes de mandar y los que esperan respuesta. */
    private static final Deque<JsonObject> links = new ArrayDeque<>();
    private static final Map<UUID, Long> lastLink = new HashMap<>();
    /** Cambios de rango hechos con /tf rango, pendientes de mandar. */
    private static final Deque<JsonObject> rankChanges = new ArrayDeque<>();
    /** Cambios de la tienda de monedas para la web, en orden. */
    private static final Deque<JsonObject> shopOps = new ArrayDeque<>();
    private static final int SHOP_PER_POLL = 50;
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
            loadExecuted();
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Bridge: error al iniciar", t);
            return;
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

    /** ¿Está el puente activo en este servidor? (/tf vincular solo funciona así). */
    public static boolean active() {
        return server != null && TFServerConfig.enabled();
    }

    /** /tf vincular CÓDIGO: se manda a la web en la siguiente consulta. Devuelve false si lo pide demasiado seguido. */
    public static boolean queueLink(ServerPlayer player, String code) {
        long now = System.currentTimeMillis();
        Long last = lastLink.get(player.getUUID());
        if (last != null && now - last < LINK_COOLDOWN_MS) return false;
        lastLink.put(player.getUUID(), now);
        JsonObject link = new JsonObject();
        link.addProperty("code", code);
        link.addProperty("name", player.getGameProfile().getName());
        link.addProperty("uuid", player.getUUID().toString());
        links.addLast(link);
        soon();
        return true;
    }

    /** /tf rango: la web guarda el nuevo rango del jugador (null = sin rango). */
    public static void queueRankChange(UUID uuid, String rankId) {
        JsonObject change = new JsonObject();
        change.addProperty("uuid", uuid.toString());
        if (rankId == null) change.add("rank", com.google.gson.JsonNull.INSTANCE);
        else change.addProperty("rank", rankId);
        rankChanges.addLast(change);
        soon();
    }

    /** /tf tienda: un cambio de la tienda de monedas para la web. */
    public static void queueShop(JsonObject op) {
        shopOps.addLast(op);
        soon();
    }

    /** Ejecuta un comando como la consola y devuelve los errores (vacío si fue bien). */
    public static List<String> run(MinecraftServer srv, String command) {
        ErrorCollector collector = new ErrorCollector();
        try {
            CommandSourceStack source = srv.createCommandSourceStack().withSource(collector);
            srv.getCommands().performPrefixedCommand(source, command);
        } catch (Throwable t) {
            collector.errors.add(t.toString());
        }
        return collector.errors;
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
        List<JsonObject> sendingLinks = new ArrayList<>(links);
        links.clear();
        List<JsonObject> sendingRanks = new ArrayList<>(rankChanges);
        rankChanges.clear();
        List<JsonObject> sendingShop = new ArrayList<>();
        for (JsonObject op; sendingShop.size() < SHOP_PER_POLL && (op = shopOps.pollFirst()) != null; ) sendingShop.add(op);
        body.add("seen", array(sendingSeen));
        body.add("links", array(sendingLinks));
        body.add("ranks", array(sendingRanks));
        if (!sendingShop.isEmpty()) body.add("shop", array(sendingShop));
        // Si la consulta falla, todo vuelve a la cola para la siguiente.
        Runnable requeue = () -> {
            sending.forEach(done::addLast);
            sendingSeen.forEach(seen::addLast);
            sendingRanks.forEach(rankChanges::addLast);
            for (int i = sendingShop.size() - 1; i >= 0; i--) shopOps.addFirst(sendingShop.get(i));
            for (JsonObject link : sendingLinks) {
                ServerPlayer player = srv.getPlayerList().getPlayer(UUID.fromString(link.get("uuid").getAsString()));
                if (player != null) {
                    player.sendSystemMessage(Component.literal("✖ No se pudo conectar con la web. Inténtalo de nuevo en un momento.")
                            .withStyle(ChatFormatting.RED));
                }
            }
        };

        String url = TFServerConfig.url() + "/bridge/poll";
        String secret = TFServerConfig.secret();
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);

        inFlight = true;
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
        if (json.has("store") && !json.get("store").isJsonNull()) storeHost = json.get("store").getAsString();
        if (json.has("rankList")) TFRanks.setList(json.getAsJsonArray("rankList"));
        if (json.has("roulette") && json.get("roulette").isJsonObject()) {
            net.tierrasfantasticas.tfclient.shop.TFRoulette.setConfig(json.getAsJsonObject("roulette"));
        }
        if (json.has("ranks")) TFRanks.update(srv, json.getAsJsonArray("ranks"));
        if (json.has("linkResults")) {
            for (JsonElement element : json.getAsJsonArray("linkResults")) linkResult(srv, element.getAsJsonObject());
        }
        JsonArray deliveries = json.has("deliveries") ? json.getAsJsonArray("deliveries") : new JsonArray();
        for (JsonElement element : deliveries) {
            deliver(srv, element.getAsJsonObject());
        }
    }

    private static void linkResult(MinecraftServer srv, JsonObject result) {
        ServerPlayer player;
        try {
            player = srv.getPlayerList().getPlayer(UUID.fromString(result.get("uuid").getAsString()));
        } catch (IllegalArgumentException e) {
            return;
        }
        if (player == null) return;
        if (result.get("ok").getAsBoolean()) {
            String account = result.has("account") && !result.get("account").isJsonNull() ? result.get("account").getAsString() : "tu cuenta";
            player.sendSystemMessage(Component.literal("✔ ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal("Tu jugador quedó vinculado con la cuenta de Discord ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(account).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                    .append(Component.literal(". Ya puedes ver tus compras en la web.").withStyle(ChatFormatting.GRAY)));
            player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.6f, 1.4f);
        } else {
            player.sendSystemMessage(Component.literal("✖ Código no válido o caducado. Pide uno nuevo en la web, en «Mi cuenta».")
                    .withStyle(ChatFormatting.RED));
        }
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
        boolean coinSpin = delivery.has("kind") && "ruleta-monedas".equals(text(delivery, "kind", ""));
        List<String> errors = new ArrayList<>();
        net.tierrasfantasticas.tfclient.shop.TFRoulette.startCapture();
        for (JsonElement cmd : delivery.getAsJsonArray("commands")) {
            String command = cmd.getAsString().replace("{player}", name).replace("{uuid}", uuid);
            List<String> failed = run(srv, command);
            // Quitar un grupo de rango que no tenía no es un error de la entrega.
            if (!failed.isEmpty() && !command.contains(" parent remove ")) {
                // Ruleta con monedas: a la web le llega el motivo tal cual («no tienes bastantes monedas»)
                errors.add(coinSpin ? String.join("; ", failed) : command + " → " + String.join("; ", failed));
            }
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
        String upgrade = text(delivery, "upgradeFrom", null);

        // Al comprador: título en pantalla y su mensaje.
        buyer.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 25));
        buyer.connection.send(new ClientboundSetTitleTextPacket(Component.literal("¡GRACIAS!").withStyle(accent.withBold(true))));
        buyer.connection.send(new ClientboundSetSubtitleTextPacket(rank != null
                ? Component.literal("Ahora eres ").withStyle(ChatFormatting.WHITE).append(rank.tag())
                : Component.literal("Has recibido ").withStyle(ChatFormatting.WHITE).append(Component.literal(what).withStyle(accent))));
        buyer.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.9f, 1.0f);

        if (!TFServerConfig.broadcast()) {
            buyer.sendSystemMessage(Component.literal("✦ Tu compra ha llegado: ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(what).withStyle(accent.withBold(true)))
                    .append(Component.literal(". ¡Gracias por apoyar Tierras Fantásticas!").withStyle(ChatFormatting.GOLD)));
            return;
        }

        // A todos: el mensaje enmarcado con el color del producto, un sonido y fuegos artificiales.
        String store = storeHost.isEmpty() ? "" : IDN.toUnicode(storeHost.replaceAll("/.*$", ""));
        Component line = Component.literal("  ✦ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ✦").withStyle(accent);
        MutableComponent who = Component.literal(name).withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE).withBold(true));
        if (TFRanks.of(buyer.getUUID()) != null) who = TFRanks.of(buyer.getUUID()).tag().append(who);

        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());
        lines.add(line);
        lines.add(Component.literal("        ⚡ ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("¡COMPRA EN LA TIENDA!").withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true)))
                .append(Component.literal(" ⚡").withStyle(ChatFormatting.YELLOW)));
        if (rank != null) {
            lines.add(Component.literal("   ♛ ").withStyle(accent).append(who)
                    .append(Component.literal(" ahora es ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(rank.prefix().toUpperCase(java.util.Locale.ROOT)).withStyle(accent.withBold(true)))
                    .append(Component.literal(upgrade != null ? " (mejoró desde " + upgrade.replaceFirst("^Rango ", "") + ")" : "").withStyle(ChatFormatting.DARK_GRAY)));
        } else {
            lines.add(Component.literal("   ✧ ").withStyle(accent).append(who)
                    .append(Component.literal(" ha conseguido ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(what).withStyle(accent.withBold(true))));
        }
        lines.add(Component.literal("   ¡Gracias por apoyar Tierras Fantásticas! ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("❤").withStyle(ChatFormatting.RED)));
        if (!store.isEmpty()) {
            Style link = Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://" + storeHost))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Abrir la tienda").withStyle(ChatFormatting.GOLD)));
            lines.add(Component.literal("   ➜ ").withStyle(ChatFormatting.GRAY).append(Component.literal(store).withStyle(link)));
        }
        lines.add(line);
        lines.add(Component.empty());
        for (Component message : lines) srv.getPlayerList().broadcastSystemMessage(message, false);

        for (ServerPlayer player : srv.getPlayerList().getPlayers()) {
            if (player != buyer) player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.55f, 1.0f);
        }
        if (TFServerConfig.fireworks()) fireworks(buyer, rgb, rank != null ? 3 + Math.min(rank.tier(), 4) : 3);
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
        return FMLPaths.CONFIGDIR.get().resolve("tfclient-bridge-entregas.txt");
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
