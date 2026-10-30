/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonObject;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * (1.3.154, el dueño: «agreguemos que existan 2 chats, el público y el del clan; con /clan chat enable o disable, enable
 * para quitar el público y solo dejar el de los miembros del mismo clan; solo para los que están en un clan; y nadie, a
 * excepción de operadores, puede ver lo que escriben en el chat del clan de otros clanes; hazlo bien con esa regla
 * estricta») El chat del clan.
 * <ul>
 *   <li><b>/clan chat enable</b> (solo quien está en un clan): lo que escribe va solo a los miembros de su clan
 *   conectados y a los operadores, y deja de ver el chat público de los demás (los avisos del servidor y los /msg, sí).
 *   <b>/clan chat disable</b>: vuelve al público. Se guarda en clanes.json («chatClan») y se quita al salir del
 *   clan.</li>
 *   <li>El mensaje no llega nunca al chat público: se para en la cabeza de
 *   ServerGamePacketListenerImpl.broadcastChatMessage (mixin claims/ServerChatPromptMixin), antes del ServerChatEvent de
 *   Forge y, en Mohist, antes del AsyncPlayerChatEvent de Bukkit (así EssentialsX, DiscordSRV o el registro de otro
 *   plugin no lo ven). Si aun así llegara a esos eventos, también se para allí ({@link #onServerChat} y el oyente de
 *   Bukkit de {@link net.tierrasfantasticas.tfclient.server.TFBukkit#registerChatFilter}).</li>
 *   <li>Los destinatarios salen de {@link #recipients}: su clan y los operadores; nadie más.</li>
 *   <li>Quien lo tiene activado no recibe el chat público: mixin ServerPlayerClanChatMixin (Forge) y, en Mohist, se le
 *   quita de los destinatarios del AsyncPlayerChatEvent.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class PadClanChat {
    /** Los conectados con el chat del clan puesto (lo leen el hilo del chat y el de Bukkit: concurrente). */
    private static final Set<UUID> ON = ConcurrentHashMap.newKeySet();
    /** Antispam: los últimos mensajes de cada uno (ms). */
    private static final Map<UUID, ArrayDeque<Long>> RECENT = new ConcurrentHashMap<>();
    private static final int BURST = 4;
    private static final long BURST_MS = 2500;
    /** Para no repartir dos veces el mismo mensaje si llega por dos caminos (mixin y evento). */
    private record Last(long at, String text) {}
    private static final Map<UUID, Last> LAST_ROUTED = new ConcurrentHashMap<>();

    /** Para la prueba en el juego: a quién se le dio cada mensaje del clan y qué se le ocultó del público. */
    public static final boolean TEST = System.getenv("TFCLIENT_PRUEBA_JEFES") != null;
    public static final List<String> TEST_LOG = new java.util.concurrent.CopyOnWriteArrayList<>();

    private PadClanChat() {}

    // ---------------------------------------------------------------------------------------------------------------
    // Estado
    // ---------------------------------------------------------------------------------------------------------------

    /** ¿Tiene puesto el chat del clan (y sigue en un clan)? */
    public static boolean enabled(UUID uuid) {
        return uuid != null && ON.contains(uuid);
    }

    /** ¿No debe ver el chat público de otros? (lo usa el filtro, desde cualquier hilo) */
    public static boolean hidesPublic(UUID receiver, UUID sender) {
        return receiver != null && !receiver.equals(sender) && ON.contains(receiver);
    }

    private static void set(UUID uuid, boolean on) {
        JsonObject p = on ? PadClans.STORE_IMPL.player(uuid) : PadClans.STORE_IMPL.playerIfAny(uuid);
        if (p != null) {
            if (on) p.addProperty("chatClan", true);
            else p.remove("chatClan");
            PadClans.STORE_IMPL.changed();
        }
        if (on) ON.add(uuid);
        else ON.remove(uuid);
    }

    /** Al salir o ser echado de un clan (o disolverlo): fuera el chat del clan. */
    static void forget(UUID uuid) {
        ON.remove(uuid);
        JsonObject p = PadClans.STORE_IMPL.playerIfAny(uuid);
        if (p != null && p.has("chatClan")) {
            p.remove("chatClan");
            PadClans.STORE_IMPL.changed();
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();
        JsonObject p = PadClans.STORE_IMPL.playerIfAny(uuid);
        boolean on = p != null && TFJson.bool(p, "chatClan", false);
        if (on && PadClans.clanIdOf(uuid) == null) {
            forget(uuid);
            on = false;
        }
        if (on) {
            ON.add(uuid);
            player.sendSystemMessage(tag("Tienes el chat del clan activado: solo te lee tu clan. /clan chat disable para volver al público.", ChatFormatting.YELLOW));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ON.remove(event.getEntity().getUUID());
        RECENT.remove(event.getEntity().getUUID());
        LAST_ROUTED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ON.clear();
        // En Mohist, el chat de los plugins (EssentialsX) va por Bukkit: allí también se filtra
        net.tierrasfantasticas.tfclient.server.TFBukkit.registerChatFilter(PadClanChat::hidesPublic, PadClanChat::divertFromBukkit);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // /clan chat enable | disable
    // ---------------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(command());
    }

    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("clan")
                .executes(ctx -> usage(ctx.getSource()))
                .then(Commands.literal("chat")
                        .executes(ctx -> usage(ctx.getSource()))
                        .then(Commands.literal("enable").executes(ctx -> toggle(ctx.getSource(), true)))
                        .then(Commands.literal("disable").executes(ctx -> toggle(ctx.getSource(), false))));
    }

    private static int usage(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        boolean on = player != null && enabled(player.getUUID());
        src.sendSuccess(() -> tag("Chat del clan " + (on ? "ACTIVADO" : "desactivado") + ". /clan chat enable: solo hablas con tu clan "
                + "y dejas de ver el chat público. /clan chat disable: vuelves al público.", ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int toggle(CommandSourceStack src, boolean on) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("Solo un jugador puede usar /clan chat."));
            return 0;
        }
        UUID uuid = player.getUUID();
        if (PadClans.clanIdOf(uuid) == null) {
            forget(uuid);
            src.sendFailure(Component.literal("Solo los miembros de un clan pueden usar /clan chat."));
            return 0;
        }
        if (on == enabled(uuid)) {
            src.sendFailure(Component.literal(on ? "Ya tienes el chat del clan activado." : "Ya estás en el chat público."));
            return 0;
        }
        set(uuid, on);
        src.sendSuccess(() -> tag(on
                ? "Chat del clan ACTIVADO: lo que escribas solo lo lee tu clan y ya no ves el chat público. /clan chat disable para volver."
                : "Chat del clan desactivado: vuelves al chat público.", on ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
        return 1;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Repartir
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Un mensaje de chat que alguien acaba de escribir (desde la cabeza de broadcastChatMessage). true si es del chat del
     * clan: entonces ya se repartió (o se rechazó) y NO debe seguir al chat público.
     */
    public static boolean intercept(ServerPlayer sender, String text) {
        if (sender == null || text == null || !enabled(sender.getUUID())) return false;
        MinecraftServer server = sender.getServer();
        if (server == null) return true;
        if (server.isSameThread()) route(sender, text);
        else server.execute(() -> route(sender, text));
        return true;
    }

    /** Si llega por el ServerChatEvent de Forge (por si el mixin no estuviera): tampoco va al público. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerChat(ServerChatEvent event) {
        if (event.getPlayer() != null && enabled(event.getPlayer().getUUID())) {
            event.setCanceled(true);
            intercept(event.getPlayer(), event.getRawText());
        }
    }

    /** Desde el AsyncPlayerChatEvent de Bukkit (Mohist): true = cancelarlo (es del chat del clan). */
    private static boolean divertFromBukkit(UUID sender, String text) {
        if (!enabled(sender)) return false;
        MinecraftServer server = PadServer.server();
        if (server != null) {
            server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayer(sender);
                if (p != null) route(p, text);
            });
        }
        return true;
    }

    private static void route(ServerPlayer sender, String raw) {
        UUID uuid = sender.getUUID();
        String text = raw.strip();
        if (text.isEmpty()) return;
        // el mismo mensaje por dos caminos (mixin y evento) en el mismo instante: una sola vez
        long now = System.currentTimeMillis();
        Last last = LAST_ROUTED.get(uuid);
        if (last != null && now - last.at() < 250 && text.equals(last.text())) return;
        LAST_ROUTED.put(uuid, new Last(now, text));
        String id = PadClans.clanIdOf(uuid);
        if (id == null) {
            // ya no está en un clan: se le quita y no se manda a nadie (tampoco al público)
            forget(uuid);
            sender.sendSystemMessage(Component.literal("Ya no estás en un clan: el chat del clan se ha desactivado. Vuelve a escribirlo.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (spam(uuid, now)) {
            sender.sendSystemMessage(Component.literal("Vas muy rápido: espera un momento.").withStyle(ChatFormatting.RED));
            return;
        }
        Boolean muted = net.tierrasfantasticas.tfclient.server.TFBukkit.essentialsMuted(uuid);
        if (Boolean.TRUE.equals(muted)) {
            sender.sendSystemMessage(Component.literal("Estás silenciado: no puedes escribir en el chat del clan.").withStyle(ChatFormatting.RED));
            return;
        }
        JsonObject c = PadClans.clan(id);
        String clanTag = TFJson.str(c, "etiqueta", "?");
        int rgb = PadClans.rgbOf(c);
        MinecraftServer server = sender.getServer();
        List<ServerPlayer> online = server.getPlayerList().getPlayers();
        List<ServerPlayer> to = recipients(uuid, online, p -> server.getPlayerList().isOp(p.getGameProfile()));
        String name = sender.getGameProfile().getName();
        Set<UUID> members = Set.copyOf(PadClans.members(c));
        for (ServerPlayer p : to) {
            boolean member = members.contains(p.getUUID());
            p.sendSystemMessage(line(clanTag, rgb, name, text, !member));
        }
        TFClient.LOGGER.info("[Clan {}] {}: {}", clanTag, name, text);
        if (TEST) {
            List<String> names = new ArrayList<>();
            for (ServerPlayer p : to) names.add(p.getGameProfile().getName());
            TEST_LOG.add("clan " + clanTag + " «" + text + "» → " + String.join(", ", names));
        }
    }

    /**
     * La regla estricta: un mensaje del chat del clan del que escribe solo lo reciben los conectados de SU clan (él
     * incluido) y los operadores. Ni los de otros clanes ni los que no tienen clan.
     */
    static <P> List<P> recipients(UUID sender, Collection<P> online, Predicate<P> op) {
        List<P> out = new ArrayList<>();
        String id = PadClans.clanIdOf(sender);
        if (id == null) return out;
        Set<UUID> members = Set.copyOf(PadClans.members(PadClans.clan(id)));
        for (P p : online) {
            UUID u = uuidOf(p);
            if (u == null) continue;
            if (members.contains(u) && id.equals(PadClans.clanIdOf(u)) || op.test(p)) out.add(p);
        }
        return out;
    }

    private static UUID uuidOf(Object p) {
        if (p instanceof ServerPlayer sp) return sp.getUUID();
        if (p instanceof UUID u) return u;
        return null;
    }

    /** «[Clan TAG] Nombre: mensaje» (para un operador de fuera del clan, en gris, para que sepa que es de otro clan). */
    private static Component line(String clanTag, int rgb, String name, String text, boolean spy) {
        Style tagStyle = spy ? Style.EMPTY.withColor(ChatFormatting.DARK_GRAY) : Style.EMPTY.withColor(TextColor.fromRgb(rgb));
        MutableComponent out = Component.literal("[Clan ").withStyle(spy ? ChatFormatting.DARK_GRAY : ChatFormatting.GOLD)
                .append(Component.literal(clanTag).withStyle(tagStyle))
                .append(Component.literal("] ").withStyle(spy ? ChatFormatting.DARK_GRAY : ChatFormatting.GOLD))
                .append(Component.literal(name).withStyle(spy ? ChatFormatting.GRAY : ChatFormatting.WHITE))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(text).withStyle(spy ? ChatFormatting.GRAY : ChatFormatting.AQUA));
        return out;
    }

    private static Component tag(String text, ChatFormatting color) {
        return Component.literal("[Clan] ").withStyle(ChatFormatting.GOLD).append(Component.literal(text).withStyle(color));
    }

    private static boolean spam(UUID uuid, long now) {
        ArrayDeque<Long> q = RECENT.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > BURST_MS) q.pollFirst();
            if (q.size() >= BURST) return true;
            q.addLast(now);
            return false;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Filtro del chat público (mixin ServerPlayerClanChatMixin)
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * ¿Hay que ocultarle a «to» este mensaje? Solo el chat de jugadores (CHAT y /me) de OTRO jugador cuando «to» tiene
     * el chat del clan puesto; los avisos del sistema, /say del servidor y los /msg siguen llegando.
     */
    public static boolean blockPublic(ServerPlayer to, OutgoingChatMessage message, ChatType.Bound bound) {
        if (!(message instanceof OutgoingChatMessage.Player p)) return false;
        UUID from = p.message().link().sender();
        if (!isPublic(to, bound)) return false;
        if (!hidesPublic(to.getUUID(), from)) {
            if (TEST) TEST_LOG.add("público a " + to.getGameProfile().getName() + ": entregado");
            // (prueba) el remitente de mentira no existe en el cliente: Minecraft lo desconectaría («chat validation
            // failed»); la decisión ya está apuntada, el paquete no se manda
            return testDry;
        }
        if (TEST) TEST_LOG.add("público a " + to.getGameProfile().getName() + ": oculto");
        return true;
    }

    /** Mientras testPublic manda su mensaje de un jugador que no existe. */
    private static volatile boolean testDry;

    private static boolean isPublic(ServerPlayer to, ChatType.Bound bound) {
        try {
            return to.server.registryAccess().registryOrThrow(Registries.CHAT_TYPE).getResourceKey(bound.chatType())
                    .map(k -> k.equals(ChatType.CHAT) || k.equals(ChatType.EMOTE_COMMAND)).orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Prueba en el juego (BossTest «oficios»)
    // ---------------------------------------------------------------------------------------------------------------

    private static final UUID T_B = UUID.nameUUIDFromBytes("tf-chat-b".getBytes()), T_C = UUID.nameUUIDFromBytes("tf-chat-c".getBytes()),
            T_D = UUID.nameUUIDFromBytes("tf-chat-d".getBytes()), T_E = UUID.nameUUIDFromBytes("tf-chat-e".getBytes());

    /**
     * Prepara la prueba: el jugador sin clan no puede usar /clan chat; luego un clan con él y B (de mentira), otro clan
     * con C, D sin clan y E operador; lo activa con el comando y comprueba la regla estricta de destinatarios.
     */
    public static String testSetup(ServerPlayer me) {
        StringBuilder out = new StringBuilder();
        MinecraftServer server = me.getServer();
        UUID uuid = me.getUUID();
        String old = PadClans.clanIdOf(uuid);
        if (old != null) PadClans.clans().remove(old);
        PadClans.STORE_IMPL.player(uuid).remove("clan");
        forget(uuid);
        server.getCommands().performPrefixedCommand(me.createCommandSourceStack(), "clan chat enable");
        out.append("sin clan, /clan chat enable → ").append(enabled(uuid) ? "ACTIVADO (MAL)" : "no deja (bien)");
        JsonObject a = new JsonObject();
        a.addProperty("nombre", "Clan del Chat");
        a.addProperty("etiqueta", "CHT");
        a.addProperty("color", "#55FF55");
        a.addProperty("lider", uuid.toString());
        com.google.gson.JsonArray am = new com.google.gson.JsonArray();
        am.add(uuid.toString());
        am.add(T_B.toString());
        a.add("miembros", am);
        PadClans.clans().add("pruebachat1", a);
        JsonObject b = new JsonObject();
        b.addProperty("nombre", "Otro Clan");
        b.addProperty("etiqueta", "OTR");
        b.addProperty("color", "#FF5555");
        b.addProperty("lider", T_C.toString());
        com.google.gson.JsonArray bm = new com.google.gson.JsonArray();
        bm.add(T_C.toString());
        b.add("miembros", bm);
        PadClans.clans().add("pruebachat2", b);
        PadClans.STORE_IMPL.player(uuid).addProperty("clan", "pruebachat1");
        PadClans.STORE_IMPL.player(T_B).addProperty("clan", "pruebachat1");
        PadClans.STORE_IMPL.player(T_C).addProperty("clan", "pruebachat2");
        PadClans.STORE_IMPL.changed();
        server.getCommands().performPrefixedCommand(me.createCommandSourceStack(), "clan chat enable");
        out.append("; en un clan, /clan chat enable → ").append(enabled(uuid) ? "activado (bien)" : "NO (MAL)");
        List<UUID> online = List.of(uuid, T_B, T_C, T_D, T_E);
        List<UUID> to = recipients(uuid, online, u -> u.equals(T_E));
        boolean strict = to.size() == 3 && to.contains(uuid) && to.contains(T_B) && to.contains(T_E);
        out.append("; destinatarios de mi chat del clan: yo ").append(to.contains(uuid)).append(", mi clan ").append(to.contains(T_B))
                .append(", otro clan ").append(to.contains(T_C)).append(", sin clan ").append(to.contains(T_D)).append(", operador ")
                .append(to.contains(T_E)).append(strict ? " (bien)" : " (MAL)");
        List<UUID> theirs = recipients(T_C, online, u -> u.equals(T_E));
        out.append("; el chat del otro clan: lo veo yo ").append(theirs.contains(uuid)).append(theirs.contains(uuid) ? " (MAL)" : " (bien)");
        TEST_LOG.clear();
        return out.toString();
    }

    /** Un mensaje «público» de otro jugador (de mentira) para ver si al que tiene el chat del clan le llega. */
    public static void testPublic(MinecraftServer server, String text) {
        var msg = net.minecraft.network.chat.PlayerChatMessage.unsigned(T_D, text);
        testDry = true;
        try {
            server.getPlayerList().broadcastChatMessage(msg, server.createCommandSourceStack(),
                    ChatType.bind(ChatType.CHAT, server.registryAccess(), Component.literal("Dani_Publico")));
        } finally {
            testDry = false;
        }
    }

    /** Lo deja como estaba: sin los clanes de prueba y con el chat público. */
    public static void testCleanup(ServerPlayer me) {
        forget(me.getUUID());
        PadClans.clans().remove("pruebachat1");
        PadClans.clans().remove("pruebachat2");
        for (UUID u : List.of(me.getUUID(), T_B, T_C)) {
            JsonObject p = PadClans.STORE_IMPL.playerIfAny(u);
            if (p != null) p.remove("clan");
        }
        PadClans.STORE_IMPL.changed();
    }
}
