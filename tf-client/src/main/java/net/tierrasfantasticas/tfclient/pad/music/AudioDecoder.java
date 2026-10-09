package net.tierrasfantasticas.tfclient.pad.music;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lo que convierte un archivo de música en sonido: PCM de 16 bits con signo, little endian, canales intercalados.
 * {@link #next()} da el siguiente trozo (un frame de MP3, unos miles de muestras de OGG o WAV) o null al acabar.
 */
public interface AudioDecoder extends Closeable {
    int sampleRate();

    int channels();

    /** El siguiente trozo de audio (nunca vacío) o null si se acabó la canción. */
    byte[] next() throws IOException;

    /** Abre el archivo según su formato y empieza en el milisegundo startMs (0 = desde el principio). */
    static AudioDecoder open(Path file, long startMs) throws IOException {
        return switch (AudioFormatSniffer.sniff(file)) {
            case MP3 -> new Mp3Decoder(file, startMs);
            case OGG -> new OggDecoder(file, startMs);
            case WAV -> new WavDecoder(file, startMs);
            default -> throw new IOException("Formato de audio no soportado: " + file.getFileName());
        };
    }

    /** Los formatos que se reconocen por sus primeros bytes. */
    enum Kind {
        MP3, OGG, WAV, UNKNOWN
    }

    final class AudioFormatSniffer {
        private AudioFormatSniffer() {}

        static Kind sniff(Path file) throws IOException {
            byte[] head = new byte[12];
            try (var in = Files.newInputStream(file)) {
                int n = in.readNBytes(head, 0, head.length);
                return sniff(head, n);
            }
        }

        /** MP3 (con etiqueta ID3 o un frame directamente), OGG («OggS») o WAV («RIFF....WAVE»). */
        public static Kind sniff(byte[] head, int n) {
            if (n >= 3 && head[0] == 'I' && head[1] == 'D' && head[2] == '3') return Kind.MP3;
            if (n >= 2 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0) return Kind.MP3;
            if (n >= 4 && head[0] == 'O' && head[1] == 'g' && head[2] == 'g' && head[3] == 'S') return Kind.OGG;
            if (n >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                    && head[8] == 'W' && head[9] == 'A' && head[10] == 'V' && head[11] == 'E') return Kind.WAV;
            return Kind.UNKNOWN;
        }
    }
}
