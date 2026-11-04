/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.claims.data.Claim;
import net.tierrasfantasticas.tfclient.claims.data.ClaimManager;
import net.tierrasfantasticas.tfclient.pad.PadSpeakerNet;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * (1.3.160, el dueño: «quiero que en la prote se pueda poner música, con la función que hace el reproductor; la claim
 * debe tener su apartado para meter música; la música solo se oirá dentro de la zona: fuera de la zona se quita; hazlo
 * bien sin romper algo») La música de las protecciones.
 * <ul>
 *   <li>Pad › Protecciones › (una zona) › MÚSICA: ELEGIR abre la app Música para tocar una canción (de un link o de tu
 *   carpeta, como el altavoz); QUITAR la quita; el volumen, de 10 en 10. Solo el dueño de la zona (o quien la gestiona:
 *   los líderes y colíderes en las del clan, los administradores).</li>
 *   <li>Suena en bucle, a la par para todos, para los que están DENTRO de la zona (Claim.contains: el cuadrado de la
 *   zona, de su altura hacia abajo y hasta el cielo). Dos veces por segundo se mira quién está dentro: al entrar
 *   empieza (por donde va), al salir se apaga poco a poco. Sin altavoz, sin depender de quién la puso: la zona suena
 *   aunque su dueño no esté.</li>
 *   <li>Por la red solo va el link: cada TF Client la baja (o, si es de una carpeta, del servidor: la sube el que la
 *   pone, como las de los discos, y se guarda con ellas en &lt;mundo&gt;/tfclient/compartidas/).</li>
 *   <li>Quien no la quiera oír: Ajustes del pad › Oír la música de otros (lo mismo que los altavoces).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class PadZoneMusic {
    static final JsonStore STORE_IMPL = new JsonStore("musica_zonas.json");
    public static final net.tierrasfantasticas.tfclient.pad.PadServer.Store STORE = STORE_IMPL;

    /**
     * Lo que suena en una zona. (1.3.171, el dueño: «en la música de las protes pon tipo el reproductor: la barra de
     * progreso de la canción, el volumen, que se repita indefinidamente, todo eso») pausedMs: dónde se pausó (−1 si
     * suena); repeat: en bucle (lo de siempre) o una vez (al acabar, se calla hasta que se vuelva a dar a reproducir).
     */
    public record Song(String url, String title, String artist, long durationMs, long originMs, int volume, String by, long pausedMs,
                       boolean repeat) {
        public boolean paused() {
            return pausedMs >= 0;
        }

        public long position(long now) {
            if (pausedMs >= 0) return pausedMs;
            long p = now - originMs;
            if (durationMs <= 0) return Math.max(0, p);
            return repeat ? Math.floorMod(p, durationMs) : Math.max(0, Math.min(p, durationMs));
        }

        /** ¿Se oye ahora? (ni en pausa ni acabada si no se repite) */
        public boolean playing(long now) {
            return pausedMs < 0 && (repeat || durationMs <= 0 || now - originMs < durationMs);
        }

        /** Acabó (una vez, sin repetir). */
        public boolean ended(long now) {
            return pausedMs < 0 && !repeat && durationMs > 0 && now - originMs >= durationMs;
        }
    }

    /** Lo que se le contó a cada jugador: la zona, su link, el volumen y desde cuándo suena (para saber si cambió). */
    private record Told(UUID claim, String url, int volume, long at, long origin) {}

    private static final Map<UUID, Told> TOLD = new HashMap<>();
    /** El que pone una canción de su carpeta que el servidor aún no tiene: la zona y la canción, hasta que la suba. */
    private record Pending(UUID claim, PadSpeakerNet.ZoneSet song, long since) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static int ticks;

    private PadZoneMusic() {}

    // ---------------------------------------------------------------------------------------------------------------
    // Datos
    // ---------------------------------------------------------------------------------------------------------------

    private static JsonObject zones() {
        return STORE_IMPL.obj(STORE_IMPL.root, "zonas");
    }

    /** La música de la zona, o null. */
    public static Song of(Claim c) {
        if (c == null) return null;
        JsonElement e = zones().get(c.getClaimId().toString());
        if (e == null || !e.isJsonObject()) return null;
        JsonObject o = e.getAsJsonObject();
        String url = TFJson.str(o, "url", "");
        if (url.isEmpty()) return null;
        return new Song(url, TFJson.str(o, "titulo", "?"), TFJson.str(o, "artista", ""), TFJson.num(o, "duracion", 0), TFJson.num(o, "inicio", 0),
                (int) TFJson.num(o, "volumen", 80), TFJson.str(o, "por", ""), TFJson.num(o, "pausa", -1), TFJson.bool(o, "repetir", true));
    }

    private static boolean canManage(ServerPlayer p, Claim c) {
        return c != null && (c.isOwner(p.getUUID()) || p.hasPermissions(2));
    }

    private static boolean validUrl(String url) {
        return (url.startsWith("https://") || url.startsWith("http://") || PadSpeakers.isLocal(url)) && url.length() <= 500;
    }

    /** Desde la app Música: la canción para esa zona. */
    public static void set(ServerPlayer p, PadSpeakerNet.ZoneSet m) {
        Claim c;
        try {
            c = ClaimManager.getInstance().findClaimById(UUID.fromString(m.claim()));
        } catch (IllegalArgumentException e) {
            return;
        }
        if (!canManage(p, c)) {
            TFPadNet.notice(p, "Solo el dueño de la zona le pone música.");
            return;
        }
        String url = m.url().trim();
        if (!validUrl(url)) {
            TFPadNet.notice(p, "Esa canción no se puede poner en la zona (pesa demasiado o no tiene link).");
            return;
        }
        if (PadSpeakers.isLocal(url)) {
            String hash = url.substring(PadSpeakerNet.LOCAL.length());
            if (!PadMusicShare.keep(hash)) {
                // de su carpeta y el servidor no la tiene: que la suba; se pone cuando llegue entera
                PENDING.put(p.getUUID(), new Pending(c.getClaimId(), m, System.currentTimeMillis()));
                PadSpeakers.askUpload(p, hash);
                TFPadNet.notice(p, "Subiendo «" + cut(m.title(), 40) + "» al servidor para la zona…");
                return;
            }
        }
        store(p, c, m);
    }

    private static void store(ServerPlayer p, Claim c, PadSpeakerNet.ZoneSet m) {
        JsonObject o = new JsonObject();
        o.addProperty("url", m.url().trim());
        o.addProperty("titulo", cut(m.title(), 60));
        o.addProperty("artista", cut(m.artist(), 60));
        o.addProperty("duracion", Math.max(0, m.durationMs()));
        o.addProperty("inicio", System.currentTimeMillis());
        Song old = of(c);
        o.addProperty("volumen", old == null ? 80 : old.volume());
        o.addProperty("repetir", old == null || old.repeat());
        o.addProperty("por", p.getGameProfile().getName());
        zones().add(c.getClaimId().toString(), o);
        STORE_IMPL.changed();
        TFPadNet.notice(p, "«" + cut(m.title(), 40) + "» suena ahora dentro de " + PadClaims.title(c) + ".");
        TFClient.LOGGER.info("TF Pad: {} pone «{}» de música en la zona {}", p.getGameProfile().getName(), m.title(), c.getClaimId());
    }

    /** ¿Espera este jugador a subir esta canción para una zona? (PadSpeakers deja pasar sus trozos) */
    static boolean waiting(ServerPlayer p, String hash) {
        Pending w = PENDING.get(p.getUUID());
        return w != null && w.song.url().endsWith(hash) && System.currentTimeMillis() - w.since < 10 * 60_000;
    }

    /** Llegó entera la canción que subía p: si era para una zona, se guarda con las de los discos y se pone. */
    static void uploaded(ServerPlayer p, String hash) {
        Pending w = PENDING.get(p.getUUID());
        if (w == null || !w.song.url().endsWith(hash)) return;
        PENDING.remove(p.getUUID());
        Claim c = ClaimManager.getInstance().findClaimById(w.claim);
        if (c == null || !canManage(p, c)) return;
        if (PadMusicShare.keep(hash)) store(p, c, w.song);
        else TFPadNet.notice(p, "No se pudo guardar la canción en el servidor. Inténtalo otra vez.");
    }

    /** QUITAR. */
    static void clear(ServerPlayer p, Claim c) {
        if (!canManage(p, c)) return;
        zones().remove(c.getClaimId().toString());
        STORE_IMPL.changed();
        TFPadNet.notice(p, "La zona ya no tiene música.");
    }

    /** El volumen (de 10 en 10, entre 10 y 100). */
    static void volume(ServerPlayer p, Claim c, int delta) {
        if (!canManage(p, c)) return;
        JsonElement e = zones().get(c.getClaimId().toString());
        if (e == null || !e.isJsonObject()) return;
        JsonObject o = e.getAsJsonObject();
        o.addProperty("volumen", Math.max(10, Math.min(100, TFJson.num(o, "volumen", 80) + delta)));
        STORE_IMPL.changed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // (1.3.171) El reproductor de la zona: pausa, saltar, repetir y volumen (la app de datos «zonamusica»)
    // ---------------------------------------------------------------------------------------------------------------

    private static JsonObject entry(Claim c) {
        JsonElement e = c == null ? null : zones().get(c.getClaimId().toString());
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    /** Pone la canción en ms (en pausa, se queda ahí; sonando, sigue desde ahí). */
    private static void seekTo(JsonObject o, Song s, long ms, long now) {
        long pos = Math.max(0, s.durationMs() > 0 ? Math.min(ms, s.durationMs() - 250) : ms);
        if (s.paused()) o.addProperty("pausa", pos);
        else o.addProperty("inicio", now - pos);
    }

    /** Pausa o sigue; si acabó (sin repetir), vuelve a empezar. */
    static String togglePause(Claim c, long now) {
        JsonObject o = entry(c);
        Song s = of(c);
        if (o == null || s == null) return "La zona no tiene música.";
        if (s.paused()) {
            o.addProperty("inicio", now - s.pausedMs());
            o.remove("pausa");
        } else if (s.ended(now)) {
            o.addProperty("inicio", now);
        } else {
            o.addProperty("pausa", s.position(now));
        }
        STORE_IMPL.changed();
        return null;
    }

    static String seek(Claim c, long ms, long now) {
        JsonObject o = entry(c);
        Song s = of(c);
        if (o == null || s == null) return "La zona no tiene música.";
        // saltar en una canción que ya acabó (sin repetir): vuelve a sonar desde ahí
        if (s.ended(now)) {
            o.addProperty("inicio", now - Math.max(0, Math.min(ms, Math.max(0, s.durationMs() - 250))));
        } else {
            seekTo(o, s, ms, now);
        }
        STORE_IMPL.changed();
        return null;
    }

    static String setVolume(Claim c, int volume) {
        JsonObject o = entry(c);
        if (o == null) return "La zona no tiene música.";
        o.addProperty("volumen", Math.max(0, Math.min(100, volume)));
        STORE_IMPL.changed();
        return null;
    }

    /** En bucle o una vez: la canción sigue por donde iba. */
    static String toggleRepeat(Claim c, long now) {
        JsonObject o = entry(c);
        Song s = of(c);
        if (o == null || s == null) return "La zona no tiene música.";
        long pos = s.position(now);
        if (s.durationMs() > 0 && pos >= s.durationMs()) pos = 0;
        o.addProperty("repetir", !s.repeat());
        if (!s.paused()) o.addProperty("inicio", now - pos);
        STORE_IMPL.changed();
        return null;
    }

    /** La app de datos del reproductor (pad/PadZonePlayerPage): la zona elegida con «zona:<id>» y sus botones. */
    public static final net.tierrasfantasticas.tfclient.pad.PadServer.DataApp APP = new net.tierrasfantasticas.tfclient.pad.PadServer.DataApp() {
        @Override
        public JsonObject data(ServerPlayer player) {
            JsonObject out = new JsonObject();
            Claim c = zoneOf(player);
            if (c == null) {
                out.addProperty("zona", "");
                return out;
            }
            long now = System.currentTimeMillis();
            out.addProperty("zona", c.getClaimId().toString());
            out.addProperty("nombre", PadClaims.title(c));
            out.addProperty("puede", canManage(player, c));
            Song s = of(c);
            out.addProperty("hay", s != null);
            if (s != null) {
                out.addProperty("titulo", s.title());
                out.addProperty("artista", s.artist());
                out.addProperty("por", s.by());
                out.addProperty("duracion", s.durationMs());
                out.addProperty("pos", s.position(now));
                out.addProperty("sonando", s.playing(now));
                out.addProperty("pausa", s.paused());
                out.addProperty("acabo", s.ended(now));
                out.addProperty("repetir", s.repeat());
                out.addProperty("volumen", s.volume());
            }
            return out;
        }

        @Override
        public JsonObject action(ServerPlayer player, String action) {
            if (action.startsWith("zona:")) {
                net.tierrasfantasticas.tfclient.pad.PadServer.put(player, "zonamusica.zona", action.substring(5));
                return null;
            }
            Claim c = zoneOf(player);
            if (c == null) return null;
            if (!canManage(player, c)) {
                TFPadNet.notice(player, "Solo el dueño de la zona controla su música.");
                return null;
            }
            long now = System.currentTimeMillis();
            String arg = action.contains(":") ? action.substring(action.indexOf(':') + 1) : "";
            String msg;
            try {
                msg = switch (action.contains(":") ? action.substring(0, action.indexOf(':')) : action) {
                    case "pausa" -> togglePause(c, now);
                    case "ir" -> seek(c, Long.parseLong(arg), now);
                    case "saltar" -> {
                        Song s = of(c);
                        yield s == null ? null : seek(c, s.position(now) + Long.parseLong(arg), now);
                    }
                    case "inicio" -> {
                        Song s = of(c);
                        if (s != null && s.paused()) togglePause(c, now);
                        yield seek(c, 0, now);
                    }
                    case "vol" -> setVolume(c, Integer.parseInt(arg));
                    case "repetir" -> toggleRepeat(c, now);
                    case "quitar" -> {
                        clear(player, c);
                        yield null;
                    }
                    default -> null;
                };
            } catch (NumberFormatException e) {
                msg = null;
            }
            if (msg != null) TFPadNet.notice(player, msg);
            return null;
        }
    };

    /** La zona que tiene abierta en el reproductor (y que aún existe). */
    private static Claim zoneOf(ServerPlayer player) {
        String id = net.tierrasfantasticas.tfclient.pad.PadServer.get(player, "zonamusica.zona", "");
        try {
            return id.isEmpty() ? null : ClaimManager.getInstance().findClaimById(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String cut(String s, int n) {
        return s.length() > n ? s.substring(0, n) : s;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Quién la oye: dos veces por segundo
    // ---------------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks % 10 != 0) return;
        MinecraftServer server = event.getServer();
        if (zones().size() == 0 && TOLD.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!TFPadNet.hasPad(p)) continue;
            Claim c = p.isSpectator() ? null : ClaimManager.getInstance().getClaimAt(p.level(), p.blockPosition());
            Song s = of(c);
            Told t = TOLD.get(p.getUUID());
            // (1.3.171) en pausa o acabada (una vez), como si no tuviera
            if (s == null || !s.playing(now)) {
                if (t != null) {
                    PadSpeakerNet.toPlayer(p, new PadSpeakerNet.Zone(t.claim, false, "", "", "", 0, 0, 0, true));
                    TOLD.remove(p.getUUID());
                }
                continue;
            }
            boolean same = t != null && t.claim.equals(c.getClaimId()) && t.url.equals(s.url()) && t.origin == s.originMs();
            // al entrar, si cambió la canción, el volumen o por dónde va (saltar, pausa, repetir), y un recordatorio cada
            // 10 s (para ir a la par)
            if (same && t.volume == s.volume() && now - t.at < 10_000) continue;
            if (t != null && !t.claim.equals(c.getClaimId())) PadSpeakerNet.toPlayer(p, new PadSpeakerNet.Zone(t.claim, false, "", "", "", 0, 0, 0, true));
            PadSpeakerNet.toPlayer(p, new PadSpeakerNet.Zone(c.getClaimId(), true, s.url(), s.title(), s.artist(), s.position(now), s.durationMs(), s.volume(),
                    s.repeat()));
            TOLD.put(p.getUUID(), new Told(c.getClaimId(), s.url(), s.volume(), now, s.originMs()));
        }
        // las canciones de las zonas no se borran por viejas (las de los discos se borran si ocupan mucho): se tocan
        if (ticks % (20 * 600) == 0) {
            for (Map.Entry<String, JsonElement> e : zones().entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                String url = TFJson.str(e.getValue().getAsJsonObject(), "url", "");
                if (PadSpeakers.isLocal(url)) {
                    java.nio.file.Path f = PadMusicShare.stored(url.substring(PadSpeakerNet.LOCAL.length()));
                    if (f != null) f.toFile().setLastModified(now);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TOLD.remove(event.getEntity().getUUID());
        PENDING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        // en el otro mundo, la de allí (o ninguna) en el próximo medio segundo
        Told t = TOLD.remove(event.getEntity().getUUID());
        if (t != null && event.getEntity() instanceof ServerPlayer p) PadSpeakerNet.toPlayer(p, new PadSpeakerNet.Zone(t.claim, false, "", "", "", 0, 0, 0, true));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Prueba en el juego
    // ---------------------------------------------------------------------------------------------------------------

    /** Para la prueba: pone esa canción (link) en la zona como si la eligiera p. */
    public static void testSet(ServerPlayer p, Claim c, String url, String title, long durationMs) {
        set(p, new PadSpeakerNet.ZoneSet(c.getClaimId().toString(), url, title, "Prueba", durationMs));
    }

    /** Para la prueba: la zona que el servidor le contó a p (o null si no oye ninguna). */
    public static UUID testHeard(ServerPlayer p) {
        Told t = TOLD.get(p.getUUID());
        return t == null ? null : t.claim;
    }

    public static void testClear(Claim c) {
        zones().remove(c.getClaimId().toString());
        STORE_IMPL.changed();
    }
}
