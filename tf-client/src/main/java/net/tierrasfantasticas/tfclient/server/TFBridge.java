package net.tierrasfantasticas.tfclient.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Puente entre el servidor de Minecraft y la web de Tierras Fantásticas (solo en servidores dedicados).
 *
 * <p>Cada pocos segundos manda a la web los jugadores conectados y las entregas ya hechas, y recibe las compras
 * pendientes de los jugadores que están dentro. Es el servidor el que llama a la web: no hace falta RCON ni abrir
 * puertos. Cada entrega tiene un número; el puente guarda los que ya ejecutó para no entregar nunca dos veces.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class TFBridge {
    private static final int MAX_REMEMBERED = 5000;
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
        } else {
            TFClient.LOGGER.info("TF Bridge: desactivado (bridge.enabled=false en {})", TFServerConfig.file());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        server = null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null || !TFServerConfig.enabled()) return;
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
        body.add("players", players);
        body.addProperty("max", srv.getMaxPlayers());
        JsonArray acks = new JsonArray();
        List<JsonObject> sending = new ArrayList<>(done);
        done.clear();
        sending.forEach(acks::add);
        body.add("done", acks);

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
                    handleResponse(srv, finalStatus, finalText, finalError, sending);
                } catch (Throwable t) {
                    sending.forEach(done::addLast);
                    logError("respuesta no válida de la web: " + t);
                }
            });
        });
    }

    private static void handleResponse(MinecraftServer srv, int status, String text, Throwable error, List<JsonObject> sent) {
        if (error != null || status < 0) {
            sent.forEach(done::addLast);
            connected = false;
            logError("no se pudo conectar con " + TFServerConfig.url() + " (" + (error == null ? "sin respuesta" : error) + ")");
            return;
        }
        if (status != 200) {
            sent.forEach(done::addLast);
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
        }

        JsonObject json = JsonParser.parseString(text).getAsJsonObject();
        JsonArray deliveries = json.has("deliveries") ? json.getAsJsonArray("deliveries") : new JsonArray();
        for (JsonElement element : deliveries) {
            deliver(srv, element.getAsJsonObject());
        }
    }

    private static void deliver(MinecraftServer srv, JsonObject delivery) {
        long id = delivery.get("id").getAsLong();
        if (executed.contains(id)) {
            // Ya se ejecutó (la confirmación se perdió): solo volvemos a confirmarla.
            ack(id, true, null);
            return;
        }
        String name = delivery.get("player").getAsString();
        ServerPlayer player = srv.getPlayerList().getPlayerByName(name);
        if (player == null) return; // Salió justo ahora: la web la reenviará cuando vuelva.

        // Se apunta antes de ejecutar: si el servidor se cae a mitad, nunca se entrega dos veces.
        remember(id);
        List<String> errors = new ArrayList<>();
        for (JsonElement cmd : delivery.getAsJsonArray("commands")) {
            String command = cmd.getAsString();
            ErrorCollector collector = new ErrorCollector();
            try {
                CommandSourceStack source = srv.createCommandSourceStack().withSource(collector);
                srv.getCommands().performPrefixedCommand(source, command);
            } catch (Throwable t) {
                collector.errors.add(t.toString());
            }
            if (!collector.errors.isEmpty()) errors.add(command + " → " + String.join("; ", collector.errors));
        }

        String product = delivery.has("product") ? delivery.get("product").getAsString() : "tu compra";
        int quantity = delivery.has("quantity") ? delivery.get("quantity").getAsInt() : 1;
        if (errors.isEmpty()) {
            TFClient.LOGGER.info("TF Bridge: entregado {}{} a {} (entrega {})", product, quantity > 1 ? " x" + quantity : "", name, id);
            thank(player, product, quantity);
            ack(id, true, null);
        } else {
            TFClient.LOGGER.error("TF Bridge: fallo al entregar {} a {} (entrega {}): {}", product, name, id, errors);
            ack(id, false, String.join(" | ", errors));
        }
    }

    private static void thank(ServerPlayer player, String product, int quantity) {
        String what = quantity > 1 ? product + " ×" + quantity : product;
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("¡Gracias!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Has recibido " + what).withStyle(ChatFormatting.YELLOW)));
        player.sendSystemMessage(Component.literal("✦ Tu compra en la tienda ha llegado: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(what).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(". ¡Gracias por apoyar Tierras Fantásticas!").withStyle(ChatFormatting.GOLD)));
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.8f, 1.0f);
    }

    private static void ack(long id, boolean ok, String error) {
        JsonObject item = new JsonObject();
        item.addProperty("id", id);
        item.addProperty("ok", ok);
        if (error != null) item.addProperty("error", error.length() > 480 ? error.substring(0, 480) : error);
        done.addLast(item);
        // Confirmamos enseguida en vez de esperar al siguiente intervalo.
        ticks = Math.max(ticks, TFServerConfig.intervalSeconds() * 20 - 20);
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
