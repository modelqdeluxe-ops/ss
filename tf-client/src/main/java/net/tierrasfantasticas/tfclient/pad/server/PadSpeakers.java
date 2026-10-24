/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
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
        boolean local = isLocal(url);
        if (!(url.startsWith("https://") || url.startsWith("http://") || local) || url.length() > 500) return;
        // (1.3.114) una canción de su carpeta: si el servidor no la tiene, se la pide (la sube a trozos)
        if (local) needFrom(p, url.substring(PadSpeakerNet.LOCAL.length()));
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

    // ---------------------------------------------------------------------------------------------------------------
    // (1.3.114) Las canciones sin link (las de la carpeta de música del que la pone): pasan por el servidor
    // ---------------------------------------------------------------------------------------------------------------

    private static final class Upload {
        final UUID from;
        final int total, size;
        final byte[][] parts;
        int got;
        long lastMs = System.currentTimeMillis();

        Upload(UUID from, int total, int size) {
            this.from = from;
            this.total = total;
            this.size = size;
            this.parts = new byte[total][];
        }
    }

    /** Lo que se está subiendo, por hash; quién espera cada canción; lo que falta por mandar a cada uno. */
    private static final Map<String, Upload> UPLOADS = new HashMap<>();
    private static final Map<String, Set<UUID>> WAITING = new HashMap<>();
    private static final Map<UUID, java.util.ArrayDeque<PadSpeakerNet.DownPart>> SENDING = new HashMap<>();
    /** Como mucho, lo guardado en el servidor (se borran las más viejas). */
    private static final long MAX_STORED = 400L * 1024 * 1024;

    static boolean isLocal(String url) {
        return url.startsWith(PadSpeakerNet.LOCAL) && url.substring(PadSpeakerNet.LOCAL.length()).matches("[0-9a-f]{40}");
    }

    private static java.nio.file.Path dir() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null
                : server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("tfclient").resolve("altavoz");
    }

    /** Donde se guarda la canción de un altavoz (exista o no). */
    static java.nio.file.Path speakerFile(String hash) {
        java.nio.file.Path d = dir();
        return d == null ? null : d.resolve(hash + ".audio");
    }

    /** La canción: la de los discos (PadMusicShare) si está; si no, la del altavoz (exista o no). */
    private static java.nio.file.Path stored(String hash) {
        java.nio.file.Path shared = PadMusicShare.stored(hash);
        return shared != null ? shared : speakerFile(hash);
    }

    /** (1.3.134) Para el disco de una canción de su carpeta: que la suba. */
    static void askUpload(ServerPlayer p, String hash) {
        needFrom(p, hash);
    }

    private static void needFrom(ServerPlayer p, String hash) {
        java.nio.file.Path f = stored(hash);
        if (f == null || java.nio.file.Files.exists(f)) return;
        Upload u = UPLOADS.get(hash);
        if (u != null && System.currentTimeMillis() - u.lastMs < 30_000) return; // ya se está subiendo
        UPLOADS.remove(hash);
        PadSpeakerNet.toPlayer(p, new PadSpeakerNet.Need(hash));
    }

    /**
     * Un trozo de lo que sube el que pone la música: de una canción que suena en su altavoz o (1.3.134) de la que
     * comparte en un disco (PadMusicShare).
     */
    public static void uploaded(ServerPlayer p, PadSpeakerNet.UpPart m) {
        State st = SPEAKERS.get(p.getUUID());
        boolean speaking = st != null && st.url.equals(PadSpeakerNet.LOCAL + m.hash()) && isLocal(st.url);
        if (!speaking && !(m.hash().matches("[0-9a-f]{40}") && PadMusicShare.waiting(p, m.hash()))) return;
        long maxParts = (PadSpeakerNet.MAX_BYTES + PadSpeakerNet.UP_PART - 1) / PadSpeakerNet.UP_PART;
        if (m.total() <= 0 || m.total() > maxParts || m.index() < 0 || m.index() >= m.total() || m.size() <= 0
                || m.size() > PadSpeakerNet.MAX_BYTES || m.data().length > PadSpeakerNet.UP_PART) return;
        Upload u = UPLOADS.get(m.hash());
        if (u == null || !u.from.equals(p.getUUID()) || u.total != m.total() || u.size != m.size()) {
            if (u != null && System.currentTimeMillis() - u.lastMs < 30_000 && !u.from.equals(p.getUUID())) return;
            u = new Upload(p.getUUID(), m.total(), m.size());
            UPLOADS.put(m.hash(), u);
        }
        u.lastMs = System.currentTimeMillis();
        if (u.parts[m.index()] == null) {
            u.parts[m.index()] = m.data();
            u.got++;
        }
        if (u.got < u.total) return;
        UPLOADS.remove(m.hash());
        byte[] all = new byte[u.size];
        int off = 0;
        for (byte[] part : u.parts) {
            int n = Math.min(part.length, all.length - off);
            System.arraycopy(part, 0, all, off, n);
            off += n;
        }
        try {
            String got = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(all));
            if (off != u.size || !got.equals(m.hash())) {
                TFClient.LOGGER.warn("TF Pad: la canción del altavoz de {} llegó mal (no coincide)", p.getGameProfile().getName());
                return;
            }
            java.nio.file.Path f = speakerFile(m.hash());
            java.nio.file.Files.createDirectories(f.getParent());
            java.nio.file.Files.write(f, all);
            trim();
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Pad: no se pudo guardar la canción de un altavoz", e);
            return;
        }
        PadMusicShare.uploaded(p, m.hash());
        Set<UUID> waiting = WAITING.remove(m.hash());
        MinecraftServer server = p.getServer();
        if (waiting != null && server != null) {
            for (UUID w : waiting) {
                ServerPlayer l = server.getPlayerList().getPlayer(w);
                if (l != null) requested(l, m.hash());
            }
        }
    }

    /** Uno de cerca pide la canción: se le manda a trozos (unos pocos por tick) o, si aún no está, cuando llegue. */
    public static void requested(ServerPlayer p, String hash) {
        if (!hash.matches("[0-9a-f]{40}")) return;
        java.nio.file.Path f = stored(hash);
        if (f == null) return;
        if (!java.nio.file.Files.exists(f)) {
            Set<UUID> w = WAITING.computeIfAbsent(hash, k -> new HashSet<>());
            if (w.size() < 64) w.add(p.getUUID());
            return;
        }
        java.util.ArrayDeque<PadSpeakerNet.DownPart> q = SENDING.computeIfAbsent(p.getUUID(), k -> new java.util.ArrayDeque<>());
        for (PadSpeakerNet.DownPart d : q) if (d.hash().equals(hash)) return; // ya se le está mandando
        try {
            byte[] all = java.nio.file.Files.readAllBytes(f);
            f.toFile().setLastModified(System.currentTimeMillis());
            int total = Math.max(1, (all.length + PadSpeakerNet.DOWN_PART - 1) / PadSpeakerNet.DOWN_PART);
            for (int i = 0; i < total; i++) {
                int from = i * PadSpeakerNet.DOWN_PART, to = Math.min(all.length, from + PadSpeakerNet.DOWN_PART);
                q.add(new PadSpeakerNet.DownPart(hash, i, total, all.length, java.util.Arrays.copyOfRange(all, from, to)));
            }
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Pad: no se pudo leer la canción de un altavoz", e);
        }
    }

    /** Lo guardado no pasa de MAX_STORED: se borran las canciones que hace más que nadie pide. */
    private static void trim() {
        java.nio.file.Path d = dir();
        if (d == null) return;
        try (java.util.stream.Stream<java.nio.file.Path> s = java.nio.file.Files.list(d)) {
            java.util.List<java.io.File> files = new java.util.ArrayList<>(s.map(java.nio.file.Path::toFile)
                    .filter(f -> f.getName().endsWith(".audio")).toList());
            files.sort(java.util.Comparator.comparingLong(java.io.File::lastModified));
            long total = 0;
            for (java.io.File f : files) total += f.length();
            for (java.io.File f : files) {
                if (total <= MAX_STORED) break;
                total -= f.length();
                f.delete();
            }
        } catch (Exception ignored) {
            // se intenta la próxima vez
        }
    }

    @SubscribeEvent
    public static void onSendTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SENDING.isEmpty()) return;
        MinecraftServer server = event.getServer();
        // 2 trozos de 256 KB por tick a cada uno (~10 MB/s): una canción, en un segundo o dos
        SENDING.entrySet().removeIf(e -> {
            ServerPlayer l = server.getPlayerList().getPlayer(e.getKey());
            if (l == null) return true;
            for (int i = 0; i < 2 && !e.getValue().isEmpty(); i++) PadSpeakerNet.toPlayer(l, e.getValue().poll());
            return e.getValue().isEmpty();
        });
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
        SENDING.remove(u);
        UPLOADS.values().removeIf(up -> up.from.equals(u));
        for (Set<UUID> w : WAITING.values()) w.remove(u);
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
