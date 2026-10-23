/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
import net.minecraft.commands.SharedSuggestionProvider;
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
 *   <li>(1.3.111) Quién entra, al azar con memoria: a veces vuelve uno que ya estuvo (más los que más vienen) y si
 *       no, uno nuevo; entre uno y otro, una espera al azar alrededor de los segundos pedidos (no a ritmo fijo).</li>
 *   <li>(1.3.125, el dueño: «no tienen rango de LuckPerms, el TAB, el servidor y la consola no los reconocen como
 *       jugadores activos, no salen en /tpa») entran como jugadores de verdad ({@link TFBotPlayer}: sin cuerpo para
 *       nadie); con bots.reales=false, o si falla, solo en la lista como antes.</li>
 *   <li>(1.3.125, el dueño: «borra todo dato guardado de los bots al actualizar el mod; pon esa regla») al arrancar con
 *       otra versión del TF Client se borra su registro y sus datos (lo de Minecraft, EssentialsX, LuckPerms y el pad),
 *       como /tf bots limpiar.</li>
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
    /** (1.3.125) Los que están dentro como jugadores de verdad (los demás, solo en la lista). */
    private static final Map<UUID, ServerPlayer> REAL = new HashMap<>();
    /** Los que estaban dentro al apagar: vuelven a entrar de uno en uno en cuanto arranca. */
    private static final java.util.ArrayDeque<Bot> RESTORE = new java.util.ArrayDeque<>();
    private static boolean stopping;
    private static MinecraftServer server;
    /** Lo que falta por entrar o salir, y cada cuántos ticks (la cadena se corta al acabar). */
    private static int toAdd, toRemove, addEvery, removeEvery;
    private static long tick, nextAdd, nextRemove, nextPing;
    /**
     * Los que ya han estado alguna vez (1.3.111, el dueño: «nunca debe ser el mismo que puse, debe ser al azar, y a veces
     * que toque uno de los que ya salieron, para que se note que son jugadores ocasionales o activos»): cuántas veces
     * han entrado y cuándo salieron por última vez. Se guarda en config/tfclient-bots.json.
     */
    private record Seen(int times, long lastLeft) {}
    private static final Map<String, Seen> seen = new HashMap<>();
    /** Más o menos uno de cada tres que entran es uno que ya estuvo (los que vienen más, con más probabilidad). */
    private static final double RETURNING = 0.35;
    /** Uno que acaba de salir no vuelve enseguida. */
    private static final long BACK_AFTER_MS = 5 * 60 * 1000;

    private TFBots() {}

    // ------------------------------------------------------------------------------------------------- comando

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("bots").requires(s -> s.hasPermission(3))
                .executes(ctx -> status(ctx.getSource()))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                // (1.3.120, el dueño: «en add, otro subcomando: cantidad o por nombre, para tener más control de cuáles entran»)
                .then(Commands.literal("add")
                        .then(Commands.literal("cantidad")
                                .then(Commands.argument("cantidad", IntegerArgumentType.integer(1, 500))
                                        .executes(ctx -> add(ctx, 3))
                                        .then(Commands.argument("segundos", IntegerArgumentType.integer(0, 3600))
                                                .executes(ctx -> add(ctx, IntegerArgumentType.getInteger(ctx, "segundos"))))))
                        .then(Commands.literal("nombre")
                                .then(Commands.argument("nombre", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(free().stream().map(Bot::name), b))
                                        .executes(ctx -> addNamed(ctx.getSource(), StringArgumentType.getString(ctx, "nombre"))))))
                // (1.3.120, el dueño: «limpia los registros de todos los bots, déjalos limpios») fuera los de dentro y lo
                // que se recuerda de los que vinieron (veces, salidas): el Ranking y sus fichas empiezan de cero
                .then(Commands.literal("limpiar").executes(ctx -> wipe(ctx.getSource())))
                .then(Commands.literal("remove")
                        .then(Commands.literal("all").executes(ctx -> removeAll(ctx.getSource())))
                        .then(Commands.literal("nombre")
                                .then(Commands.argument("nombre", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(online.stream().map(Bot::name), b))
                                        .executes(ctx -> removeNamed(ctx.getSource(), StringArgumentType.getString(ctx, "nombre")))))
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
                + (take > 1 && seconds > 0 ? ", uno cada " + seconds + " s más o menos (al azar)" : "")
                + (take < n ? " (no hay más libres)" : "") + "."), true);
        return take;
    }

    /** Entra ese bot (por su nombre, de los del mod), ya. */
    private static int addNamed(CommandSourceStack src, String name) {
        server = src.getServer();
        Bot b = null;
        for (Bot x : pool()) if (x.name().equalsIgnoreCase(name)) b = x;
        if (b == null) {
            src.sendFailure(Component.literal("No hay ningún bot «" + name + "». Pulsa Tab para ver los libres."));
            return 0;
        }
        if (online.contains(b)) {
            src.sendFailure(Component.literal(b.name() + " ya está dentro."));
            return 0;
        }
        if (server.getPlayerList().getPlayerByName(b.name()) != null) {
            src.sendFailure(Component.literal("Hay un jugador de verdad que se llama " + b.name() + "."));
            return 0;
        }
        join(b, true);
        Bot joined = b;
        src.sendSuccess(() -> Component.literal("Entra " + joined.name() + "."), true);
        return 1;
    }

    /** Sale ese bot, ya. */
    private static int removeNamed(CommandSourceStack src, String name) {
        server = src.getServer();
        for (Bot b : new ArrayList<>(online)) {
            if (!b.name().equalsIgnoreCase(name)) continue;
            leave(b, true);
            src.sendSuccess(() -> Component.literal("Sale " + b.name() + "."), true);
            return 1;
        }
        src.sendFailure(Component.literal("No hay ningún bot «" + name + "» dentro."));
        return 0;
    }

    /** Borra todo lo de los bots: fuera los de dentro (sin mensaje) y su registro (veces que vinieron, salidas). */
    private static int wipe(CommandSourceStack src) {
        server = src.getServer();
        int n = online.size(), known = seen.size();
        toAdd = toRemove = 0;
        RESTORE.clear();
        while (!online.isEmpty()) leave(online.get(0), false);
        wipeData(server, new HashSet<>(seen.keySet()));
        seen.clear();
        save();
        src.sendSuccess(() -> Component.literal("Bots limpios: salieron " + n + " y se borró el registro de " + known + "."), true);
        return known;
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
                + (take > 1 && seconds > 0 ? ", uno cada " + seconds + " s más o menos (al azar)" : "") + "."), true);
        return take;
    }

    private static int removeAll(CommandSourceStack src) {
        server = src.getServer();
        int n = online.size();
        toAdd = toRemove = 0;
        while (!online.isEmpty()) leave(online.get(RANDOM.nextInt(online.size())), true);
        src.sendSuccess(() -> Component.literal("Fuera los " + n + " bots."), true);
        return n;
    }

    private static int status(CommandSourceStack src) {
        src.sendSuccess(() -> Component.literal("Bots: " + online.size() + " dentro de " + pool().size()
                + (toAdd > 0 ? "; faltan por entrar " + toAdd : "") + (toRemove > 0 ? "; faltan por salir " + toRemove : "")
                + ". /tf bots add cantidad <n> [segundos] · /tf bots add nombre <nombre> · /tf bots remove <cantidad|all|nombre> · "
                + "/tf bots list · /tf bots limpiar"), false);
        return online.size();
    }

    private static int list(CommandSourceStack src) {
        StringBuilder sb = new StringBuilder();
        for (Bot b : online) sb.append(sb.length() == 0 ? "" : ", ").append(b.name());
        src.sendSuccess(() -> Component.literal(online.isEmpty() ? "No hay bots dentro." : "Bots (" + online.size() + "): " + sb), false);
        return online.size();
    }

    /**
     * (1.3.125) Lo que el servidor guarda de los bots, fuera: sus archivos de jugador de Minecraft (playerdata, stats,
     * advancements), su usuario de EssentialsX, sus datos en LuckPerms (los que vinieron) y su ficha del pad.
     */
    private static void wipeData(MinecraftServer srv, Set<String> cameNames) {
        if (srv == null) return;
        int files = 0;
        Path essentials = srv.getServerDirectory().toPath().resolve("plugins").resolve("Essentials").resolve("userdata");
        for (Bot b : pool()) {
            String id = b.id().toString();
            for (Path f : List.of(srv.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR).resolve(id + ".dat"),
                    srv.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR).resolve(id + ".dat_old"),
                    srv.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_STATS_DIR).resolve(id + ".json"),
                    srv.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(id + ".json"),
                    essentials.resolve(id + ".yml"))) {
                try {
                    if (Files.deleteIfExists(f)) files++;
                } catch (Exception e) {
                    TFClient.LOGGER.warn("TF Bots: no se pudo borrar {}: {}", f, e.toString());
                }
            }
            net.tierrasfantasticas.tfclient.pad.server.PadStats.forget(b.id());
            if (cameNames.contains(b.name().toLowerCase())) TFLuckPerms.dispatch(srv, "lp user " + id + " clear");
        }
        TFClient.LOGGER.info("TF Bots: datos de bots borrados ({} archivos, {} usuarios de LuckPerms)", files, cameNames.size());
    }

    // ------------------------------------------------------------------------------------------------- lo de dentro

    /** Los bots que hay ahora (los de verdad y los de solo la lista). */
    public static List<Bot> online() {
        return Collections.unmodifiableList(online);
    }

    /**
     * (1.3.125) Los que están solo en la lista del TAB (los de verdad ya los cuenta el servidor: MinecraftServerBotsMixin
     * suma solo estos).
     */
    public static List<Bot> listOnly() {
        List<Bot> out = new ArrayList<>();
        for (Bot b : online) if (!REAL.containsKey(b.id())) out.add(b);
        return out;
    }

    /** (1.3.125) ¿Es un bot metido como jugador de verdad? */
    public static boolean isBotPlayer(ServerPlayer player) {
        return player instanceof TFBotPlayer;
    }

    public static int count() {
        return online.size();
    }

    /** El bot que está dentro con ese UUID, o null. */
    public static Bot byId(UUID id) {
        for (Bot b : online) if (b.id().equals(id)) return b;
        return null;
    }

    /**
     * (1.3.113, el dueño: «los bots debe reconocerlos el pad, deben salir conectados, ponles trabajos y datos por defecto
     * para que no tengan 0; clan no») lo que enseña su ficha del pad: un oficio con su nivel y lo que ha hecho, siempre lo
     * mismo para el mismo bot (sale de su nombre) y un poco más cada vez que vuelve.
     */
    public record Card(List<String> jobs, int[] jobLevels, long missions, long hunts, long photos, long likes, long explores,
                       long travels) {}

    public static Card card(Bot b, List<String> jobIds, int maxJobs) {
        Random r = new Random(b.name().toLowerCase().hashCode() * 31L + 7);
        Seen s = seen.get(b.name().toLowerCase());
        int times = s == null ? 1 : Math.max(1, s.times());
        // (1.3.115, el dueño: «¿por qué tienen esas stats de comunidad si no han subido nada? Ponles 0, y menos stats: al
        // inicio no es creíble que tengan 30 misiones hechas, mínimo 3 o 5») como alguien que acaba de empezar en un
        // servidor nuevo: un oficio (alguno, dos) de nivel bajo, de 0 a 5 misiones, alguna caza, ninguna foto ni like y
        // pocos viajes; cada vez que vuelve, un poco más (despacio)
        List<String> pool = new ArrayList<>(jobIds);
        Collections.shuffle(pool, r);
        int n = Math.min(pool.size(), Math.max(1, Math.min(maxJobs, r.nextInt(5) == 0 ? 2 : 1)));
        List<String> jobs = new ArrayList<>(pool.subList(0, n));
        int[] levels = new int[n];
        for (int i = 0; i < n; i++) levels[i] = Math.min(50, 1 + r.nextInt(i == 0 ? 5 : 3) + Math.min(6, times / 3));
        return new Card(jobs, levels,
                r.nextInt(6) + Math.min(12, times / 2),
                r.nextInt(3) + Math.min(5, times / 4),
                0,
                0,
                r.nextInt(4) + Math.min(8, times / 2),
                1 + r.nextInt(5) + Math.min(10, times / 2));
    }

    /** (1.3.117) Un bot del mod por su UUID, esté dentro o no (null si no es un bot). */
    public static Bot ofId(UUID id) {
        if (id == null) return null;
        for (Bot b : pool()) if (b.id().equals(id)) return b;
        return null;
    }

    /** (1.3.117) Los bots que están dentro o han estado alguna vez (los del Ranking del pad). */
    public static List<Bot> known() {
        List<Bot> out = new ArrayList<>();
        for (Bot b : pool()) if (online.contains(b) || seen.containsKey(b.name().toLowerCase())) out.add(b);
        return out;
    }

    /**
     * (1.3.117, el dueño: «los bots en el pad no tienen ranking; bajo, sí, no tan alto, y no números exactos como ese
     * 500») lo que lleva un bot en el Ranking del pad: sus misiones, cazas y exploraciones de su ficha (card) y unas
     * monedas de quien empieza (de unas decenas a poco más de mil, más cuantas más veces ha venido), nunca redondas.
     * Likes, ninguno (no ha subido fotos). Siempre lo mismo para el mismo bot mientras no vuelva a entrar.
     */
    public static long rankValue(Bot b, String what, List<String> jobIds, int maxJobs) {
        Card c = card(b, jobIds, maxJobs);
        return switch (what) {
            case "misiones" -> c.missions();
            case "cazas" -> c.hunts();
            case "exploraciones" -> c.explores();
            case "monedas" -> {
                Random r = new Random(b.name().toLowerCase().hashCode() * 131L + 17);
                Seen s = seen.get(b.name().toLowerCase());
                int times = s == null ? 1 : Math.max(1, s.times());
                long v = 37 + r.nextInt(260) + (long) Math.min(times, 20) * (18 + r.nextInt(47));
                while (v % 5 == 0) v += 1 + r.nextInt(3);
                yield v;
            }
            default -> 0;
        };
    }

    private static List<Bot> free() {
        Set<String> taken = new HashSet<>();
        for (Bot b : online) taken.add(b.name().toLowerCase());
        if (server != null) for (ServerPlayer p : server.getPlayerList().getPlayers()) taken.add(p.getGameProfile().getName().toLowerCase());
        List<Bot> out = new ArrayList<>();
        for (Bot b : pool()) if (!taken.contains(b.name().toLowerCase())) out.add(b);
        return out;
    }

    /**
     * El que entra ahora: a veces (RETURNING) uno que ya estuvo y salió hace rato, con más probabilidad cuantas más veces
     * ha venido (los «activos»); si no, uno que no ha venido nunca (o, si ya vinieron todos, cualquiera libre).
     */
    private static Bot pick(List<Bot> free) {
        long now = System.currentTimeMillis();
        List<Bot> fresh = new ArrayList<>(), back = new ArrayList<>();
        for (Bot b : free) {
            Seen s = seen.get(b.name().toLowerCase());
            if (s == null) fresh.add(b);
            else if (now - s.lastLeft() >= BACK_AFTER_MS) back.add(b);
        }
        if (!back.isEmpty() && (fresh.isEmpty() || RANDOM.nextDouble() < RETURNING)) {
            int total = 0;
            for (Bot b : back) total += 1 + seen.get(b.name().toLowerCase()).times();
            int r = RANDOM.nextInt(total);
            for (Bot b : back) {
                r -= 1 + seen.get(b.name().toLowerCase()).times();
                if (r < 0) return b;
            }
        }
        List<Bot> from = fresh.isEmpty() ? free : fresh;
        return from.get(RANDOM.nextInt(from.size()));
    }

    /** La espera hasta el siguiente: entre un tercio y algo más del doble de lo pedido, al azar (como gente de verdad). */
    private static long jitter(int ticks) {
        return ticks <= 0 ? 0 : Math.max(1, Math.round(ticks * (0.35 + RANDOM.nextDouble() * 1.3)));
    }

    private static void join(Bot b, boolean announce) {
        if (server == null || online.contains(b) || server.getPlayerList().getPlayer(b.id()) != null) return;
        online.add(b);
        Seen s = seen.get(b.name().toLowerCase());
        seen.put(b.name().toLowerCase(), new Seen(s == null ? 1 : s.times() + 1, s == null ? 0 : s.lastLeft()));
        ping.put(b.id(), 25 + RANDOM.nextInt(110));
        // (1.3.125) como jugador de verdad (el servidor anuncia su entrada); si no se puede, solo en la lista
        ServerPlayer real = TFServerConfig.realBots() ? TFBotPlayer.spawn(server, b, ping.get(b.id())) : null;
        if (real != null) {
            REAL.put(b.id(), real);
        } else {
            server.getPlayerList().broadcastAll(addPacket(List.of(b)));
            if (announce) {
                server.getPlayerList().broadcastSystemMessage(
                        Component.translatable("multiplayer.player.joined", Component.literal(b.name())).withStyle(ChatFormatting.YELLOW), false);
            }
        }
        save();
    }

    private static void leave(Bot b, boolean announce) {
        if (server == null || !online.remove(b)) return;
        ping.remove(b.id());
        Seen s = seen.get(b.name().toLowerCase());
        seen.put(b.name().toLowerCase(), new Seen(s == null ? 1 : s.times(), System.currentTimeMillis()));
        ServerPlayer real = REAL.remove(b.id());
        if (real != null) {
            TFBotPlayer.despawn(real); // (sale como un jugador: el servidor anuncia su salida)
        } else {
            server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(b.id())));
            if (announce) {
                server.getPlayerList().broadcastSystemMessage(
                        Component.translatable("multiplayer.player.left", Component.literal(b.name())).withStyle(ChatFormatting.YELLOW), false);
            }
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
    private static ClientboundPlayerInfoUpdatePacket pingPacket(List<Bot> bots) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeEnumSet(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY), ClientboundPlayerInfoUpdatePacket.Action.class);
        buf.writeVarInt(bots.size());
        for (Bot b : bots) {
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
        stopping = false;
        online.clear();
        ping.clear();
        REAL.clear();
        RESTORE.clear();
        toAdd = toRemove = 0;
        // los que estaban dentro al apagar, otra vez dentro (de uno en uno en los primeros ticks)
        int missing = 0;
        for (String name : load()) {
            Bot found = null;
            for (Bot b : pool()) if (b.name().equalsIgnoreCase(name)) found = b;
            if (found == null) missing++;
            else if (!RESTORE.contains(found)) RESTORE.add(found);
        }
        // los guardados con un nombre que ya no está en el mod (1.3.110 cambió los nombres): los mismos que había, con
        // otros nombres de la lista
        List<Bot> free = new ArrayList<>(pool());
        free.removeAll(RESTORE);
        Collections.shuffle(free, RANDOM);
        for (int i = 0; i < missing && i < free.size(); i++) RESTORE.add(free.get(i));
        if (!RESTORE.isEmpty()) TFClient.LOGGER.info("TF Bots: {} vuelven a entrar", RESTORE.size());
    }

    /** Al apagar, el servidor echa a todos: los bots no «salen» (vuelven al arrancar). */
    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        stopping = true;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        server = null;
        online.clear();
        ping.clear();
        REAL.clear();
        RESTORE.clear();
        toAdd = toRemove = 0;
    }

    /** Al que entra, los bots que ya están (después de la lista de los de verdad, que la manda Minecraft al entrar). */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p instanceof TFBotPlayer) return;
        List<Bot> list = listOnly();
        if (!list.isEmpty()) p.connection.send(addPacket(list));
    }

    /** (1.3.125) Un bot de verdad que sale sin pasar por aquí (lo echaron con /kick): fuera de la lista de dentro. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (stopping || !(event.getEntity() instanceof TFBotPlayer p) || REAL.remove(p.getUUID()) == null) return;
        for (Bot b : new ArrayList<>(online)) {
            if (!b.id().equals(p.getUUID())) continue;
            online.remove(b);
            ping.remove(b.id());
            Seen s = seen.get(b.name().toLowerCase());
            seen.put(b.name().toLowerCase(), new Seen(s == null ? 1 : s.times(), System.currentTimeMillis()));
            save();
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null) return;
        tick++;
        if (!RESTORE.isEmpty() && tick % 2 == 0) join(RESTORE.poll(), false);
        if (toAdd > 0 && tick >= nextAdd) {
            List<Bot> free = free();
            if (free.isEmpty()) {
                toAdd = 0;
            } else {
                join(pick(free), true);
                toAdd--;
                nextAdd = tick + jitter(addEvery);
            }
        }
        if (toRemove > 0 && tick >= nextRemove) {
            if (online.isEmpty()) {
                toRemove = 0;
            } else {
                leave(online.get(RANDOM.nextInt(online.size())), true);
                toRemove--;
                nextRemove = tick + jitter(removeEvery);
            }
        }
        if (!online.isEmpty() && tick >= nextPing) {
            nextPing = tick + 600;
            // los de verdad: su ping lo manda Minecraft (cada 30 s); los de la lista, este paquete
            for (Map.Entry<UUID, ServerPlayer> e : REAL.entrySet()) {
                int p = Math.max(12, Math.min(260, ping.getOrDefault(e.getKey(), 60) + RANDOM.nextInt(31) - 15));
                ping.put(e.getKey(), p);
                e.getValue().latency = p;
            }
            List<Bot> list = listOnly();
            if (!list.isEmpty()) server.getPlayerList().broadcastAll(pingPacket(list));
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
            o.addProperty("version", FILE_VERSION);
            o.addProperty("mod", TFClient.VERSION);
            o.add("dentro", a);
            JsonObject v = new JsonObject();
            for (Map.Entry<String, Seen> e : seen.entrySet()) {
                JsonObject one = new JsonObject();
                one.addProperty("veces", e.getValue().times());
                one.addProperty("salio", e.getValue().lastLeft());
                v.add(e.getKey(), one);
            }
            o.add("vistos", v);
            new Gson().toJson(o, w);
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Bots: no se pudo guardar {}", file(), e);
        }
    }

    /**
     * (1.3.120, el dueño: «limpia los registros de todos los bots, ranking, todo, sin datos residuales; solo tendrán datos
     * los que yo una») el archivo de antes de la versión 2 no se lee: se empieza de cero una vez.
     */
    private static final int FILE_VERSION = 2;

    private static List<String> load() {
        List<String> out = new ArrayList<>();
        if (!Files.exists(file())) return out;
        try (Reader r = Files.newBufferedReader(file(), StandardCharsets.UTF_8)) {
            JsonObject o = new Gson().fromJson(r, JsonObject.class);
            if (o == null || !o.has("version") || o.get("version").getAsInt() < FILE_VERSION) {
                seen.clear();
                TFClient.LOGGER.info("TF Bots: registro de bots anterior borrado (se empieza de cero)");
                return out;
            }
            // (1.3.125, regla del dueño) con otra versión del mod, todo lo de los bots se borra y se empieza de cero
            if (!o.has("mod") || !TFClient.VERSION.equals(o.get("mod").getAsString())) {
                Set<String> came = new HashSet<>();
                if (o.has("vistos")) came.addAll(o.getAsJsonObject("vistos").keySet());
                if (o.has("dentro")) for (JsonElement e : o.getAsJsonArray("dentro")) came.add(e.getAsString().toLowerCase());
                seen.clear();
                wipeData(server, came);
                TFClient.LOGGER.info("TF Bots: versión nueva del TF Client ({}): registro y datos de los bots borrados", TFClient.VERSION);
                save();
                return out;
            }
            if (o != null && o.has("dentro")) for (JsonElement e : o.getAsJsonArray("dentro")) out.add(e.getAsString());
            seen.clear();
            if (o != null && o.has("vistos")) {
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("vistos").entrySet()) {
                    JsonObject one = e.getValue().getAsJsonObject();
                    seen.put(e.getKey(), new Seen(one.get("veces").getAsInt(), one.get("salio").getAsLong()));
                }
            }
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Bots: no se pudo leer {}", file(), e);
        }
        return out;
    }
}
