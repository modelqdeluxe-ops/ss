package net.tierrasfantasticas.tfclient.pad.music;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Recorre las cabeceras de los frames de un MP3 sin decodificar nada (es casi instantáneo): para saber cuánto dura y
 * en qué byte empieza el frame de un momento dado (para saltar a un punto de la canción). Solo MPEG-1 (capas I, II y
 * III), que es lo que entiende el decodificador.
 */
public final class Mp3Frames {
    private static final int[][] BITRATE = {
            {0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448}, // capa I
            {0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384}, // capa II
            {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320}}; // capa III
    private static final int[] RATE = {44100, 48000, 32000};

    /**
     * Lo que se averigua al recorrer el archivo. offset: byte por donde empezar a decodificar; warmup: frames que hay
     * que decodificar y tirar antes del momento pedido (la capa III usa datos de los frames anteriores); startMs: el
     * momento del primer frame que se oye.
     */
    public record Scan(long durationMs, long offset, int warmup, long startMs, int sampleRate, boolean mpeg1) {}

    private Mp3Frames() {}

    /** Tamaño de la etiqueta ID3v2 del principio (0 si no tiene). */
    public static long id3Size(byte[] head) {
        if (head.length < 10 || head[0] != 'I' || head[1] != 'D' || head[2] != '3') return 0;
        long size = ((head[6] & 0x7F) << 21) | ((head[7] & 0x7F) << 14) | ((head[8] & 0x7F) << 7) | (head[9] & 0x7F);
        boolean footer = (head[5] & 0x10) != 0;
        return 10 + size + (footer ? 10 : 0);
    }

    /** Recorre todo el archivo; si targetMs &gt;= 0, apunta el primer frame que empieza en ese momento o después. */
    public static Scan scan(Path file, long targetMs) throws IOException {
        try (InputStream raw = Files.newInputStream(file); Reader in = new Reader(raw)) {
            byte[] head = new byte[10];
            in.mark(10);
            int got = in.readNBytes(head, 0, 10);
            in.reset();
            long pos = 0;
            long skip = got == 10 ? id3Size(head) : 0;
            pos += in.skipNBytes0(skip);
            long samples = 0;
            int rate = 0;
            boolean mpeg1 = true;
            long offset = -1, startMs = 0, prev1 = -1, prev2 = -1;
            int warmup = 0;
            byte[] h = new byte[4];
            while (true) {
                in.mark(4);
                int n = in.readNBytes(h, 0, 4);
                if (n < 4) break;
                int b0 = h[0] & 0xFF, b1 = h[1] & 0xFF, b2 = h[2] & 0xFF;
                int version = (b1 >> 3) & 3, layer = (b1 >> 1) & 3, br = (b2 >> 4) & 15, sr = (b2 >> 2) & 3, pad = (b2 >> 1) & 1;
                boolean sync = b0 == 0xFF && (b1 & 0xE0) == 0xE0;
                if (!sync || layer == 0 || br == 0 || br == 15 || sr == 3) {
                    in.reset();
                    in.skipNBytes(1);
                    pos++;
                    continue;
                }
                if (version != 3) mpeg1 = false;
                int r = RATE[sr] >> (version == 3 ? 0 : version == 2 ? 1 : 2);
                int bitrate = BITRATE[3 - layer][br] * 1000;
                int len, perFrame;
                if (layer == 3) { // capa I
                    len = (12 * bitrate / r + pad) * 4;
                    perFrame = 384;
                } else {
                    boolean lsf = version != 3 && layer == 1;
                    len = (lsf ? 72 : 144) * bitrate / r + pad;
                    perFrame = lsf ? 576 : 1152;
                }
                if (len < 4) {
                    in.reset();
                    in.skipNBytes(1);
                    pos++;
                    continue;
                }
                if (rate == 0) rate = r;
                long ms = samples * 1000 / Math.max(1, rate);
                if (targetMs >= 0 && offset < 0 && ms >= targetMs) {
                    offset = prev2 >= 0 ? prev2 : prev1 >= 0 ? prev1 : pos;
                    warmup = prev2 >= 0 ? 2 : prev1 >= 0 ? 1 : 0;
                    startMs = ms;
                }
                prev2 = prev1;
                prev1 = pos;
                samples += perFrame;
                in.reset();
                long s = in.skipNBytes0(len);
                pos += s;
                if (s < len) break;
            }
            long duration = rate == 0 ? 0 : samples * 1000 / rate;
            if (offset < 0) {
                offset = pos;
                startMs = duration;
            }
            return new Scan(duration, offset, warmup, startMs, rate, mpeg1);
        }
    }

    /** skipNBytes que no lanza al llegar al final: devuelve lo que saltó de verdad. */
    private static final class Reader extends java.io.BufferedInputStream {
        Reader(InputStream in) {
            super(in, 1 << 16);
        }

        long skipNBytes0(long n) throws IOException {
            long left = n;
            while (left > 0) {
                long s = skip(left);
                if (s <= 0) {
                    if (read() < 0) break;
                    s = 1;
                }
                left -= s;
            }
            return n - left;
        }
    }
}
