package net.tierrasfantasticas.tfclient.pad.music;

import java.io.IOException;
import java.nio.ByteBuffer;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;

/**
 * Le pasa al motor de sonido de Minecraft la canción decodificada, en trozos de 1 s (los pide él, 4 por delante).
 * De paso mide el volumen cada 50 ms ({@link MusicPlayer#levels}) para que el ecualizador de la app baile con la
 * música de verdad.
 */
final class MusicStream implements AudioStream {
    private final AudioDecoder decoder;
    private final AudioFormat format;
    private final long startMs;
    private final int frameBytes;
    /** Medir el volumen para el ecualizador de la app (solo la música propia, no la de los altavoces de otros). */
    private final boolean meter;
    private byte[] chunk;
    private int chunkPos;
    private long frames;
    private double windowSum;
    private int windowCount;
    volatile boolean ended;

    MusicStream(AudioDecoder decoder, long startMs) {
        this(decoder, startMs, true);
    }

    MusicStream(AudioDecoder decoder, long startMs, boolean meter) {
        this.meter = meter;
        this.decoder = decoder;
        this.startMs = startMs;
        this.format = new AudioFormat(decoder.sampleRate(), 16, decoder.channels(), true, false);
        this.frameBytes = 2 * decoder.channels();
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        size -= size % frameBytes;
        ByteBuffer out = BufferUtils.createByteBuffer(Math.max(frameBytes, size));
        while (out.hasRemaining()) {
            if (chunk == null || chunkPos >= chunk.length) {
                chunk = ended ? null : decoder.next();
                chunkPos = 0;
                if (chunk == null) {
                    ended = true;
                    break;
                }
            }
            int n = Math.min(out.remaining(), chunk.length - chunkPos);
            if (meter) measure(chunk, chunkPos, n);
            out.put(chunk, chunkPos, n);
            chunkPos += n;
        }
        if (out.position() == 0) return null;
        out.flip();
        return out;
    }

    /** Raíz cuadrática media de cada ventana de 50 ms (0..1), guardada por su momento en la canción. */
    private void measure(byte[] b, int off, int len) {
        int window = Math.max(1, decoder.sampleRate() / 20);
        int channels = decoder.channels();
        for (int i = off; i + frameBytes <= off + len; i += frameBytes) {
            int v = (short) ((b[i] & 0xFF) | (b[i + 1] << 8));
            if (channels == 2) v = (v + (short) ((b[i + 2] & 0xFF) | (b[i + 3] << 8))) / 2;
            windowSum += (double) v * v;
            windowCount++;
            frames++;
            if (windowCount >= window) {
                float rms = (float) Math.sqrt(windowSum / windowCount) / 32768F;
                long ms = startMs + frames * 1000 / decoder.sampleRate();
                MusicPlayer.level((int) (ms / 50), Math.min(1F, rms * 2.2F));
                windowSum = 0;
                windowCount = 0;
            }
        }
    }

    @Override
    public void close() throws IOException {
        decoder.close();
    }
}
