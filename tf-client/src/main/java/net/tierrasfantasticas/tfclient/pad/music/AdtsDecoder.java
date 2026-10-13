package net.tierrasfantasticas.tfclient.pad.music;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.sourceforge.jaad.aac.Decoder;
import net.sourceforge.jaad.aac.SampleBuffer;
import net.sourceforge.jaad.adts.ADTSDemultiplexer;

/**
 * AAC «suelto» (.aac, frames ADTS, sin contenedor MP4) con JAAD (1.3.64). Cada frame son 1024 muestras: para empezar a
 * mitad se saltan frames sin decodificarlos y se decodifica y tira el de antes del punto (el AAC solapa frames).
 */
final class AdtsDecoder implements AudioDecoder {
    private final InputStream in;
    private final ADTSDemultiplexer adts;
    private final Decoder decoder;
    private final SampleBuffer buf = new SampleBuffer();
    private final int rate, channels;
    private byte[] pending;

    AdtsDecoder(Path file, long startMs) throws IOException {
        in = new BufferedInputStream(Files.newInputStream(file), 1 << 16);
        try {
            adts = new ADTSDemultiplexer(in);
            decoder = new Decoder(adts.getDecoderSpecificInfo());
            buf.setBigEndian(false);
            long skip = startMs * adts.getSampleFrequency() / 1000 / 1024;
            for (long i = 0; i < skip - 1; i++) if (frame() == null) break;
            if (skip > 0) decode(frame()); // el de antes: se tira
            byte[] first = null;
            for (int tries = 0; first == null && tries < 64; tries++) {
                byte[] f = frame();
                if (f == null) break;
                first = decode(f);
            }
            if (first == null) throw new IOException("No hay audio AAC que se pueda leer");
            pending = first;
            rate = buf.getSampleRate();
            channels = Math.min(2, buf.getChannels());
        } catch (IOException | RuntimeException e) {
            in.close();
            throw e instanceof IOException io ? io : new IOException("AAC dañado", e);
        }
    }

    private byte[] frame() {
        try {
            return adts.readNextFrame();
        } catch (IOException | RuntimeException e) {
            return null; // se acabó
        }
    }

    private byte[] decode(byte[] f) {
        if (f == null) return null;
        try {
            decoder.decodeFrame(f, buf);
        } catch (Exception e) {
            return null;
        }
        byte[] pcm = buf.getData();
        return buf.getChannels() <= 2 ? pcm.clone() : null; // más de 2 canales en .aac suelto: muy raro, se salta
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
        if (pending != null) {
            byte[] b = pending;
            pending = null;
            return b;
        }
        for (int tries = 0; tries < 64; tries++) {
            byte[] f = frame();
            if (f == null) return null;
            byte[] b = decode(f);
            if (b != null && b.length > 0) return b;
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    /** Lo que dura (ms): cuenta los frames (1024 muestras cada uno) sin decodificarlos. */
    static long durationMs(Path file) {
        try (InputStream s = new BufferedInputStream(Files.newInputStream(file), 1 << 16)) {
            ADTSDemultiplexer d = new ADTSDemultiplexer(s);
            int rate = Math.max(1, d.getSampleFrequency());
            long n = 0;
            try {
                while (true) {
                    d.readNextFrame();
                    n++;
                }
            } catch (IOException | RuntimeException end) {
                return n * 1024 * 1000 / rate;
            }
        } catch (Exception e) {
            return 0;
        }
    }
}
