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

    /** Lo que suena en una zona. */
    public record Song(String url, String title, String artist, long durationMs, long originMs, int volume, String by) {
        long position(long now) {
            long p = now - originMs;
            return durationMs > 0 ? Math.floorMod(p, durationMs) : Math.max(0, p);
        }
    }

    /** Lo que se le contó a cada jugador: la zona y su link (para saber si cambió). */
    private record Told(UUID claim, String url, int volume, long at) {}

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
                (int) TFJson.num(o, "volumen", 80), TFJson.str(o, "por", ""));
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
            if (s == null) {
                if (t != null) {
                    PadSpeakerNet.toPlayer(p, new PadSpeakerNet.Zone(t.claim, false, "", "", "", 0, 0, 0));
                    TOLD.remove(p.getUUID());
                }
                continue;
            }
            boolean same = t != null && t.claim.equals(c.getClaimId()) && t.url.equals(s.url());
            // al entrar, si cambió la canción o el volumen, y un recordatorio cada 10 s (para ir a la par)
            if (same && t.volume == s.volume() && now - t.at < 10_000) continue;
            if (t != null && !t.claim.equals(c.getClaimId())) PadSpeakerNet.toPlayer(p, new PadSpeakerNet.Zone(t.claim, false, "", "", "", 0, 0, 0));
            PadSpeakerNet.toPlayer(p, new PadSpeakerNet.Zone(c.getClaimId(), true, s.url(), s.title(), s.artist(), s.position(now), s.durationMs(), s.volume()));
            TOLD.put(p.getUUID(), new Told(c.getClaimId(), s.url(), s.volume(), now));
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
        if (t != null && event.getEntity() instanceof ServerPlayer p) PadSpeakerNet.toPlayer(p, new PadSpeakerNet.Zone(t.claim, false, "", "", "", 0, 0, 0));
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
