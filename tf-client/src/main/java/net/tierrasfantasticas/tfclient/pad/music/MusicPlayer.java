package net.tierrasfantasticas.tfclient.pad.music;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * El reproductor de la app Música. Sigue sonando con el pad cerrado (es un sonido más del juego) hasta que se pausa o
 * se para en la app; solo lo oye este jugador. Pausar para el sonido al momento y reanudar lo vuelve a empezar desde
 * donde iba (los decodificadores saben saltar). Mientras suena, la música de fondo de Minecraft se calla. Si el motor
 * de sonido se reinicia (cambio de dimensión, de dispositivo…), la canción sigue sola donde estaba.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class MusicPlayer {
    private static MusicLibrary.Track current;
    private static boolean playing;
    private static long positionMs;
    private static long lastNanos;
    private static long restartedAt;
    private static MusicSound sound;
    private static String error;
    private static long errorAt;
    private static final Deque<String> HISTORY = new ArrayDeque<>();
    private static volatile float[] levels = new float[0];

    private MusicPlayer() {}

    // ---------------------------------------------------------------------------------------------------------------
    // Estado (para la app)
    // ---------------------------------------------------------------------------------------------------------------

    public static MusicLibrary.Track current() {
        if (current != null && MusicLibrary.get(current.id()) == null) current = null;
        return current;
    }

    public static boolean playing() {
        return playing;
    }

    public static long position() {
        return positionMs;
    }

    public static float volume() {
        MusicLibrary.load();
        return MusicLibrary.volume;
    }

    public static boolean shuffle() {
        MusicLibrary.load();
        return MusicLibrary.shuffle;
    }

    public static MusicLibrary.Repeat repeat() {
        MusicLibrary.load();
        return MusicLibrary.repeat;
    }

    /** El último error de reproducción (se borra a los 6 s). */
    public static String error() {
        if (error != null && System.currentTimeMillis() - errorAt > 6000) error = null;
        return error;
    }

    /** Volumen medido de la canción en ese momento (0..1), para el ecualizador. */
    public static float levelAt(long ms) {
        float[] l = levels;
        int i = (int) (ms / 50);
        return i >= 0 && i < l.length ? l[i] : 0F;
    }

    static void level(int index, float value) {
        float[] l = levels;
        if (index >= 0 && index < l.length) l[index] = value;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Controles
    // ---------------------------------------------------------------------------------------------------------------

    public static void play(MusicLibrary.Track track) {
        if (track == null) return;
        if (current != null && !current.id().equals(track.id())) {
            HISTORY.push(current.id());
            while (HISTORY.size() > 50) HISTORY.removeLast();
        }
        current = track;
        positionMs = 0;
        long dur = track.durationMs() > 0 ? track.durationMs() : 20 * 60_000L;
        levels = new float[(int) (dur / 50) + 2];
        start(0);
        Minecraft.getInstance().gui.setNowPlaying(Component.literal(track.shownTitle()
                + (track.artist().isBlank() ? "" : " · " + track.artist())));
    }

    /** Play / pausa. Sin canción elegida, empieza la primera. */
    public static void toggle() {
        if (playing) {
            pause();
        } else if (current() != null) {
            start(positionMs);
        } else {
            List<MusicLibrary.Track> all = MusicLibrary.tracks();
            if (!all.isEmpty()) play(shuffle() ? all.get(ThreadLocalRandom.current().nextInt(all.size())) : all.get(0));
        }
    }

    public static void pause() {
        stopSound();
        playing = false;
    }

    /** Apaga la música (y vuelve al principio de la canción). */
    public static void stop() {
        stopSound();
        playing = false;
        positionMs = 0;
    }

    public static void next() {
        MusicLibrary.Track n = pick(1);
        if (n != null) play(n);
        else stop();
    }

    public static void previous() {
        if (positionMs > 3000 || current == null) {
            seek(0);
            return;
        }
        while (!HISTORY.isEmpty() && shuffle()) {
            MusicLibrary.Track t = MusicLibrary.get(HISTORY.pop());
            if (t != null) {
                current = null; // que no vuelva al historial
                play(t);
                return;
            }
        }
        MusicLibrary.Track p = pick(-1);
        if (p != null) play(p);
        else seek(0);
    }

    public static void seek(long ms) {
        if (current == null) return;
        long dur = current.durationMs();
        positionMs = Math.max(0, dur > 0 ? Math.min(dur - 500, ms) : ms);
        if (playing) start(positionMs);
    }

    public static void setVolume(float v) {
        MusicLibrary.load();
        MusicLibrary.volume = Math.max(0, Math.min(1, v));
        if (sound != null) sound.setVolume(MusicLibrary.volume);
    }

    /** Guarda el volumen (al soltar la barra). */
    public static void saveSettings() {
        MusicLibrary.save();
    }

    public static void toggleShuffle() {
        MusicLibrary.load();
        MusicLibrary.shuffle = !MusicLibrary.shuffle;
        MusicLibrary.save();
    }

    public static void cycleRepeat() {
        MusicLibrary.load();
        MusicLibrary.repeat = MusicLibrary.repeat.next();
        MusicLibrary.save();
    }

    /** Al borrar una canción: si es la que suena, se para. */
    public static void removed(String id) {
        if (current != null && current.id().equals(id)) {
            stop();
            current = null;
        }
        HISTORY.remove(id);
    }

    // ---------------------------------------------------------------------------------------------------------------

    /** La siguiente (dir 1) o anterior (-1) según aleatorio y repetir; null si no hay. */
    private static MusicLibrary.Track pick(int dir) {
        List<MusicLibrary.Track> all = MusicLibrary.tracks();
        if (all.isEmpty()) return null;
        if (current == null) return all.get(0);
        if (shuffle() && all.size() > 1 && dir > 0) {
            MusicLibrary.Track t;
            do t = all.get(ThreadLocalRandom.current().nextInt(all.size())); while (t.id().equals(current.id()));
            return t;
        }
        int i = 0;
        for (int k = 0; k < all.size(); k++) if (all.get(k).id().equals(current.id())) i = k;
        int j = i + dir;
        if (j < 0 || j >= all.size()) {
            if (repeat() != MusicLibrary.Repeat.ALL && dir > 0) return null;
            j = Math.floorMod(j, all.size());
        }
        return all.get(j);
    }

    private static void start(long fromMs) {
        stopSound();
        if (current == null) return;
        Minecraft mc = Minecraft.getInstance();
        sound = new MusicSound(current, fromMs, volume());
        mc.getSoundManager().play(sound);
        playing = true;
        positionMs = fromMs;
        lastNanos = System.nanoTime();
        restartedAt = System.currentTimeMillis();
        mc.getMusicManager().stopPlaying();
    }

    private static void stopSound() {
        if (sound != null) {
            sound.stopNow();
            Minecraft.getInstance().getSoundManager().stop(sound);
            sound = null;
        }
    }

    private static void ended() {
        if (repeat() == MusicLibrary.Repeat.ONE) {
            positionMs = 0;
            start(0);
            return;
        }
        MusicLibrary.Track n = pick(1);
        if (n != null) {
            play(n);
        } else {
            stop();
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Eventos
    // ---------------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !playing) return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        if (!mc.isPaused()) positionMs += (now - lastNanos) / 1_000_000L;
        lastNanos = now;
        if (current != null && current.durationMs() > 0) positionMs = Math.min(positionMs, current.durationMs());
        mc.getMusicManager().stopPlaying();
        MusicSound s = sound;
        if (s == null) return;
        if (s.failure != null) {
            error = "No se pudo reproducir: " + (s.failure.getMessage() == null ? "archivo dañado" : s.failure.getMessage());
            errorAt = System.currentTimeMillis();
            TFClient.LOGGER.warn("Música: {}", error);
            stop();
            return;
        }
        if (mc.getSoundManager().isActive(s)) return;
        if (s.stream != null && s.stream.ended) {
            ended();
        } else if (System.currentTimeMillis() - restartedAt > 2000 && mc.options.getSoundSourceVolume(SoundSource.MASTER) > 0) {
            start(positionMs); // el motor de sonido se reinició: sigue donde iba
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (playing) pause();
    }
}
