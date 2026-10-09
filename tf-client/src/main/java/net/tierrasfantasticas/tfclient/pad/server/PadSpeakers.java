package net.tierrasfantasticas.tfclient.pad.server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.pad.PadSpeakerNet;

/**
 * Los altavoces de la app Música en el servidor: qué suena en cada uno (link, título, cuándo empezó) y a quién se le ha
 * contado. Cada segundo mira quién está cerca (RANGE bloques, misma dimensión): a los que entran se les cuenta (con el
 * punto por el que va la canción), a los que salen se les dice que paren. Solo pasa el link: el audio lo baja cada
 * cliente. Si el que pone la música se va, cambia de mundo, se apaga o la canción se acaba, se para para todos.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class PadSpeakers {
    /** Hasta dónde llega (bloques). El cliente apaga el sonido poco a poco hasta esta distancia. */
    public static final double RANGE = 32;

    private record State(String url, String title, String artist, long originMs, long durationMs) {
        long position() {
            return System.currentTimeMillis() - originMs;
        }
    }

    private static final Map<UUID, State> SPEAKERS = new HashMap<>();
    private static final Map<UUID, Set<UUID>> TOLD = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();
    /** Cuándo cambió de canción por última vez cada altavoz. */
    private static final Map<UUID, Long> URL_AT = new HashMap<>();
    private static int ticks;

    private PadSpeakers() {}

    /**
     * Lo que manda el que pone la música. Parar vale siempre; lo demás, como mucho 4 veces por segundo y una canción
     * nueva cada 2 s (lo que se salte llega con el recordatorio de cada 10 s del cliente).
     */
    public static void update(ServerPlayer p, PadSpeakerNet.Up m) {
        if (!m.playing()) {
            stop(p.getUUID());
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < 250) return;
        String url = m.url().trim();
        if (!(url.startsWith("https://") || url.startsWith("http://")) || url.length() > 500) return;
        State old = SPEAKERS.get(p.getUUID());
        Long changed = URL_AT.get(p.getUUID());
        boolean newUrl = old == null || !old.url.equals(url);
        if (newUrl && changed != null && now - changed < 2000) return;
        LAST.put(p.getUUID(), now);
        if (newUrl) URL_AT.put(p.getUUID(), now);
        State s = new State(url, cut(m.title()), cut(m.artist()), now - m.positionMs(), m.durationMs());
        SPEAKERS.put(p.getUUID(), s);
        // a los que ya lo oían, lo nuevo (otra canción, otro punto)
        Set<UUID> told = TOLD.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
        MinecraftServer server = p.getServer();
        for (UUID u : told) {
            ServerPlayer l = server == null ? null : server.getPlayerList().getPlayer(u);
            if (l != null) PadSpeakerNet.toPlayer(l, down(p.getUUID(), s));
        }
        tell(p);
    }

    private static String cut(String s) {
        return s.length() > 60 ? s.substring(0, 60) : s;
    }

    private static PadSpeakerNet.Down down(UUID speaker, State s) {
        return new PadSpeakerNet.Down(speaker, true, s.url, s.title, s.artist, s.position(), s.durationMs);
    }

    /** Para el altavoz: a todos los que lo oían, que paren. */
    static void stop(UUID speaker) {
        SPEAKERS.remove(speaker);
        Set<UUID> told = TOLD.remove(speaker);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (told == null || server == null) return;
        for (UUID u : told) {
            ServerPlayer l = server.getPlayerList().getPlayer(u);
            if (l != null) PadSpeakerNet.toPlayer(l, new PadSpeakerNet.Down(speaker, false, "", "", "", 0, 0));
        }
    }

    /** A quién hay que contárselo o decirle que pare, según quién está cerca ahora. */
    private static void tell(ServerPlayer sp) {
        State s = SPEAKERS.get(sp.getUUID());
        if (s == null) return;
        Set<UUID> told = TOLD.computeIfAbsent(sp.getUUID(), k -> new HashSet<>());
        MinecraftServer server = sp.getServer();
        // los que se fueron, cambiaron de mundo o se alejaron
        for (Iterator<UUID> it = told.iterator(); it.hasNext(); ) {
            UUID u = it.next();
            ServerPlayer l = server == null ? null : server.getPlayerList().getPlayer(u);
            if (l == null || l.level() != sp.level() || l.distanceToSqr(sp) > (RANGE + 4) * (RANGE + 4)) {
                it.remove();
                if (l != null) PadSpeakerNet.toPlayer(l, new PadSpeakerNet.Down(sp.getUUID(), false, "", "", "", 0, 0));
            }
        }
        // los que llegan
        for (ServerPlayer l : sp.serverLevel().players()) {
            if (l == sp || told.contains(l.getUUID()) || l.distanceToSqr(sp) > RANGE * RANGE) continue;
            told.add(l.getUUID());
            PadSpeakerNet.toPlayer(l, down(sp.getUUID(), s));
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SPEAKERS.isEmpty() || ++ticks % 20 != 0) return;
        MinecraftServer server = event.getServer();
        for (UUID u : Set.copyOf(SPEAKERS.keySet())) {
            ServerPlayer sp = server.getPlayerList().getPlayer(u);
            State s = SPEAKERS.get(u);
            // se fue, o la canción se acabó hace rato sin que avisara de la siguiente
            if (sp == null || s == null || (s.durationMs > 0 && s.position() > s.durationMs + 15_000)) {
                stop(u);
                continue;
            }
            tell(sp);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID u = event.getEntity().getUUID();
        stop(u);
        LAST.remove(u);
        URL_AT.remove(u);
        for (Set<UUID> told : TOLD.values()) told.remove(u);
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        // al cambiar de mundo los demás dejan de oírlo; si sigue sonando, el siguiente aviso lo cuenta en el mundo nuevo
        Set<UUID> told = TOLD.get(event.getEntity().getUUID());
        if (told != null && event.getEntity() instanceof ServerPlayer sp) tell(sp);
    }
}
