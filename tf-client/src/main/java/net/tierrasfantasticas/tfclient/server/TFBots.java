package net.tierrasfantasticas.tfclient.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.netty.buffer.Unpooled;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * (1.3.109, el dueño: «quiero poder meter bots o dummies al server para que no se vea vacío; sin cuerpo físico, solo que
 * salgan conectados, con su skin en el TAB; nombres únicos inventados; /tf bots add (cantidad) (segundos entre uno y
 * otro) y /tf bots remove igual») jugadores de mentira: solo existen en la lista de jugadores (el TAB) de cada cliente,
 * con su cara (skin firmada por Mojang: el cliente no enseña las que no lo están), su ping y su modo de juego; al
 * entrar y al salir, el mensaje amarillo de siempre. No tienen entidad, ni posición, ni conexión: no ocupan nada.
 * <ul>
 *   <li>Las skins: tfclient/bots/skins.json (68, de MineSkin, con su firma: tools/bots/skins_bots.py).</li>
 *   <li>Los nombres: tfclient/bots/nombres.json (68 que no usa ninguna cuenta, como los de jugadores de verdad y sin
 *       dos hechos con el mismo molde; 1.3.110, medido en 190 000 nombres reales: tools/bots/nombres_bots.py).</li>
 *   <li>Los que están dentro se guardan en config/tfclient-bots.json y vuelven solos al reiniciar el servidor (si
 *       el nombre guardado ya no está en el mod, vuelve otro: los mismos que había).</li>
 *   <li>El servidor en la lista de servidores cuenta también los bots (MinecraftServerMixin).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class TFBots {
    /** Un bot: nombre, UUID (siempre el mismo para ese nombre) y su skin firmada. */
    public record Bot(String name, UUID id, String value, String signature) {
        public GameProfile profile() {
            return new GameProfile(id, name);
        }
    }

    private static final Random RANDOM = new Random();
    private static List<Bot> pool;
    /** Los que están dentro, por orden de entrada. */
    private static final List<Bot> online = new ArrayList<>();
    private static final Map<UUID, Integer> ping = new HashMap<>();
    private static MinecraftServer server;
    /** Lo que falta por entrar o salir, y cada cuántos ticks (la cadena se corta al acabar). */
    private static int toAdd, toRemove, addEvery, removeEvery;
    private static long tick, nextAdd, nextRemove, nextPing;

    private TFBots() {}

    // ------------------------------------------------------------------------------------------------- comando

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("bots").requires(s -> s.hasPermission(3))
                .executes(ctx -> status(ctx.getSource()))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("add")
                        .then(Commands.argument("cantidad", IntegerArgumentType.integer(1, 500))
                                .executes(ctx -> add(ctx, 3))
                                .then(Commands.argument("segundos", IntegerArgumentType.integer(0, 3600))
                                        .executes(ctx -> add(ctx, IntegerArgumentType.getInteger(ctx, "segundos"))))))
                .then(Commands.literal("remove")
                        .then(Commands.literal("all").executes(ctx -> removeAll(ctx.getSource())))
                        .then(Commands.argument("cantidad", IntegerArgumentType.integer(1, 500))
                                .executes(ctx -> remove(ctx, 3))
                                .then(Commands.argument("segundos", IntegerArgumentType.integer(0, 3600))
                                        .executes(ctx -> remove(ctx, IntegerArgumentType.getInteger(ctx, "segundos"))))));
    }

    private static int add(CommandContext<CommandSourceStack> ctx, int seconds) {
        server = ctx.getSource().getServer();
        int n = IntegerArgumentType.getInteger(ctx, "cantidad");
        int free = free().size() - toAdd;
        int take = Math.max(0, Math.min(n, free));
        if (take <= 0) {
            ctx.getSource().sendFailure(Component.literal("No quedan bots libres (" + pool().size() + " en total, " + online.size() + " dentro)."));
            return 0;
        }
        toRemove = 0;
        toAdd += take;
        addEvery = Math.max(0, seconds) * 20;
        nextAdd = tick; // el primero, ya
        ctx.getSource().sendSuccess(() -> Component.literal("Entran " + take + " bot" + (take == 1 ? "" : "s")
                + (take > 1 ? ", uno cada " + seconds + " s" : "") + (take < n ? " (no hay más libres)" : "") + "."), true);
        return take;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx, int seconds) {
        server = ctx.getSource().getServer();
        int n = IntegerArgumentType.getInteger(ctx, "cantidad");
        int take = Math.min(n, online.size());
        if (take <= 0) {
            ctx.getSource().sendFailure(Component.literal("No hay bots dentro."));
            return 0;
        }
        toAdd = 0;
        toRemove = take;
        removeEvery = Math.max(0, seconds) * 20;
        nextRemove = tick;
        ctx.getSource().sendSuccess(() -> Component.literal("Salen " + take + " bot" + (take == 1 ? "" : "s")
                + (take > 1 ? ", uno cada " + seconds + " s" : "") + "."), true);
        return take;
    }

    private static int removeAll(CommandSourceStack src) {
        server = src.getServer();
        int n = online.size();
        toAdd = toRemove = 0;
        while (!online.isEmpty()) leave(online.get(online.size() - 1), true);
        src.sendSuccess(() -> Component.literal("Fuera los " + n + " bots."), true);
        return n;
    }

    private static int status(CommandSourceStack src) {
        src.sendSuccess(() -> Component.literal("Bots: " + online.size() + " dentro de " + pool().size()
                + (toAdd > 0 ? "; faltan por entrar " + toAdd : "") + (toRemove > 0 ? "; faltan por salir " + toRemove : "")
                + ". /tf bots add <cantidad> [segundos] · /tf bots remove <cantidad|all> [segundos] · /tf bots list"), false);
        return online.size();
    }

    private static int list(CommandSourceStack src) {
        StringBuilder sb = new StringBuilder();
        for (Bot b : online) sb.append(sb.length() == 0 ? "" : ", ").append(b.name());
        src.sendSuccess(() -> Component.literal(online.isEmpty() ? "No hay bots dentro." : "Bots (" + online.size() + "): " + sb), false);
        return online.size();
    }

    // ------------------------------------------------------------------------------------------------- lo de dentro

    /** Los bots que hay ahora (para la lista de servidores: MinecraftServerMixin). */
    public static List<Bot> online() {
        return Collections.unmodifiableList(online);
    }

    public static int count() {
        return online.size();
    }

    private static List<Bot> free() {
        Set<String> taken = new HashSet<>();
        for (Bot b : online) taken.add(b.name().toLowerCase());
        if (server != null) for (ServerPlayer p : server.getPlayerList().getPlayers()) taken.add(p.getGameProfile().getName().toLowerCase());
        List<Bot> out = new ArrayList<>();
        for (Bot b : pool()) if (!taken.contains(b.name().toLowerCase())) out.add(b);
        return out;
    }

    private static void join(Bot b, boolean announce) {
        if (server == null || online.contains(b)) return;
        online.add(b);
        ping.put(b.id(), 25 + RANDOM.nextInt(110));
        server.getPlayerList().broadcastAll(addPacket(List.of(b)));
        if (announce) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("multiplayer.player.joined", Component.literal(b.name())).withStyle(ChatFormatting.YELLOW), false);
        }
        save();
    }

    private static void leave(Bot b, boolean announce) {
        if (server == null || !online.remove(b)) return;
        ping.remove(b.id());
        server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(b.id())));
        if (announce) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("multiplayer.player.left", Component.literal(b.name())).withStyle(ChatFormatting.YELLOW), false);
        }
        save();
    }

    /**
     * Su entrada en la lista de jugadores: el perfil con la skin, en supervivencia, en la lista y con su ping. El paquete
     * de Minecraft solo se construye con jugadores de verdad: se escribe como va por la red y se lee.
     */
    private static ClientboundPlayerInfoUpdatePacket addPacket(List<Bot> bots) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeEnumSet(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY),
                ClientboundPlayerInfoUpdatePacket.Action.class);
        buf.writeVarInt(bots.size());
        for (Bot b : bots) {
            buf.writeUUID(b.id());
            buf.writeUtf(b.name(), 16);
            PropertyMap props = new PropertyMap();
            props.put("textures", new Property("textures", b.value(), b.signature()));
            buf.writeGameProfileProperties(props);
            buf.writeVarInt(GameType.SURVIVAL.getId());
            buf.writeBoolean(true);
            buf.writeVarInt(ping.getOrDefault(b.id(), 60));
        }
        try {
            return new ClientboundPlayerInfoUpdatePacket(buf);
        } finally {
            buf.release();
        }
    }

    /** El ping de todos (cambia un poco cada medio minuto, como el de verdad). */
    private static ClientboundPlayerInfoUpdatePacket pingPacket() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeEnumSet(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY), ClientboundPlayerInfoUpdatePacket.Action.class);
        buf.writeVarInt(online.size());
        for (Bot b : online) {
            int p = Math.max(12, Math.min(260, ping.getOrDefault(b.id(), 60) + RANDOM.nextInt(31) - 15));
            ping.put(b.id(), p);
            buf.writeUUID(b.id());
            buf.writeVarInt(p);
        }
        try {
            return new ClientboundPlayerInfoUpdatePacket(buf);
        } finally {
            buf.release();
        }
    }

    // ------------------------------------------------------------------------------------------------- eventos

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        online.clear();
        ping.clear();
        toAdd = toRemove = 0;
        // los que estaban dentro al apagar, otra vez dentro (sin mensaje: ya «estaban»)
        int missing = 0;
        for (String name : load()) {
            Bot found = null;
            for (Bot b : pool()) if (b.name().equalsIgnoreCase(name)) found = b;
            if (found == null) missing++;
            else if (!online.contains(found)) {
                online.add(found);
                ping.put(found.id(), 25 + RANDOM.nextInt(110));
            }
        }
        // los guardados con un nombre que ya no está en el mod (1.3.110 cambió los nombres): los mismos que había, con
        // otros nombres de la lista
        List<Bot> free = new ArrayList<>(pool());
        free.removeAll(online);
        Collections.shuffle(free, RANDOM);
        for (int i = 0; i < missing && i < free.size(); i++) {
            online.add(free.get(i));
            ping.put(free.get(i).id(), 25 + RANDOM.nextInt(110));
        }
        if (!online.isEmpty()) TFClient.LOGGER.info("TF Bots: {} vuelven a estar dentro", online.size());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        server = null;
        online.clear();
        ping.clear();
        toAdd = toRemove = 0;
    }

    /** Al que entra, los bots que ya están (después de la lista de los de verdad, que la manda Minecraft al entrar). */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!online.isEmpty() && event.getEntity() instanceof ServerPlayer p) p.connection.send(addPacket(online));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null) return;
        tick++;
        if (toAdd > 0 && tick >= nextAdd) {
            List<Bot> free = free();
            if (free.isEmpty()) {
                toAdd = 0;
            } else {
                join(free.get(RANDOM.nextInt(free.size())), true);
                toAdd--;
                nextAdd = tick + addEvery;
            }
        }
        if (toRemove > 0 && tick >= nextRemove) {
            if (online.isEmpty()) {
                toRemove = 0;
            } else {
                leave(online.get(RANDOM.nextInt(online.size())), true);
                toRemove--;
                nextRemove = tick + removeEvery;
            }
        }
        if (!online.isEmpty() && tick >= nextPing) {
            nextPing = tick + 600;
            server.getPlayerList().broadcastAll(pingPacket());
        }
    }

    // ------------------------------------------------------------------------------------------------- datos

    /** Las skins y los nombres del mod, emparejados (el nombre n con la skin n); el UUID sale del nombre. */
    static synchronized List<Bot> pool() {
        if (pool != null) return pool;
        List<Bot> out = new ArrayList<>();
        try {
            JsonArray skins = read("/tfclient/bots/skins.json").getAsJsonArray();
            JsonArray names = read("/tfclient/bots/nombres.json").getAsJsonArray();
            for (int i = 0; i < Math.min(skins.size(), names.size()); i++) {
                JsonObject s = skins.get(i).getAsJsonObject();
                String name = names.get(i).getAsString();
                UUID id = UUID.nameUUIDFromBytes(("TFBot:" + name.toLowerCase()).getBytes(StandardCharsets.UTF_8));
                out.add(new Bot(name, id, s.get("value").getAsString(), s.get("signature").getAsString()));
            }
        } catch (Exception e) {
            TFClient.LOGGER.error("TF Bots: no se pudieron leer las skins o los nombres", e);
        }
        pool = out;
        return pool;
    }

    private static JsonElement read(String res) throws Exception {
        try (InputStream in = TFBots.class.getResourceAsStream(res)) {
            if (in == null) throw new IllegalStateException("falta " + res);
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonElement.class);
        }
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("tfclient-bots.json");
    }

    private static void save() {
        try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
            JsonArray a = new JsonArray();
            for (Bot b : online) a.add(b.name());
            JsonObject o = new JsonObject();
            o.add("dentro", a);
            new Gson().toJson(o, w);
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Bots: no se pudo guardar {}", file(), e);
        }
    }

    private static List<String> load() {
        List<String> out = new ArrayList<>();
        if (!Files.exists(file())) return out;
        try (Reader r = Files.newBufferedReader(file(), StandardCharsets.UTF_8)) {
            JsonObject o = new Gson().fromJson(r, JsonObject.class);
            if (o != null && o.has("dentro")) for (JsonElement e : o.getAsJsonArray("dentro")) out.add(e.getAsString());
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Bots: no se pudo leer {}", file(), e);
        }
        return out;
    }
}
