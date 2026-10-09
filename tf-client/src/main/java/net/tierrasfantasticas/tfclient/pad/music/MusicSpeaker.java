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

        Heard(String url, long originMs, long durationMs) {
            this.url = url;
            this.originMs = originMs;
            this.durationMs = durationMs;
        }

        long position() {
            return System.currentTimeMillis() - originMs;
        }

        boolean over() {
            return durationMs > 0 && position() > durationMs;
        }
    }

    private static final Map<UUID, Heard> HEARD = new HashMap<>();
    private static final Set<String> DOWNLOADING = new HashSet<>();
    private static final Map<String, Long> FAILED = new HashMap<>();
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
        boolean on = MusicLibrary.speaker() && MusicPlayer.playing() && t != null && !t.url().isBlank();
        if (on) {
            PadSpeakerNet.toServer(new PadSpeakerNet.Up(true, t.url(), t.shownTitle(), t.artist(), MusicPlayer.position(), t.durationMs()));
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
        if (!on) return "Altavoz apagado: solo lo oyes tú.";
        MusicLibrary.Track t = MusicPlayer.current();
        if (t != null && t.url().isBlank()) return "Altavoz encendido, pero esta canción no vino de un link: los demás no la pueden bajar.";
        return "Altavoz encendido: los que estén a menos de " + RANGE + " bloques oyen tu música.";
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
        if (!(url.startsWith("https://") || url.startsWith("http://"))) return;
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
        Thread t = new Thread(() -> {
            boolean ok = false;
            Path part = file.resolveSibling(file.getFileName() + ".part");
            try {
                Files.createDirectories(file.getParent());
                MusicDownloader.fetch(new MusicDownloader.Job(h.url, false), h.url, part, MusicDownloader.MAX_AUDIO, true, true);
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
        h.sound = new SpeakerSound(speaker, file, Math.max(0, h.position()));
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
        if (event.phase != TickEvent.Phase.END || ++ticks % 20 != 0) return;
        // mi altavoz: un recordatorio cada 10 s para que los demás vayan a la par
        if (published && System.currentTimeMillis() - lastPublish > 10_000) publish();
        // los de los demás: si el motor de sonido se reinició, vuelven donde iban; si la canción acabó, se olvida
        Minecraft mc = Minecraft.getInstance();
        HEARD.entrySet().removeIf(e -> {
            Heard h = e.getValue();
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
        published = false;
    }

    /** La canción de un altavoz: situada en el jugador que la pone, en mono, y apagándose hasta RANGE bloques. */
    static final class SpeakerSound extends AbstractSoundInstance implements TickableSoundInstance {
        private final UUID speaker;
        private final Path file;
        private final long startMs;
        volatile MusicStream stream;
        private boolean stopped;

        SpeakerSound(UUID speaker, Path file, long startMs) {
            super(ID, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
            this.speaker = speaker;
            this.file = file;
            this.startMs = startMs;
            this.volume = MusicPlayer.volume();
            this.relative = false;
            this.attenuation = Attenuation.LINEAR;
            this.looping = false;
            follow();
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
            this.volume = MusicLibrary.hearOthers() ? MusicPlayer.volume() : 0F;
        }
    }
}
