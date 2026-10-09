package net.tierrasfantasticas.tfclient.pad.music;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.tierrasfantasticas.tfclient.pad.music.mp3.Decoder;

/**
 * MP3 con el decodificador de JavaMP3 (incluido en pad/music/mp3, licencia MIT). Para empezar a mitad de canción se
 * busca el frame con {@link Mp3Frames} y se decodifican y tiran los dos anteriores (la capa III los necesita).
 */
final class Mp3Decoder implements AudioDecoder {
    private final InputStream in;
    private final Decoder.SoundData data;
    private byte[] pending;

    Mp3Decoder(Path file, long startMs) throws IOException {
        long offset;
        int warmup = 0;
        if (startMs > 0) {
            Mp3Frames.Scan scan = Mp3Frames.scan(file, startMs);
            offset = scan.offset();
            warmup = scan.warmup();
        } else {
            byte[] head = new byte[10];
            try (InputStream h = Files.newInputStream(file)) {
                int n = h.readNBytes(head, 0, 10);
                offset = n == 10 ? Mp3Frames.id3Size(head) : 0;
            }
        }
        in = new BufferedInputStream(Files.newInputStream(file), 1 << 16);
        try {
            in.skipNBytes(offset);
            Decoder.SoundData d = Decoder.init(in);
            if (d == null) throw new IOException("No hay audio MP3 en el archivo");
            data = d;
            pending = d.samplesBuffer;
            for (; warmup > 0; warmup--) {
                pending = null;
                if (warmup > 1 && !Decoder.decodeFrame(d)) break;
            }
        } catch (IOException | RuntimeException e) {
            in.close();
            throw e instanceof IOException io ? io : new IOException("MP3 dañado", e);
        }
    }

    @Override
    public int sampleRate() {
        return data.frequency;
    }

    @Override
    public int channels() {
        return data.stereo == 1 ? 2 : 1;
    }

    @Override
    public byte[] next() throws IOException {
        if (pending != null) {
            byte[] b = pending;
            pending = null;
            return b;
        }
        try {
            // el decodificador puede reutilizar el array: quien llama lo copia antes de pedir el siguiente
            return Decoder.decodeFrame(data) ? data.samplesBuffer : null;
        } catch (RuntimeException e) {
            return null; // un final dañado: se acaba ahí
        }
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
