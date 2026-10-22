package net.tierrasfantasticas.tfclient.server;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * (1.3.112, el dueño: «los bots: al hacerles /tpa un jugador por curiosidad, dice "entidad no encontrada" y se dará cuenta
 * de que es un bot; que puedan enviarles /tpa pero el bot la rechaza a los 5 segundos (el /tp es de operador: con ese no
 * hay problema) y que puedan recibir /msg, pero no van a responder») /tpa, /tpahere y /msg a un bot (TFBots) no llegan a
 * EssentialsX ni a Minecraft: aquí se contestan con los mismos mensajes que EssentialsX (su idioma y sus colores, de su
 * config.yml: «locale» y «message-colors»), y la petición se deniega a los 5 s. /r tras escribirle a un bot, igual.
 * <p>Entra por dos sitios: lo que escribe el jugador (mixin/BotCommandsMixin, en performChatCommand: después de validar
 * la firma del chat y antes de que lo vean los plugins) y, por si un comando llega por otro camino, el CommandEvent de
 * Forge.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class BotCommands {
    private static final Set<String> TPA = Set.of("tpa", "tpask", "call", "etpa", "etpask", "ecall", "tpahere", "etpahere");
    private static final Set<String> MSG = Set.of("msg", "tell", "w", "whisper", "m", "t", "pm", "emsg", "etell", "ewhisper",
            "em", "et", "ew", "epm", "dm", "message");
    private static final Set<String> REPLY = Set.of("r", "reply", "er", "ereply");
    private static final Set<String> CANCEL = Set.of("tpacancel", "etpacancel");
    /** Los 5 s hasta que el bot deniega la petición. */
    private static final int DENY_TICKS = 100;

    /** Las peticiones a bots que esperan: jugador → (bot → tick en que se deniega). */
    private static final Map<UUID, Map<String, Long>> PENDING = new HashMap<>();
    /** A qué bot escribió cada jugador por última vez (para /r). */
    private static final Map<UUID, String> LAST_BOT = new HashMap<>();
    private static long tick;

    private BotCommands() {}

    // ------------------------------------------------------------------------------------------------- entrada

    /** Lo que escribe el jugador (sin la «/»). true si era para un bot y ya se contestó. */
    public static boolean handle(ServerPlayer player, String raw) {
        if (player == null || raw == null || TFBots.count() == 0 && LAST_BOT.isEmpty()) return false;
        String line = raw.startsWith("/") ? raw.substring(1) : raw;
        String[] parts = line.trim().split("\\s+", 3);
        if (parts.length == 0 || parts[0].isEmpty()) return false;
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        int colon = cmd.indexOf(':');
        if (colon >= 0) cmd = cmd.substring(colon + 1); // essentials:tpa, minecraft:msg…
        try {
            if (TPA.contains(cmd) && parts.length >= 2) {
                TFBots.Bot bot = bot(parts[1]);
                if (bot == null) return false;
                tpa(player, bot);
                return true;
            }
            if (CANCEL.contains(cmd)) return cancel(player, parts.length >= 2 ? parts[1] : null);
            if (MSG.contains(cmd) && parts.length >= 2) {
                TFBots.Bot bot = bot(parts[1]);
                if (bot == null) {
                    LAST_BOT.remove(player.getUUID()); // le escribe a otro: /r ya no es para el bot
                    return false;
                }
                if (parts.length < 3 || parts[2].isBlank()) return false; // sin mensaje: que conteste el plugin (uso)
                msg(player, bot.name(), parts[2]);
                return true;
            }
            if (REPLY.contains(cmd) && parts.length >= 2) {
                String last = LAST_BOT.get(player.getUUID());
                if (last == null) return false;
                msg(player, last, line.trim().split("\\s+", 2)[1]);
                return true;
            }
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Bots: no se pudo contestar «/{}» de {}", line, player.getScoreboardName(), e);
        }
        return false;
    }

    /** Si un comando llega por el dispatcher (otro mod, la consola en nombre de un jugador, la prueba). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCommand(CommandEvent event) {
        if (!(event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player)) return;
        if (handle(player, event.getParseResults().getReader().getString())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        tick++;
        MinecraftServer server = event.getServer();
        for (Iterator<Map.Entry<UUID, Map<String, Long>>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Map<String, Long>> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            for (Iterator<Map.Entry<String, Long>> jt = e.getValue().entrySet().iterator(); jt.hasNext(); ) {
                Map.Entry<String, Long> req = jt.next();
                if (tick < req.getValue()) continue;
                jt.remove();
                if (player != null) player.sendSystemMessage(text().deniedFrom(req.getKey()));
            }
            if (e.getValue().isEmpty()) it.remove();
        }
    }

    /** Las peticiones a bots de ese jugador que aún esperan (la prueba en el juego). */
    public static int pending(UUID player) {
        Map<String, Long> mine = PENDING.get(player);
        return mine == null ? 0 : mine.size();
    }

    // ------------------------------------------------------------------------------------------------- respuestas

    private static void tpa(ServerPlayer player, TFBots.Bot bot) {
        Map<String, Long> mine = PENDING.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        Messages m = text();
        if (mine.containsKey(bot.name())) {
            player.sendSystemMessage(m.sentAlready(bot.name()));
            return;
        }
        mine.put(bot.name(), tick + DENY_TICKS);
        player.sendSystemMessage(m.sent(bot.name()));
        player.sendSystemMessage(m.typeCancel());
    }

    private static boolean cancel(ServerPlayer player, String target) {
        Map<String, Long> mine = PENDING.get(player.getUUID());
        if (mine == null || mine.isEmpty()) return false;
        Messages m = text();
        if (target != null) {
            TFBots.Bot bot = bot(target);
            if (bot == null || mine.remove(bot.name()) == null) return false;
            player.sendSystemMessage(m.cancelledWith(bot.name()));
            return true;
        }
        // sin nombre: Essentials cancela todas; si además hay alguna a un jugador de verdad, que la cancele él también
        List<String> bots = new ArrayList<>(mine.keySet());
        mine.clear();
        for (String b : bots) player.sendSystemMessage(m.cancelledTo(b));
        return true;
    }

    private static void msg(ServerPlayer player, String bot, String message) {
        LAST_BOT.put(player.getUUID(), bot);
        player.sendSystemMessage(text().msg(bot, message));
    }

    private static TFBots.Bot bot(String name) {
        for (TFBots.Bot b : TFBots.online()) if (b.name().equalsIgnoreCase(name)) return b;
        return null;
    }

    // ------------------------------------------------------------------------------------------------- textos

    private static Messages messages;

    private static Messages text() {
        if (messages == null) messages = Messages.load();
        return messages;
    }

    /** Los mensajes de EssentialsX (messages_es / messages, rama 2.x) con sus colores. */
    private record Messages(boolean es, int primary, int secondary) {
        static Messages load() {
            boolean es = Locale.getDefault().getLanguage().equals("es");
            int primary = 0xFFAA00, secondary = 0xFF5555;
            Path cfg = Path.of("plugins", "Essentials", "config.yml");
            try {
                if (Files.exists(cfg)) {
                    boolean colors = false;
                    for (String l : Files.readAllLines(cfg, StandardCharsets.UTF_8)) {
                        String t = l.trim();
                        if (t.startsWith("#")) continue;
                        if (l.startsWith("locale:")) {
                            String v = unquote(t.substring(7));
                            if (!v.isEmpty()) es = v.toLowerCase(Locale.ROOT).startsWith("es");
                        }
                        if (l.startsWith("message-colors:")) colors = true;
                        else if (colors && !l.startsWith(" ") && !t.isEmpty()) colors = false;
                        if (colors && t.startsWith("primary:")) primary = color(unquote(t.substring(8)), primary);
                        if (colors && t.startsWith("secondary:")) secondary = color(unquote(t.substring(10)), secondary);
                    }
                }
            } catch (Exception e) {
                TFClient.LOGGER.warn("TF Bots: no se pudo leer {} (los mensajes van con los de serie)", cfg, e);
            }
            return new Messages(es, primary, secondary);
        }

        private static String unquote(String s) {
            s = s.trim();
            int hash = s.indexOf(" #");
            if (hash >= 0) s = s.substring(0, hash).trim();
            if (s.length() >= 2 && (s.startsWith("'") && s.endsWith("'") || s.startsWith("\"") && s.endsWith("\""))) s = s.substring(1, s.length() - 1);
            return s;
        }

        private static int color(String v, int fallback) {
            try {
                if (v.startsWith("#") && v.length() == 7) return Integer.parseInt(v.substring(1), 16);
                ChatFormatting f = ChatFormatting.getByName(v);
                if (f != null && f.getColor() != null) return f.getColor();
                if (v.length() == 1) {
                    f = ChatFormatting.getByCode(v.charAt(0));
                    if (f != null && f.getColor() != null) return f.getColor();
                }
            } catch (Exception ignored) {
                // el de serie
            }
            return fallback;
        }

        private MutableComponent p(String s) {
            return Component.literal(s).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(primary)));
        }

        private MutableComponent s(String s) {
            return Component.literal(s).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(secondary)));
        }

        private static MutableComponent dark(String s) {
            return Component.literal(s).withStyle(ChatFormatting.DARK_RED);
        }

        private static MutableComponent plain(String s) {
            return Component.literal(s).withStyle(ChatFormatting.RESET);
        }

        // requestSent
        Component sent(String bot) {
            return es ? p("La petición ha sido enviada a").append(s(" " + bot)).append(p("."))
                    : p("Request sent to").append(s(" " + bot)).append(p("."));
        }

        // typeTpacancel
        Component typeCancel() {
            return es ? p("Para cancelar esta petición, teclea ").append(s("/tpacancel")).append(p("."))
                    : p("To cancel this request, type ").append(s("/tpacancel")).append(p("."));
        }

        // requestSentAlready
        Component sentAlready(String bot) {
            return es ? dark("Ya has enviado una peticion de teletransporte a " + bot).append(dark("."))
                    : dark("You have already sent " + bot).append(dark(" a teleport request."));
        }

        // requestDeniedFrom
        Component deniedFrom(String bot) {
            return es ? s(bot + " ").append(p("ha denegado tu teletransportación."))
                    : s(bot + " ").append(p("denied your teleport request."));
        }

        // teleportRequestSpecificCancelled
        Component cancelledWith(String bot) {
            return es ? p("Solicitud de teletransporte pendiente con").append(s(" " + bot)).append(p(" cancelada."))
                    : p("Pending teleport request with").append(s(" " + bot)).append(p(" cancelled."));
        }

        // teleportRequestCancelled
        Component cancelledTo(String bot) {
            return es ? p("Tu solicitud de teletransporte hacia ").append(s(bot)).append(p(" fue cancelada."))
                    : p("Your teleport request to ").append(s(bot)).append(p(" was cancelled."));
        }

        // msgFormat con {0} = meSender, {1} = el bot, {2} = el mensaje (lo que ve quien lo manda)
        Component msg(String bot, String message) {
            return es ? p("[").append(plain("yo")).append(p("] ")).append(p("[")).append(plain(bot)).append(p("] ")).append(plain(message))
                    : p("[").append(s("me")).append(p(" -> ")).append(s(bot)).append(p("] ")).append(plain(message));
        }
    }
}
