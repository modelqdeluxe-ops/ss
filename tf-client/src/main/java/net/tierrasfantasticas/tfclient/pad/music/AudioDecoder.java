/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
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
            // todos los MP3 (MPEG-1, 2 y 2.5, de 8 a 320 kbps, CBR o VBR) con JLayer: suena igual que el original (JavaMP3,
            // el de antes, distorsionaba los de 128 kbps y se apartaba en los de 320)
            case MP3 -> new JLayerDecoder(file, startMs);
            case MP4 -> new AacDecoder(file, startMs); // MP4 / M4A (AAC), también con vídeo o fragmentado
            case AAC -> new AdtsDecoder(file, startMs); // .aac suelto (ADTS)
            case OGG -> new OggDecoder(file, startMs);
            case WAV -> new WavDecoder(file, startMs);
            default -> throw new IOException("Formato de audio no soportado: " + file.getFileName());
        };
    }

    /** Los formatos que se reconocen por sus primeros bytes. */
    enum Kind {
        MP3, MP4, AAC, OGG, WAV, UNKNOWN;

        /** La extensión con la que se guarda en la biblioteca. */
        public String ext() {
            return switch (this) {
                case MP3 -> ".mp3";
                case MP4 -> ".m4a";
                case AAC -> ".aac";
                case OGG -> ".ogg";
                default -> ".wav";
            };
        }
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

        /**
         * MP4/M4A (una caja ftyp, moov, mdat, free o wide al principio), AAC suelto (cabecera ADTS: 0xFFF y capa 0),
         * MP3 (con etiqueta ID3 o un frame directamente), OGG («OggS») o WAV («RIFF....WAVE»).
         */
        public static Kind sniff(byte[] head, int n) {
            if (n >= 8) {
                String box = new String(head, 4, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
                if (box.equals("ftyp") || box.equals("moov") || box.equals("mdat") || box.equals("free") || box.equals("wide"))
                    return Kind.MP4;
            }
            if (n >= 2 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xF6) == 0xF0) return Kind.AAC;
            if (n >= 3 && head[0] == 'I' && head[1] == 'D' && head[2] == '3') return Kind.MP3;
            if (n >= 2 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0) return Kind.MP3;
            if (n >= 4 && head[0] == 'O' && head[1] == 'g' && head[2] == 'g' && head[3] == 'S') return Kind.OGG;
            if (n >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                    && head[8] == 'W' && head[9] == 'A' && head[10] == 'V' && head[11] == 'E') return Kind.WAV;
            return Kind.UNKNOWN;
        }
    }
}
