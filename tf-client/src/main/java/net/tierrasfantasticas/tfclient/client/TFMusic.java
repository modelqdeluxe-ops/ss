package net.tierrasfantasticas.tfclient.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.tierrasfantasticas.tfclient.TFClient;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Música de TF para la pantalla de carga y el menú. Suena desde que arranca el mod (antes de que el sistema de
 * sonido de Minecraft esté listo), en bucle, con fundidos de entrada y salida, y respeta los volúmenes
 * "General" y "Música" de las opciones. Se calla al entrar a un mundo o al servidor y vuelve en el menú.
 *
 * El OGG se decodifica con stb_vorbis (incluido en Minecraft) y se reproduce con javax.sound: sin dependencias.
 */
public final class TFMusic {
    private static final String TRACK = "assets/" + TFClient.MOD_ID + "/music/menu.ogg";
    private static final float BASE_VOLUME = 0.75f;
    private static final float FADE_IN_SECONDS = 2.5f;
    private static final float FADE_OUT_SECONDS = 1.5f;
    private static final int CHUNK_FRAMES = 2048;

    private static volatile boolean wanted;
    private static volatile boolean running;
    private static volatile boolean failed;
    private static Thread thread;

    private TFMusic() {}

    /** true mientras la música de TF está sonando (o debería): la música normal del juego espera. */
    public static boolean isActive() {
        return running && wanted && !failed;
    }

    public static synchronized void play() {
        wanted = true;
        if (thread == null && !failed) {
            thread = new Thread(TFMusic::run, "TF Client Music");
            thread.setDaemon(true);
            thread.start();
        }
    }

    public static void stop() {
        wanted = false;
    }

    private static float volume() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return BASE_VOLUME;
        return BASE_VOLUME * mc.options.getSoundSourceVolume(SoundSource.MASTER) * mc.options.getSoundSourceVolume(SoundSource.MUSIC);
    }

    private static void run() {
        ByteBuffer ogg = null;
        ShortBuffer pcm = null;
        long handle = 0L;
        SourceDataLine line = null;
        try {
            byte[] file;
            try (InputStream in = TFResources.open(TRACK)) {
                file = in.readAllBytes();
            }
            ogg = MemoryUtil.memAlloc(file.length);
            ogg.put(file).flip();

            int channels;
            int rate;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer error = stack.mallocInt(1);
                handle = STBVorbis.stb_vorbis_open_memory(ogg, error, null);
                if (handle == 0L) throw new IOException("OGG no válido (error " + error.get(0) + ")");
                STBVorbisInfo info = STBVorbisInfo.malloc(stack);
                STBVorbis.stb_vorbis_get_info(handle, info);
                channels = info.channels();
                rate = info.sample_rate();
            }

            AudioFormat format = new AudioFormat(rate, 16, channels, true, false);
            line = AudioSystem.getSourceDataLine(format);
            line.open(format, rate * channels * 2 / 4); // ~250 ms de búfer
            line.start();
            running = true;

            pcm = MemoryUtil.memAllocShort(CHUNK_FRAMES * channels);
            byte[] bytes = new byte[CHUNK_FRAMES * channels * 2];
            float chunkSeconds = CHUNK_FRAMES / (float) rate;
            float fade = 0f;

            while (true) {
                if (!wanted && fade <= 0f) {
                    // En partida: esperamos en silencio y al volver al menú empieza de nuevo desde el principio.
                    line.stop();
                    line.flush();
                    while (!wanted) Thread.sleep(150L);
                    STBVorbis.stb_vorbis_seek_start(handle);
                    line.start();
                }
                fade = wanted
                        ? Math.min(1f, fade + chunkSeconds / FADE_IN_SECONDS)
                        : Math.max(0f, fade - chunkSeconds / FADE_OUT_SECONDS);

                pcm.clear();
                int frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, pcm);
                if (frames <= 0) {
                    STBVorbis.stb_vorbis_seek_start(handle); // fin de la canción: otra vez
                    continue;
                }
                // Fundido con curva para que suene natural
                float gain = fade * fade * volume();
                int samples = frames * channels;
                for (int i = 0; i < samples; i++) {
                    int s = Math.round(pcm.get(i) * gain);
                    if (s > Short.MAX_VALUE) s = Short.MAX_VALUE;
                    else if (s < Short.MIN_VALUE) s = Short.MIN_VALUE;
                    bytes[2 * i] = (byte) s;
                    bytes[2 * i + 1] = (byte) (s >> 8);
                }
                line.write(bytes, 0, samples * 2);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            failed = true;
            TFClient.LOGGER.warn("TF Client: no se pudo reproducir la música del menú: {}", t.toString());
        } finally {
            running = false;
            if (line != null) line.close();
            if (handle != 0L) STBVorbis.stb_vorbis_close(handle);
            if (pcm != null) MemoryUtil.memFree(pcm);
            if (ogg != null) MemoryUtil.memFree(ogg);
        }
    }
}
