/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad.music;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import net.sourceforge.jaad.aac.Decoder;
import net.sourceforge.jaad.aac.SampleBuffer;

/**
 * MP4 / M4A con audio AAC (1.3.64): el contenedor lo lee {@link Mp4Audio} (también con vídeo o fragmentado) y cada
 * frame lo decodifica JAAD (net.sourceforge.jaad.aac, dominio público; copiado con un arreglo, ver
 * META-INF/licenses/jaad-PUBLIC-DOMAIN.txt): AAC-LC y HE-AAC (v1 y v2). Medido contra ffmpeg con M4A de 256 y 128
 * kbps, a 44,1 y 48 kHz y MP4 con vídeo: 90 dB de SNR (la misma señal). Al empezar se recorta el retardo del codificador
 * (la lista de edición del MP4); para empezar a mitad se decodifica y tira el frame anterior (el AAC solapa frames). Más
 * de 2 canales (5.1) se mezclan a estéreo.
 */
final class AacDecoder implements AudioDecoder {
    private final RandomAccessFile raf;
    private final Mp4Audio mp4;
    private final Decoder decoder;
    private final SampleBuffer buf = new SampleBuffer();
    private int frame;
    private int rate, channels;
    private byte[] pending;

    AacDecoder(Path file, long startMs) throws IOException {
        mp4 = Mp4Audio.read(file);
        raf = new RandomAccessFile(file.toFile(), "r");
        try {
            decoder = new Decoder(mp4.config);
            buf.setBigEndian(false);
            long target = mp4.priming + startMs * mp4.timescale / 1000;
            frame = startMs > 0 ? mp4.frameAt(target) : 0;
            if (frame > 0) decode(frame - 1); // el frame de antes: el AAC solapa la mitad de cada uno con el siguiente
            byte[] first = null;
            while (first == null && frame < mp4.frames()) first = decode(frame++);
            if (first == null) throw new IOException("No hay audio AAC que se pueda leer");
            rate = buf.getSampleRate();
            channels = Math.min(2, buf.getChannels());
            // lo que sobra del primer frame: el retardo del codificador (al empezar) o hasta el punto pedido
            long skipUnits = startMs > 0 ? target - mp4.time(frame - 1) : mp4.priming;
            long skip = Math.max(0, skipUnits) * rate / Math.max(1, mp4.timescale) * 2L * channels;
            pending = skip >= first.length ? null : skip > 0 ? java.util.Arrays.copyOfRange(first, (int) skip, first.length) : first;
            if (pending != null && pending.length == 0) pending = null;
        } catch (IOException | RuntimeException e) {
            raf.close();
            throw e instanceof IOException io ? io : new IOException("MP4 dañado", e);
        }
    }

    /** Decodifica el frame i: PCM de 16 bits little endian en estéreo (o mono); null si está dañado. */
    private byte[] decode(int i) throws IOException {
        int size = mp4.size(i);
        if (size <= 0 || size > 1 << 20) return null;
        byte[] data = new byte[size];
        raf.seek(mp4.offset(i));
        raf.readFully(data);
        try {
            decoder.decodeFrame(data, buf);
        } catch (Exception e) {
            return null; // un frame dañado: se salta
        }
        byte[] pcm = buf.getData();
        int ch = buf.getChannels();
        return ch <= 2 ? pcm.clone() : stereo(pcm, ch);
    }

    /** 5.1 u otros a estéreo: izquierda = L + 0,7·C + 0,7·Ls; derecha = R + 0,7·C + 0,7·Rs (orden del AAC: C L R Ls Rs LFE). */
    private static byte[] stereo(byte[] pcm, int ch) {
        int frames = pcm.length / (2 * ch);
        byte[] out = new byte[frames * 4];
        for (int f = 0; f < frames; f++) {
            float[] s = new float[ch];
            for (int c = 0; c < ch; c++) s[c] = (short) ((pcm[(f * ch + c) * 2] & 0xFF) | (pcm[(f * ch + c) * 2 + 1] << 8));
            float l, r;
            if (ch >= 5) {
                l = s[1] + 0.7F * s[0] + 0.7F * s[3];
                r = s[2] + 0.7F * s[0] + 0.7F * s[4];
                l *= 0.5F;
                r *= 0.5F;
            } else {
                l = s[0];
                r = s[1];
            }
            int li = Math.max(-32768, Math.min(32767, Math.round(l))), ri = Math.max(-32768, Math.min(32767, Math.round(r)));
            out[f * 4] = (byte) li;
            out[f * 4 + 1] = (byte) (li >> 8);
            out[f * 4 + 2] = (byte) ri;
            out[f * 4 + 3] = (byte) (ri >> 8);
        }
        return out;
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
        while (frame < mp4.frames()) {
            byte[] b = decode(frame++);
            if (b != null && b.length > 0) return b;
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }

    /** Lo que dura (ms), del índice del MP4: no hace falta decodificar. */
    static long durationMs(Path file) {
        try {
            return Mp4Audio.read(file).durationMs();
        } catch (Exception e) {
            return 0;
        }
    }
}
