package net.tierrasfantasticas.tfclient.pad.music;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;

/**
 * Título, artista y portada que trae la canción: etiquetas ID3 (v2.2, v2.3, v2.4 y la v1 del final) en los MP3 y
 * comentarios Vorbis (TITLE, ARTIST, METADATA_BLOCK_PICTURE) en los OGG. Lo que falte queda vacío / null.
 */
public final class MusicTags {
    /** cover: los bytes de la imagen tal cual (JPEG o PNG) o null. */
    public record Tags(String title, String artist, byte[] cover) {
        static final Tags NONE = new Tags("", "", null);
    }

    private static final int MAX_TAG = 16 << 20;

    private MusicTags() {}

    public static Tags read(Path file) {
        try {
            return switch (AudioDecoder.AudioFormatSniffer.sniff(file)) {
                case MP3 -> id3(file);
                case OGG -> vorbis(file);
                default -> Tags.NONE;
            };
        } catch (Exception e) {
            return Tags.NONE;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // ID3
    // ---------------------------------------------------------------------------------------------------------------

    private static Tags id3(Path file) throws IOException {
        String title = "", artist = "";
        byte[] cover = null;
        int coverType = -1;
        try (InputStream in = Files.newInputStream(file)) {
            byte[] head = in.readNBytes(10);
            if (head.length == 10 && head[0] == 'I' && head[1] == 'D' && head[2] == '3') {
                int version = head[3];
                int flags = head[5] & 0xFF;
                int size = syncsafe(head, 6);
                if (size > 0 && size <= MAX_TAG) {
                    byte[] tag = in.readNBytes(size);
                    if (version < 4 && (flags & 0x80) != 0) tag = unsync(tag);
                    int p = 0;
                    if ((flags & 0x40) != 0 && version >= 3) { // cabecera extendida
                        int ext = version == 4 ? syncsafe(tag, 0) : int32(tag, 0) + 4;
                        p = Math.max(0, Math.min(tag.length, ext));
                    }
                    int idLen = version == 2 ? 3 : 4, headLen = version == 2 ? 6 : 10;
                    while (p + headLen <= tag.length) {
                        String id = new String(tag, p, idLen, StandardCharsets.ISO_8859_1);
                        if (id.charAt(0) == 0) break;
                        int fsize = version == 2 ? (tag[p + 3] & 0xFF) << 16 | (tag[p + 4] & 0xFF) << 8 | (tag[p + 5] & 0xFF)
                                : version == 4 ? syncsafe(tag, p + 4) : int32(tag, p + 4);
                        int fflags = version == 2 ? 0 : (tag[p + 9] & 0xFF);
                        int start = p + headLen;
                        if (fsize <= 0 || start + fsize > tag.length) break;
                        byte[] body = java.util.Arrays.copyOfRange(tag, start, start + fsize);
                        if (version == 4 && (fflags & 0x02) != 0) body = unsync(body);
                        if (version == 4 && (fflags & 0x01) != 0 && body.length >= 4) body = java.util.Arrays.copyOfRange(body, 4, body.length);
                        switch (id) {
                            case "TIT2", "TT2" -> title = text(body);
                            case "TPE1", "TP1" -> artist = text(body);
                            case "TPE2", "TP2" -> {
                                if (artist.isEmpty()) artist = text(body);
                            }
                            case "APIC", "PIC" -> {
                                Object[] pic = picture(body, id.equals("PIC"));
                                if (pic != null && (cover == null || ((int) pic[0] == 3 && coverType != 3))) {
                                    cover = (byte[]) pic[1];
                                    coverType = (int) pic[0];
                                }
                            }
                            default -> {
                            }
                        }
                        p = start + fsize;
                    }
                }
            }
        }
        if (title.isEmpty() || artist.isEmpty()) { // ID3v1 al final
            try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
                if (raf.length() > 128) {
                    raf.seek(raf.length() - 128);
                    byte[] v1 = new byte[128];
                    raf.readFully(v1);
                    if (v1[0] == 'T' && v1[1] == 'A' && v1[2] == 'G') {
                        if (title.isEmpty()) title = clean(new String(v1, 3, 30, StandardCharsets.ISO_8859_1));
                        if (artist.isEmpty()) artist = clean(new String(v1, 33, 30, StandardCharsets.ISO_8859_1));
                    }
                }
            }
        }
        return new Tags(title, artist, cover);
    }

    private static int syncsafe(byte[] b, int o) {
        return (b[o] & 0x7F) << 21 | (b[o + 1] & 0x7F) << 14 | (b[o + 2] & 0x7F) << 7 | (b[o + 3] & 0x7F);
    }

    private static int int32(byte[] b, int o) {
        return (b[o] & 0xFF) << 24 | (b[o + 1] & 0xFF) << 16 | (b[o + 2] & 0xFF) << 8 | (b[o + 3] & 0xFF);
    }

    /** Quita la «desincronización» de ID3 (0xFF 0x00 → 0xFF). */
    private static byte[] unsync(byte[] b) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(b.length);
        for (int i = 0; i < b.length; i++) {
            out.write(b[i]);
            if ((b[i] & 0xFF) == 0xFF && i + 1 < b.length && b[i + 1] == 0) i++;
        }
        return out.toByteArray();
    }

    private static Charset charset(int enc) {
        return switch (enc) {
            case 1 -> StandardCharsets.UTF_16;
            case 2 -> StandardCharsets.UTF_16BE;
            case 3 -> StandardCharsets.UTF_8;
            default -> StandardCharsets.ISO_8859_1;
        };
    }

    private static String text(byte[] body) {
        if (body.length < 2) return "";
        return clean(new String(body, 1, body.length - 1, charset(body[0])));
    }

    /** [tipo, bytes] de un APIC/PIC; el tipo 3 es la portada. */
    private static Object[] picture(byte[] b, boolean v22) {
        if (b.length < 6) return null;
        int enc = b[0];
        int p = 1;
        if (v22) {
            p += 3;
        } else {
            while (p < b.length && b[p] != 0) p++;
            p++;
        }
        if (p >= b.length) return null;
        int type = b[p++] & 0xFF;
        boolean wide = enc == 1 || enc == 2;
        if (wide) {
            while (p + 1 < b.length && !(b[p] == 0 && b[p + 1] == 0)) p += 2;
            p += 2;
        } else {
            while (p < b.length && b[p] != 0) p++;
            p++;
        }
        if (p >= b.length) return null;
        return new Object[] {type, java.util.Arrays.copyOfRange(b, p, b.length)};
    }

    private static String clean(String s) {
        int end = s.indexOf('\0');
        if (end >= 0) s = s.substring(0, end);
        s = s.replace("﻿", "").replaceAll("[\\p{Cntrl}]", " ").trim();
        return s.length() > 80 ? s.substring(0, 80) : s;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Vorbis (OGG)
    // ---------------------------------------------------------------------------------------------------------------

    /** El segundo paquete del OGG (los comentarios), juntando las páginas que ocupe. */
    private static Tags vorbis(Path file) throws IOException {
        byte[] packet = secondPacket(file);
        if (packet == null || packet.length < 7 || packet[0] != 3) return Tags.NONE;
        int p = 7;
        int vendor = le32(packet, p);
        p += 4 + vendor;
        if (p + 4 > packet.length) return Tags.NONE;
        int count = le32(packet, p);
        p += 4;
        String title = "", artist = "";
        byte[] cover = null;
        for (int i = 0; i < count && p + 4 <= packet.length; i++) {
            int len = le32(packet, p);
            p += 4;
            if (len < 0 || p + len > packet.length) break;
            String c = new String(packet, p, len, StandardCharsets.UTF_8);
            p += len;
            int eq = c.indexOf('=');
            if (eq <= 0) continue;
            String key = c.substring(0, eq).toUpperCase(Locale.ROOT), value = c.substring(eq + 1);
            switch (key) {
                case "TITLE" -> title = clean(value);
                case "ARTIST" -> artist = clean(value);
                case "METADATA_BLOCK_PICTURE" -> {
                    if (cover == null) cover = flacPicture(value);
                }
                default -> {
                }
            }
        }
        return new Tags(title, artist, cover);
    }

    private static byte[] flacPicture(String base64) {
        try {
            byte[] b = Base64.getDecoder().decode(base64.trim());
            int p = 4;
            int mime = int32(b, p);
            p += 4 + mime;
            int desc = int32(b, p);
            p += 4 + desc + 16;
            int len = int32(b, p);
            p += 4;
            if (len <= 0 || p + len > b.length) return null;
            return java.util.Arrays.copyOfRange(b, p, p + len);
        } catch (Exception e) {
            return null;
        }
    }

    private static int le32(byte[] b, int o) {
        return (b[o] & 0xFF) | (b[o + 1] & 0xFF) << 8 | (b[o + 2] & 0xFF) << 16 | (b[o + 3] & 0xFF) << 24;
    }

    private static byte[] secondPacket(Path file) throws IOException {
        try (InputStream in = new java.io.BufferedInputStream(Files.newInputStream(file))) {
            ByteArrayOutputStream packet = new ByteArrayOutputStream();
            int packetIndex = 0;
            for (int page = 0; page < 512; page++) {
                byte[] h = in.readNBytes(27);
                if (h.length < 27 || h[0] != 'O' || h[1] != 'g' || h[2] != 'g' || h[3] != 'S') return null;
                int segments = h[26] & 0xFF;
                byte[] table = in.readNBytes(segments);
                for (int s = 0; s < segments; s++) {
                    int len = table[s] & 0xFF;
                    byte[] data = in.readNBytes(len);
                    if (packetIndex == 1) packet.write(data);
                    if (packet.size() > MAX_TAG) return null;
                    if (len < 255) { // fin de paquete
                        if (packetIndex == 1) return packet.toByteArray();
                        packetIndex++;
                    }
                }
            }
            return null;
        }
    }
}
