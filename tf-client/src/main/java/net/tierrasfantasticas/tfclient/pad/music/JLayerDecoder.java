package net.tierrasfantasticas.tfclient.pad.music;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

/**
 * Todos los MP3 (MPEG-1, 2 y 2.5; 8 a 320 kbps; CBR o VBR) con el decodificador de JLayer 1.0.1 (javazoom.jl.decoder,
 * LGPL 2.1, copiado sin cambios; licencia en resources/javazoom/jl/decoder). Medido contra ffmpeg con MP3 de 320, 128,
 * VBR, 48 kHz y MPEG-2: la misma señal (solo 0,02 dB más bajo). Para empezar a mitad de canción se saltan frames
 * leyendo solo sus cabeceras, y los dos últimos antes del punto se decodifican y se tiran: así la reserva de bits de la
 * capa III está llena y el primer trozo que suena no chasquea.
 */
final class JLayerDecoder implements AudioDecoder {
    private final InputStream in;
    private final Bitstream bits;
    private final Decoder decoder = new Decoder();
    private int rate = 44100, channels = 2;
    private byte[] pending;

    JLayerDecoder(Path file, long startMs) throws IOException {
        in = new BufferedInputStream(Files.newInputStream(file), 1 << 16);
        bits = new Bitstream(in);
        try {
            Header h = bits.readFrame();
            float t = 0;
            while (h != null && startMs > 0 && t + frameMs(h) < startMs) {
                float d = frameMs(h);
                if (t + 3 * d >= startMs) decode(h); // calienta la reserva de bits (se tira lo decodificado)
                else bits.closeFrame();
                t += d;
                h = bits.readFrame();
            }
            if (h == null) throw new IOException("No hay audio MP3 en el archivo");
            pending = decode(h);
        } catch (BitstreamException | RuntimeException e) {
            close();
            throw new IOException("MP3 dañado", e);
        }
    }

    /** Decodifica el frame de h (y lo cierra): PCM de 16 bits little endian, canales intercalados ("" si falla). */
    private byte[] decode(Header h) {
        try {
            SampleBuffer out = (SampleBuffer) decoder.decodeFrame(h, bits);
            rate = out.getSampleFrequency();
            channels = out.getChannelCount();
            short[] s = out.getBuffer();
            int n = Math.min(s.length, out.getBufferLength());
            byte[] b = new byte[n * 2];
            for (int i = 0; i < n; i++) {
                b[2 * i] = (byte) s[i];
                b[2 * i + 1] = (byte) (s[i] >> 8);
            }
            return b;
        } catch (DecoderException | RuntimeException e) {
            return new byte[0]; // un frame dañado (o sin su reserva de bits tras saltar): silencio
        } finally {
            bits.closeFrame();
        }
    }

    @Override
    public int sampleRate() {
        return rate;
    }

    @Override
    public int channels() {
        return channels;
    }

    @Override
    public byte[] next() throws IOException {
        if (pending != null && pending.length > 0) {
            byte[] b = pending;
            pending = null;
            return b;
        }
        pending = null;
        try {
            for (int tries = 0; tries < 64; tries++) {
                Header h = bits.readFrame();
                if (h == null) return null;
                byte[] b = decode(h);
                if (b.length > 0) return b;
            }
            return null;
        } catch (BitstreamException | RuntimeException e) {
            return null; // un final dañado: se acaba ahí
        }
    }

    @Override
    public void close() throws IOException {
        try {
            bits.close();
        } catch (BitstreamException e) {
            in.close();
        }
    }

    /**
     * Lo que dura un frame en ms (Header.ms_per_frame se equivoca en MPEG-2.5): muestras por frame (capa I 384, II 1152,
     * III 1152 en MPEG-1 y 576 en MPEG-2/2.5) entre la frecuencia.
     */
    static float frameMs(Header h) {
        int samples = h.layer() == 1 ? 384 : h.layer() == 2 || h.version() == Header.MPEG1 ? 1152 : 576;
        return samples * 1000F / Math.max(1, h.frequency());
    }

    /** La duración sumando lo que dura cada frame (solo se leen las cabeceras). */
    static long durationMs(Path file) {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file), 1 << 16)) {
            Bitstream b = new Bitstream(in);
            double t = 0;
            for (Header h = b.readFrame(); h != null; h = b.readFrame()) {
                t += frameMs(h);
                b.closeFrame();
            }
            return Math.round(t);
        } catch (Exception e) {
            return 0;
        }
    }

    /** ¿Es un MP3 MPEG-1 (su duración se mide con Mp3Frames)? Mira la cabecera del primer frame, tras la etiqueta ID3. */
    static boolean isMpeg1(Path file) {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file), 1 << 16)) {
            byte[] head = new byte[10];
            int n = in.readNBytes(head, 0, 10);
            long skip = n == 10 ? Mp3Frames.id3Size(head) : 0;
            if (skip > 10) in.skipNBytes(skip - 10);
            int prev = skip > 0 ? -1 : (n > 0 ? head[n - 1] & 0xFF : -1);
            if (skip == 0) {
                for (int i = 0; i + 1 < n; i++) {
                    if ((head[i] & 0xFF) == 0xFF && (head[i + 1] & 0xE0) == 0xE0) return ((head[i + 1] >> 3) & 3) == 3;
                }
            }
            for (int i = 0; i < 1 << 16; i++) {
                int c = in.read();
                if (c < 0) return true;
                if (prev == 0xFF && (c & 0xE0) == 0xE0) return ((c >> 3) & 3) == 3;
                prev = c;
            }
            return true;
        } catch (IOException e) {
            return true;
        }
    }
}
