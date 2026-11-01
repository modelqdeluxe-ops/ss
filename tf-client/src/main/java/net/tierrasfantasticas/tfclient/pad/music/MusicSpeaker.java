/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad.music;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.pad.PadSpeakerNet;

/**
 * El altavoz de la app Música en el cliente.
 * <ul>
 *   <li>Si está encendido y lo que suena vino de un link, le cuenta al servidor qué suena y por dónde va (al empezar,
 *       pausar, saltar, cambiar de canción y cada 10 s, para que los demás vayan a la par).</li>
 *   <li>Lo que suena en los altavoces de los de cerca: baja la canción del mismo link (una vez; se guarda en
 *       musica/altavoz/, como mucho 30) y la reproduce situada en ese jugador, en mono y apagándose con la distancia
 *       (categoría «Discos» del juego, con el volumen de la app). En Ajustes de la app se puede dejar de oírlos.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class MusicSpeaker {
    /** Distancia a la que deja de oírse (como el servidor, PadSpeakers.RANGE). */
    private static final int RANGE = 32;
    private static final int MAX_CACHE = 30, MAX_DOWNLOADS = 2;
    private static final ResourceLocation ID = new ResourceLocation(TFClient.MOD_ID, "pad_altavoz");

    private static final class Heard {
        final String url;
        final long durationMs;
        long originMs;
        SpeakerSound sound;
        /** (1.3.160) La música de una protección: suena igual en toda la zona (no situada) y en bucle. */
        boolean zone;
        long playedAt;

        Heard(String url, long originMs, long durationMs) {
            this.url = url;
            this.originMs = originMs;
            this.durationMs = durationMs;
        }

        long position() {
            long p = System.currentTimeMillis() - originMs;
            return zone && durationMs > 0 ? Math.floorMod(p, durationMs) : p;
        }

        boolean over() {
            return !zone && durationMs > 0 && position() > durationMs;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // (1.3.160) La música de las protecciones (PadZoneMusic): la de la zona en la que estás
    // ---------------------------------------------------------------------------------------------------------------

    /** La zona que suena ahora (su id), o null. */
    private static UUID zoneKey;
    /** Su volumen (0 a 100, el que puso el dueño de la zona). */
    private static int zoneVolume = 80;

    /** Lo que cuenta el servidor: entraste en una zona con música (o sigue sonando), o saliste. */
    public static void zone(PadSpeakerNet.Zone m) {
        if (!m.playing()) {
            Heard h = HEARD.get(m.claim());
            if (h != null && h.zone) {
                HEARD.remove(m.claim());
                fadeOut(h);
            }
            if (m.claim().equals(zoneKey)) zoneKey = null;
            return;
        }
        String url = m.url();
        if (!(url.startsWith("https://") || url.startsWith("http://") || url.startsWith(PadSpeakerNet.LOCAL))) return;
        if (zoneKey != null && !zoneKey.equals(m.claim())) {
            Heard old = HEARD.remove(zoneKey);
            if (old != null) fadeOut(old);
        }
        zoneKey = m.claim();
        zoneVolume = m.volume();
        long origin = System.currentTimeMillis() - m.positionMs();
        Heard old = HEARD.get(m.claim());
        if (old != null && old.zone && old.url.equals(url)) {
            // el recordatorio: si va desfasada más de 1,5 s, se vuelve a poner donde va; si no, no se corta
            long d = m.durationMs() > 0 ? Math.floorMod(origin - old.originMs, m.durationMs()) : Math.abs(origin - old.originMs);
            if (m.durationMs() > 0) d = Math.min(d, m.durationMs() - d);
            if (d > 1500) {
                old.originMs = origin;
                if (old.sound != null && MusicLibrary.hearOthers()) {
                    Path file = cacheFile(old.url);
                    if (file != null && Files.exists(file)) play(m.claim(), old, file);
                }
            }
            if (old.sound == null && MusicLibrary.hearOthers()) ensure(m.claim(), old);
            return;
        }
        if (old != null) stopSound(old);
        Heard h = new Heard(url, origin, m.durationMs());
        h.zone = true;
        HEARD.put(m.claim(), h);
        if (MusicLibrary.hearOthers()) ensure(m.claim(), h);
    }

    /**
     * (1.3.160) Desde la app Música, eligiendo para una zona: manda la canción (su link, o tfaudio: si es de tu carpeta:
     * el servidor la pide y se sube) para la música de esa protección. Devuelve un aviso si no se pudo (si no, avisa el
     * servidor).
     */
    public static String setZone(String claim, MusicLibrary.Track t) {
        if (Minecraft.getInstance().getConnection() == null) return "Entra en el servidor para ponerle música a la zona.";
        String url = shareUrl(t);
        if (url == null) return "Esa canción pesa demasiado para ponerla en la zona.";
        PadSpeakerNet.toServerAny(new PadSpeakerNet.ZoneSet(claim, url, t.shownTitle(), t.artist(), t.durationMs()));
        return null;
    }

    /** Se apaga poco a poco (1 s) en vez de cortarse. */
    private static void fadeOut(Heard h) {
        if (h.sound != null) {
            h.sound.fadeOut();
            h.sound = null;
        }
    }

    /** Para la prueba en el juego: la zona que suena (o null) y si su sonido está sonando. */
    public static String zoneForTest() {
        Heard h = zoneKey == null ? null : HEARD.get(zoneKey);
        if (h == null) return "ninguna";
        boolean active = h.sound != null && Minecraft.getInstance().getSoundManager().isActive(h.sound);
        return zoneKey + (active ? " sonando" : DOWNLOADING.contains(h.url) ? " bajando" : " sin sonar");
    }

    private static final Map<UUID, Heard> HEARD = new HashMap<>();
    private static final Set<String> DOWNLOADING = new HashSet<>();
    private static final Map<String, Long> FAILED = new HashMap<>();
    /** (1.3.114) Desde cuándo se espera una canción del servidor que aún no empieza a llegar. */
    private static final Map<String, Long> WAIT_SINCE = new HashMap<>();
    private static boolean published;
    private static long lastPublish;
    private static int ticks;

    private MusicSpeaker() {}

    // ---------------------------------------------------------------------------------------------------------------
    // Mi altavoz
    // ---------------------------------------------------------------------------------------------------------------

    /** Cuenta al servidor lo que suena ahora (o que paró). Lo llama MusicPlayer en cada cambio. */
    static void publish() {
        if (Minecraft.getInstance().getConnection() == null) return;
        MusicLibrary.Track t = MusicPlayer.current();
        String url = t == null ? null : shareUrl(t);
        boolean on = MusicLibrary.speaker() && MusicPlayer.playing() && url != null;
        if (on) {
            PadSpeakerNet.toServer(new PadSpeakerNet.Up(true, url, t.shownTitle(), t.artist(), MusicPlayer.position(), t.durationMs()));
            published = true;
        } else if (published) {
            PadSpeakerNet.toServer(new PadSpeakerNet.Up(false, "", "", "", 0, 0));
            published = false;
        }
        lastPublish = System.currentTimeMillis();
    }

    /** Enciende o apaga el altavoz. Devuelve el aviso para enseñar. */
    public static String toggle() {
        boolean on = !MusicLibrary.speaker();
        MusicLibrary.setSpeaker(on);
        publish();
        if (!on) return "Altavoz apagado";
        MusicLibrary.Track t = MusicPlayer.current();
        if (t != null && shareUrl(t) == null) return "Altavoz encendido · esta canción pesa demasiado para compartirla";
        return "Altavoz encendido · te oyen a " + RANGE + " bloques";
    }

    // ---------------------------------------------------------------------------------------------------------------
    // (1.3.114) Las canciones sin link (las de tu carpeta): las reparte el servidor (PadSpeakerNet.LOCAL)
    // ---------------------------------------------------------------------------------------------------------------

    /** El hash de cada canción de la carpeta (por su archivo, tamaño y fecha), y de cada hash, su archivo. */
    private static final Map<String, String> HASH_OF = new HashMap<>();
    private static final Map<String, Path> FILE_OF = new HashMap<>();
    /** Lo que falta por subir (unos pocos trozos por tick). */
    private static final java.util.ArrayDeque<PadSpeakerNet.UpPart> UPLOAD = new java.util.ArrayDeque<>();
    private static String uploading = "";

    /** Lo que se cuenta al servidor: el link, o tfaudio:&lt;sha1&gt; si es de la carpeta; null si no se puede compartir. */
    static String shareUrl(MusicLibrary.Track t) {
        // (1.3.134) una que te pasaron con un disco desde la carpeta de otro: su tfaudio: (por si el servidor la pide)
        if (t.url().startsWith(PadSpeakerNet.LOCAL)) FILE_OF.put(t.url().substring(PadSpeakerNet.LOCAL.length()), MusicLibrary.file(t));
        if (!t.url().isBlank()) return t.url();
        Path f = MusicLibrary.file(t);
        try {
            long size = Files.size(f);
            if (size <= 0 || size > PadSpeakerNet.MAX_BYTES) return null;
            String key = f + ":" + size + ":" + f.toFile().lastModified();
            String hash = HASH_OF.get(key);
            if (hash == null) {
                hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(f)));
                HASH_OF.put(key, hash);
            }
            FILE_OF.put(hash, f);
            return PadSpeakerNet.LOCAL + hash;
        } catch (Exception e) {
            return null;
        }
    }

    /** El servidor no tiene la canción que suena en mi altavoz: se sube a trozos. */
    public static void upload(String hash) {
        Path f = FILE_OF.get(hash);
        if (f == null || hash.equals(uploading) && !UPLOAD.isEmpty()) return;
        try {
            byte[] all = Files.readAllBytes(f);
            if (all.length > PadSpeakerNet.MAX_BYTES) return;
            UPLOAD.clear();
            uploading = hash;
            int total = Math.max(1, (all.length + PadSpeakerNet.UP_PART - 1) / PadSpeakerNet.UP_PART);
            for (int i = 0; i < total; i++) {
                int from = i * PadSpeakerNet.UP_PART, to = Math.min(all.length, from + PadSpeakerNet.UP_PART);
                UPLOAD.add(new PadSpeakerNet.UpPart(hash, i, total, all.length, java.util.Arrays.copyOfRange(all, from, to)));
            }
        } catch (Exception e) {
            TFClient.LOGGER.info("Música: no se pudo subir la canción del altavoz: {}", e.toString());
        }
    }

    private static final class Incoming {
        final byte[] data;
        final boolean[] have;
        int got;
        long lastMs = System.currentTimeMillis();

        Incoming(int size, int total) {
            data = new byte[size];
            have = new boolean[total];
        }
    }

    private static final Map<String, Incoming> INCOMING = new HashMap<>();

    /** Un trozo de una canción de altavoz que reparte el servidor. */
    public static void part(PadSpeakerNet.DownPart m) {
        String url = PadSpeakerNet.LOCAL + m.hash();
        if (!DOWNLOADING.contains(url) || m.size() <= 0 || m.size() > PadSpeakerNet.MAX_BYTES || m.total() <= 0
                || m.index() < 0 || m.index() >= m.total()) return;
        Incoming in = INCOMING.get(m.hash());
        if (in == null || in.data.length != m.size() || in.have.length != m.total()) {
            in = new Incoming(m.size(), m.total());
            INCOMING.put(m.hash(), in);
        }
        in.lastMs = System.currentTimeMillis();
        int off = m.index() * PadSpeakerNet.DOWN_PART;
        if (in.have[m.index()] || off + m.data().length > in.data.length) return;
        System.arraycopy(m.data(), 0, in.data, off, m.data().length);
        in.have[m.index()] = true;
        if (++in.got < in.have.length) return;
        INCOMING.remove(m.hash());
        Path file = cacheFile(url);
        boolean ok = false;
        try {
            if (file != null && HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(in.data)).equals(m.hash())) {
                Files.createDirectories(file.getParent());
                Files.write(file, in.data);
                ok = AudioDecoder.AudioFormatSniffer.sniff(file) != AudioDecoder.Kind.UNKNOWN;
                trimCache();
            }
        } catch (Exception e) {
            TFClient.LOGGER.info("Música: no se pudo guardar la canción de un altavoz: {}", e.toString());
        }
        finish(url, file, ok);
    }

    /**
     * (1.3.134) La canción de un disco compartido que guarda el servidor (tfaudio:): se pide como la de un altavoz y,
     * al llegar, MusicShare la mete en la música. false si ya se estaba bajando.
     */
    static boolean fetchShared(String url) {
        if (!url.startsWith(PadSpeakerNet.LOCAL) || !DOWNLOADING.add(url)) return false;
        FAILED.remove(url);
        PadSpeakerNet.toServerAny(new PadSpeakerNet.Get(url.substring(PadSpeakerNet.LOCAL.length())));
        return true;
    }

    /** Acabó de bajarse (o falló) la canción de un altavoz: suena en los que la tienen puesta, a la par. */
    private static void finish(String url, Path file, boolean ok) {
        DOWNLOADING.remove(url);
        MusicShare.arrived(url, file, ok);
        if (!ok) {
            if (FAILED.size() > 50) FAILED.clear();
            FAILED.put(url, System.currentTimeMillis());
            return;
        }
        for (Map.Entry<UUID, Heard> e : HEARD.entrySet()) {
            if (e.getValue().url.equals(url) && MusicLibrary.hearOthers()) play(e.getKey(), e.getValue(), file);
        }
    }


    // ---------------------------------------------------------------------------------------------------------------
    // Los altavoces de los demás
    // ---------------------------------------------------------------------------------------------------------------

    /** Lo que cuenta el servidor de un altavoz cercano. */
    public static void heard(PadSpeakerNet.Down m) {
        long origin = System.currentTimeMillis() - m.positionMs();
        Heard old = HEARD.get(m.speaker());
        // el recordatorio de lo mismo que ya suena (a menos de 1,5 s de donde va): no se corta, solo se apunta
        if (m.playing() && old != null && old.url.equals(m.url()) && Math.abs(origin - old.originMs) < 1500) {
            old.originMs = origin;
            if (old.sound == null && MusicLibrary.hearOthers()) ensure(m.speaker(), old);
            return;
        }
        HEARD.remove(m.speaker());
        if (old != null) stopSound(old);
        if (!m.playing()) return;
        String url = m.url();
        if (!(url.startsWith("https://") || url.startsWith("http://") || url.startsWith(PadSpeakerNet.LOCAL))) return;
        Heard h = new Heard(url, origin, m.durationMs());
        HEARD.put(m.speaker(), h);
        if (MusicLibrary.hearOthers()) ensure(m.speaker(), h);
    }

    /** Oír o no los altavoces de otros (Ajustes de la app). */
    public static void setHearOthers(boolean on) {
        MusicLibrary.setHearOthers(on);
        for (Map.Entry<UUID, Heard> e : HEARD.entrySet()) {
            if (on) ensure(e.getKey(), e.getValue());
            else stopSound(e.getValue());
        }
    }

    private static void ensure(UUID speaker, Heard h) {
        Path file = cacheFile(h.url);
        if (file != null && Files.exists(file)) {
            play(speaker, h, file);
            return;
        }
        // como mucho 2 descargas a la vez (si hay más, se intenta con el siguiente recordatorio)
        if (file == null || DOWNLOADING.size() >= MAX_DOWNLOADS) return;
        Long failed = FAILED.get(h.url);
        if (failed != null && System.currentTimeMillis() - failed < 120_000) return; // un link que falló: no se reintenta en 2 min
        if (!DOWNLOADING.add(h.url)) return;
        // (1.3.114) una canción de la carpeta del que la pone: la reparte el servidor (llega a trozos, part())
        if (h.url.startsWith(PadSpeakerNet.LOCAL)) {
            PadSpeakerNet.toServerAny(new PadSpeakerNet.Get(h.url.substring(PadSpeakerNet.LOCAL.length())));
            return;
        }
        Thread t = new Thread(() -> {
            boolean ok = false;
            Path part = file.resolveSibling(file.getFileName() + ".part");
            try {
                Files.createDirectories(file.getParent());
                MusicDownloader.fetch(new MusicDownloader.Job(h.url, false, ""), h.url, part, MusicDownloader.MAX_AUDIO, true, true);
                if (AudioDecoder.AudioFormatSniffer.sniff(part) != AudioDecoder.Kind.UNKNOWN) {
                    Files.move(part, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    ok = true;
                    trimCache();
                }
            } catch (Exception e) {
                TFClient.LOGGER.info("Música: no se pudo bajar la canción de un altavoz: {}", e.toString());
            } finally {
                try {
                    Files.deleteIfExists(part);
                } catch (Exception ignored) {
                    // nada
                }
            }
            boolean done = ok;
            Minecraft.getInstance().execute(() -> {
                DOWNLOADING.remove(h.url);
                if (!done) {
                    if (FAILED.size() > 50) FAILED.clear();
                    FAILED.put(h.url, System.currentTimeMillis());
                    return;
                }
                // sigue sonando la misma en ese altavoz: a la par por donde vaya
                for (Map.Entry<UUID, Heard> e : HEARD.entrySet()) {
                    if (e.getValue().url.equals(h.url) && MusicLibrary.hearOthers()) play(e.getKey(), e.getValue(), file);
                }
            });
        }, "TF Música (altavoz)");
        t.setDaemon(true);
        t.start();
    }

    private static void play(UUID speaker, Heard h, Path file) {
        stopSound(h);
        if (h.over()) return;
        h.sound = new SpeakerSound(speaker, file, Math.max(0, h.position()), h.zone);
        h.playedAt = System.currentTimeMillis();
        Minecraft.getInstance().getSoundManager().play(h.sound);
    }

    private static void stopSound(Heard h) {
        if (h.sound != null) {
            h.sound.stopNow();
            Minecraft.getInstance().getSoundManager().stop(h.sound);
            h.sound = null;
        }
    }

    private static Path cacheFile(String url) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return MusicLibrary.dir().resolve("altavoz").resolve(HexFormat.of().formatHex(d, 0, 10) + ".audio");
        } catch (Exception e) {
            return null;
        }
    }

    /** Como mucho MAX_CACHE canciones de altavoces guardadas: se borran las más viejas. */
    private static void trimCache() {
        Path dir = MusicLibrary.dir().resolve("altavoz");
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> files = s.filter(p -> p.toString().endsWith(".audio"))
                    .sorted(Comparator.comparingLong(p -> p.toFile().lastModified())).toList();
            for (int i = 0; i < files.size() - MAX_CACHE; i++) Files.deleteIfExists(files.get(i));
        } catch (Exception ignored) {
            // la caché es opcional
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Eventos
    // ---------------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        // (1.3.114) la subida de mi canción, 4 trozos de 30 KB por tick (~2,4 MB/s)
        for (int i = 0; i < 4 && !UPLOAD.isEmpty(); i++) PadSpeakerNet.toServerAny(UPLOAD.poll());
        if (++ticks % 20 != 0) return;
        // lo que se bajaba del servidor y dejó de llegar (1 min), o que no empieza a llegar (90 s): falló
        long now = System.currentTimeMillis();
        for (String hash : Set.copyOf(INCOMING.keySet())) {
            if (now - INCOMING.get(hash).lastMs > 60_000) {
                INCOMING.remove(hash);
                finish(PadSpeakerNet.LOCAL + hash, null, false);
            }
        }
        for (String url : Set.copyOf(DOWNLOADING)) {
            if (url.startsWith(PadSpeakerNet.LOCAL) && !INCOMING.containsKey(url.substring(PadSpeakerNet.LOCAL.length()))) {
                long since = WAIT_SINCE.computeIfAbsent(url, k -> now);
                if (now - since > 90_000) {
                    WAIT_SINCE.remove(url);
                    finish(url, null, false);
                }
            } else {
                WAIT_SINCE.remove(url);
            }
        }
        // mi altavoz: un recordatorio cada 10 s para que los demás vayan a la par
        if (published && System.currentTimeMillis() - lastPublish > 10_000) publish();
        // los de los demás: si el motor de sonido se reinició, vuelven donde iban; si la canción acabó, se olvida
        Minecraft mc = Minecraft.getInstance();
        HEARD.entrySet().removeIf(e -> {
            Heard h = e.getValue();
            // (1.3.160) la de la zona, en bucle: si acabó (o el motor de sonido se reinició), otra vez donde va
            if (h.zone) {
                if ((h.sound == null || !mc.getSoundManager().isActive(h.sound)) && MusicLibrary.hearOthers()
                        && System.currentTimeMillis() - h.playedAt > 2000) {
                    Path file = cacheFile(h.url);
                    if (file != null && Files.exists(file)) play(e.getKey(), h, file);
                }
                return false;
            }
            if (h.over()) {
                stopSound(h);
                return true;
            }
            if (h.sound != null && !mc.getSoundManager().isActive(h.sound) && h.sound.stream != null && !h.sound.stream.ended
                    && mc.level != null && mc.level.getPlayerByUUID(e.getKey()) != null) {
                Path file = cacheFile(h.url);
                if (file != null && Files.exists(file)) play(e.getKey(), h, file);
            }
            return false;
        });
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        HEARD.values().forEach(MusicSpeaker::stopSound);
        HEARD.clear();
        zoneKey = null;
        published = false;
        UPLOAD.clear();
        INCOMING.clear();
        WAIT_SINCE.clear();
        DOWNLOADING.removeIf(u -> u.startsWith(PadSpeakerNet.LOCAL));
    }

    /**
     * La canción de un altavoz: situada en el jugador que la pone, en mono, y apagándose hasta RANGE bloques. (1.3.160)
     * O la de una zona: igual en toda la zona (pegada a quien la oye), entrando y saliendo poco a poco.
     */
    static final class SpeakerSound extends AbstractSoundInstance implements TickableSoundInstance {
        private final UUID speaker;
        private final Path file;
        private final long startMs;
        private final boolean zone;
        private final long bornMs = System.currentTimeMillis();
        private long fadeFrom = -1;
        volatile MusicStream stream;
        private boolean stopped;

        SpeakerSound(UUID speaker, Path file, long startMs, boolean zone) {
            super(ID, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
            this.speaker = speaker;
            this.file = file;
            this.startMs = startMs;
            this.zone = zone;
            this.volume = zone ? 0F : MusicPlayer.volume();
            this.relative = zone;
            this.attenuation = zone ? Attenuation.NONE : Attenuation.LINEAR;
            this.looping = false;
            follow();
        }

        /** (1.3.160) Que se apague en 1 s y pare. */
        void fadeOut() {
            if (fadeFrom < 0) fadeFrom = System.currentTimeMillis();
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            this.sound = new Sound(ID.toString(), ConstantFloat.of(1F), ConstantFloat.of(1F), 1, Sound.Type.FILE, true, false, RANGE);
            WeighedSoundEvents events = new WeighedSoundEvents(ID, null);
            events.addSound(this.sound);
            return events;
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    MusicStream s = new MusicStream(new MonoDecoder(AudioDecoder.open(file, startMs)), startMs, false);
                    stream = s;
                    return (AudioStream) s;
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, Util.backgroundExecutor());
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public boolean isStopped() {
            return stopped;
        }

        void stopNow() {
            stopped = true;
        }

        /** El sonido va donde está el jugador (a la altura de la cabeza). */
        private void follow() {
            if (zone) return; // la de la zona va pegada a quien la oye (relative)
            Minecraft mc = Minecraft.getInstance();
            Player p = mc.level == null ? null : mc.level.getPlayerByUUID(speaker);
            if (p == null) return;
            this.x = p.getX();
            this.y = p.getEyeY();
            this.z = p.getZ();
        }

        @Override
        public void tick() {
            follow();
            if (!zone) {
                this.volume = MusicLibrary.hearOthers() ? MusicPlayer.volume() : 0F;
                return;
            }
            long now = System.currentTimeMillis();
            float in = Math.min(1F, (now - bornMs) / 1200F);
            float out = fadeFrom < 0 ? 1F : Math.max(0F, 1F - (now - fadeFrom) / 1000F);
            if (out <= 0F) stopped = true;
            // si suena tu música del pad, la de la zona se queda bajita
            float duck = MusicPlayer.playing() ? 0.3F : 1F;
            this.volume = MusicLibrary.hearOthers() ? MusicPlayer.volume() * zoneVolume / 100F * in * out * duck : 0F;
        }
    }
}
