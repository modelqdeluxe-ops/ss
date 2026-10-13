package net.tierrasfantasticas.tfclient.pad.music;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * La pista de audio AAC de un MP4 / M4A (1.3.64): sin importar si lleva vídeo (se coge solo el audio), si es
 * «fragmentado» (moof/trun, como los de muchos conversores y de YouTube) o si el índice (moov) va al final. Lee las
 * cajas (boxes) con un RandomAccessFile: dónde está y cuánto ocupa cada frame de AAC, su momento, la configuración del
 * decodificador (AudioSpecificConfig, del esds), lo que se recorta al principio (la lista de edición: el retardo del
 * codificador) y las etiquetas de iTunes (título, artista y portada). Lo decodifica {@link AacDecoder} con JAAD.
 */
final class Mp4Audio {
    /** Lo que se lee como mucho de una caja con índices (moov, moof): de sobra para horas de audio. */
    private static final long MAX_INDEX = 64L << 20;

    byte[] config;
    int timescale;
    /** Muestras (a timescale) que se recortan al principio (lista de edición). */
    long priming;
    String title = "", artist = "";
    byte[] cover;

    private int trackId = -1;
    private int defDuration, defSize;
    private long[] offsets = new long[1024], times = new long[1024];
    private int[] sizes = new int[1024];
    private int count;
    private long end;

    private Mp4Audio() {}

    int frames() {
        return count;
    }

    long offset(int i) {
        return offsets[i];
    }

    int size(int i) {
        return sizes[i];
    }

    /** Cuándo empieza el frame i, en unidades de timescale. */
    long time(int i) {
        return times[i];
    }

    /** Lo que dura en ms (sin el recorte del principio). */
    long durationMs() {
        return timescale <= 0 ? 0 : Math.max(0, (end - priming) * 1000 / timescale);
    }

    /** El primer frame que suena en el momento t (unidades de timescale). */
    int frameAt(long t) {
        int i = Arrays.binarySearch(times, 0, count, t);
        if (i < 0) i = Math.max(0, -i - 2);
        return Math.min(i, Math.max(0, count - 1));
    }

    private void add(long offset, int size, long time) {
        if (count == offsets.length) {
            offsets = Arrays.copyOf(offsets, count * 2);
            times = Arrays.copyOf(times, count * 2);
            sizes = Arrays.copyOf(sizes, count * 2);
        }
        offsets[count] = offset;
        sizes[count] = size;
        times[count] = time;
        count++;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Lectura
    // ---------------------------------------------------------------------------------------------------------------

    /** Lee el MP4. Lanza IOException si no es un MP4 o no tiene audio AAC. */
    static Mp4Audio read(Path file) throws IOException {
        Mp4Audio m = new Mp4Audio();
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long len = raf.length(), pos = 0;
            boolean moov = false;
            while (pos + 8 <= len) {
                raf.seek(pos);
                long size = raf.readInt() & 0xFFFFFFFFL;
                int type = raf.readInt();
                int head = 8;
                if (size == 1) {
                    size = raf.readLong();
                    head = 16;
                } else if (size == 0) {
                    size = len - pos;
                }
                if (size < head || pos + size > len + 8) break; // roto: hasta aquí
                if (type == box("moov") || type == box("moof")) {
                    if (size - head > MAX_INDEX) throw new IOException("Índice del MP4 demasiado grande");
                    byte[] body = new byte[(int) Math.min(size - head, len - pos - head)];
                    raf.readFully(body);
                    ByteBuffer b = ByteBuffer.wrap(body);
                    if (type == box("moov")) {
                        m.moov(b);
                        moov = true;
                    } else if (moov) {
                        m.moof(b, pos);
                    }
                }
                pos += size;
            }
        }
        if (m.config == null || m.timescale <= 0) throw new IOException("El MP4 no tiene audio AAC");
        if (m.count == 0) throw new IOException("El MP4 no tiene audio");
        if (m.end == 0) m.end = m.times[m.count - 1];
        return m;
    }

    static int box(String s) {
        return s.charAt(0) << 24 | s.charAt(1) << 16 | s.charAt(2) << 8 | s.charAt(3);
    }

    /** Recorre las cajas hijas de b (de su posición a su límite) y llama a f con el tipo y el cuerpo de cada una. */
    private interface Visit {
        void box(int type, ByteBuffer body);
    }

    private static void children(ByteBuffer b, Visit f) {
        while (b.remaining() >= 8) {
            int start = b.position();
            long size = b.getInt() & 0xFFFFFFFFL;
            int type = b.getInt();
            int head = 8;
            if (size == 1) {
                if (b.remaining() < 8) return;
                size = b.getLong();
                head = 16;
            } else if (size == 0) {
                size = b.limit() - start;
            }
            if (size < head || start + size > b.limit()) return;
            ByteBuffer body = b.duplicate();
            body.position(start + head).limit((int) (start + size));
            f.box(type, body.slice());
            b.position((int) (start + size));
        }
    }

    // ---- moov --------------------------------------------------------------------------------------------------------

    private void moov(ByteBuffer b) {
        children(b, (type, body) -> {
            if (type == box("trak") && config == null) trak(body);
            else if (type == box("mvex")) children(body, (t2, b2) -> {
                if (t2 == box("trex")) trex(b2);
            });
            else if (type == box("udta")) udta(body);
        });
        // las etiquetas también pueden ir en moov/meta (sin udta)
        b.rewind();
        children(b, (type, body) -> {
            if (type == box("meta") && title.isEmpty()) meta(body);
        });
    }

    /** Una pista: solo vale la de sonido con AAC (mp4a con esds); la primera que haya. */
    private void trak(ByteBuffer b) {
        int[] id = {-1};
        boolean[] sound = {false};
        long[] media = {-1};
        ByteBuffer[] stbl = {null};
        int[] scale = {0};
        children(b, (type, body) -> {
            if (type == box("tkhd")) {
                int v = body.get(0);
                id[0] = body.getInt(v == 1 ? 4 + 16 : 4 + 8);
            } else if (type == box("edts")) {
                children(body, (t2, b2) -> {
                    if (t2 != box("elst")) return;
                    int v = b2.get(0);
                    int n = b2.getInt(4);
                    for (int i = 0, p = 8; i < n && p + (v == 1 ? 16 : 8) <= b2.limit(); i++, p += v == 1 ? 20 : 12) {
                        long mt = v == 1 ? b2.getLong(p + 8) : b2.getInt(p + 4);
                        if (mt >= 0) {
                            media[0] = mt;
                            break;
                        }
                    }
                });
            } else if (type == box("mdia")) {
                children(body, (t2, b2) -> {
                    if (t2 == box("mdhd")) {
                        int v = b2.get(0);
                        scale[0] = b2.getInt(v == 1 ? 4 + 16 : 4 + 8);
                    } else if (t2 == box("hdlr")) {
                        sound[0] = b2.getInt(8) == box("soun");
                    } else if (t2 == box("minf")) {
                        children(b2, (t3, b3) -> {
                            if (t3 == box("stbl")) stbl[0] = b3;
                        });
                    }
                });
            }
        });
        if (!sound[0] || stbl[0] == null || scale[0] <= 0) return;
        byte[] cfg = stsd(stbl[0].duplicate());
        if (cfg == null) return;
        config = cfg;
        timescale = scale[0];
        trackId = id[0];
        priming = Math.max(0, media[0]);
        sampleTable(stbl[0].duplicate());
    }

    /** La configuración del AAC (AudioSpecificConfig) del primer mp4a del stsd; null si no es AAC. */
    private static byte[] stsd(ByteBuffer stbl) {
        byte[][] out = {null};
        children(stbl, (type, body) -> {
            if (type != box("stsd") || body.remaining() < 8) return;
            body.position(8); // versión, flags y número de entradas
            children(body.slice(), (t2, entry) -> {
                if (out[0] != null || t2 != box("mp4a") || entry.remaining() < 28) return;
                int version = entry.getShort(8) & 0xFFFF; // QuickTime: 0, 1 (+16 bytes) o 2 (+36)
                int skip = 28 + (version == 1 ? 16 : version == 2 ? 36 : 0);
                if (entry.remaining() <= skip) return;
                entry.position(skip);
                children(entry.slice(), (t3, b3) -> {
                    if (t3 == box("esds") && out[0] == null) out[0] = esds(b3);
                    else if (t3 == box("wave") && out[0] == null) children(b3, (t4, b4) -> { // QuickTime: mp4a/wave/esds
                        if (t4 == box("esds") && out[0] == null) out[0] = esds(b4);
                    });
                });
            });
        });
        return out[0];
    }

    /** El esds: ES_Descriptor (3) › DecoderConfigDescriptor (4, AAC = 0x40) › DecoderSpecificInfo (5). */
    private static byte[] esds(ByteBuffer b) {
        try {
            b.position(4);
            while (b.remaining() > 2) {
                int tag = b.get() & 0xFF;
                int len = descLength(b);
                if (tag == 3) {
                    b.getShort(); // ES_ID
                    int flags = b.get() & 0xFF;
                    if ((flags & 0x80) != 0) b.getShort();
                    if ((flags & 0x40) != 0) b.position(b.position() + (b.get() & 0xFF));
                    if ((flags & 0x20) != 0) b.getShort();
                } else if (tag == 4) {
                    int object = b.get() & 0xFF;
                    if (object != 0x40 && object != 0x66 && object != 0x67 && object != 0x68) return null; // no es AAC
                    b.position(b.position() + 12);
                } else if (tag == 5) {
                    byte[] cfg = new byte[Math.min(len, b.remaining())];
                    b.get(cfg);
                    return cfg.length >= 2 ? cfg : null;
                } else {
                    b.position(b.position() + len);
                }
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    private static int descLength(ByteBuffer b) {
        int len = 0;
        for (int i = 0; i < 4; i++) {
            int c = b.get() & 0xFF;
            len = len << 7 | c & 0x7F;
            if ((c & 0x80) == 0) break;
        }
        return len;
    }

    /** stts (duraciones), stsz (tamaños), stsc (frames por trozo) y stco/co64 (dónde empieza cada trozo). */
    private void sampleTable(ByteBuffer stbl) {
        ByteBuffer[] box = new ByteBuffer[5];
        children(stbl, (type, body) -> {
            if (type == box("stts")) box[0] = body;
            else if (type == box("stsz")) box[1] = body;
            else if (type == box("stsc")) box[2] = body;
            else if (type == box("stco")) box[3] = body;
            else if (type == box("co64")) box[4] = body;
        });
        ByteBuffer stsz = box[1], stsc = box[2];
        if (stsz == null || stsc == null || (box[3] == null && box[4] == null)) return; // fragmentado: van en moof
        int fixed = stsz.getInt(4), n = stsz.getInt(8);
        boolean co64 = box[3] == null;
        ByteBuffer co = co64 ? box[4] : box[3];
        int chunks = co.getInt(4);
        int entries = stsc.getInt(4);
        // los momentos, de stts
        long[] t = new long[n + 1];
        if (box[0] != null) {
            int e = box[0].getInt(4), k = 0;
            long now = 0;
            for (int i = 0; i < e && k < n; i++) {
                int c = box[0].getInt(8 + i * 8), d = box[0].getInt(12 + i * 8);
                for (int j = 0; j < c && k < n; j++) {
                    t[k++] = now;
                    now += d;
                }
            }
            for (; k <= n; k++) t[k] = now;
            end = now;
        }
        int sample = 0;
        for (int i = 0; i < entries && sample < n; i++) {
            int first = stsc.getInt(8 + i * 12), per = stsc.getInt(12 + i * 12);
            int last = i + 1 < entries ? stsc.getInt(8 + (i + 1) * 12) - 1 : chunks;
            for (int c = first; c <= last && c <= chunks && sample < n; c++) {
                long off = co64 ? co.getLong(8 + (c - 1) * 8) : co.getInt(8 + (c - 1) * 4) & 0xFFFFFFFFL;
                for (int s = 0; s < per && sample < n; s++) {
                    int size = fixed != 0 ? fixed : stsz.getInt(12 + sample * 4);
                    add(off, size, t[sample]);
                    off += size;
                    sample++;
                }
            }
        }
    }

    private void trex(ByteBuffer b) {
        if (b.remaining() < 24) return;
        int id = b.getInt(4);
        if (trackId >= 0 && id != trackId) return;
        defDuration = b.getInt(12);
        defSize = b.getInt(16);
    }

    // ---- fragmentos (moof) ---------------------------------------------------------------------------------------

    private long fragTime = -1;

    /** Un moof: sus traf de nuestra pista, con sus trun (cuántos frames, dónde, cuánto ocupan y duran). */
    private void moof(ByteBuffer b, long moofStart) {
        children(b, (type, traf) -> {
            if (type != box("traf")) return;
            long[] base = {moofStart};
            int[] dur = {defDuration}, size = {defSize};
            boolean[] ours = {false};
            long[] decode = {-1};
            children(traf.duplicate(), (t2, b2) -> {
                if (t2 == box("tfhd")) {
                    int flags = b2.getInt(0) & 0xFFFFFF;
                    int id = b2.getInt(4);
                    ours[0] = trackId < 0 || id == trackId;
                    int p = 8;
                    if ((flags & 0x1) != 0) {
                        base[0] = b2.getLong(p);
                        p += 8;
                    }
                    if ((flags & 0x2) != 0) p += 4;
                    if ((flags & 0x8) != 0) {
                        dur[0] = b2.getInt(p);
                        p += 4;
                    }
                    if ((flags & 0x10) != 0) size[0] = b2.getInt(p);
                } else if (t2 == box("tfdt")) {
                    decode[0] = b2.get(0) == 1 ? b2.getLong(4) : b2.getInt(4) & 0xFFFFFFFFL;
                }
            });
            if (!ours[0]) return;
            if (decode[0] >= 0) fragTime = decode[0];
            if (fragTime < 0) fragTime = count == 0 ? 0 : end;
            long[] data = {base[0]};
            children(traf, (t2, run) -> {
                if (t2 != box("trun")) return;
                int flags = run.getInt(0) & 0xFFFFFF;
                int n = run.getInt(4);
                int p = 8;
                if ((flags & 0x1) != 0) {
                    data[0] = base[0] + run.getInt(p);
                    p += 4;
                }
                if ((flags & 0x4) != 0) p += 4;
                for (int i = 0; i < n && p <= run.limit(); i++) {
                    int d = dur[0], s = size[0];
                    if ((flags & 0x100) != 0) {
                        d = run.getInt(p);
                        p += 4;
                    }
                    if ((flags & 0x200) != 0) {
                        s = run.getInt(p);
                        p += 4;
                    }
                    if ((flags & 0x400) != 0) p += 4;
                    if ((flags & 0x800) != 0) p += 4;
                    add(data[0], s, fragTime);
                    data[0] += s;
                    fragTime += d;
                }
                end = fragTime;
            });
        });
    }

    // ---- etiquetas (iTunes: udta › meta › ilst) ----------------------------------------------------------------------

    private void udta(ByteBuffer b) {
        children(b, (type, body) -> {
            if (type == box("meta")) meta(body);
        });
    }

    private void meta(ByteBuffer b) {
        // meta es una FullBox (versión y flags) salvo en algunos QuickTime, que van directos a las hijas
        if (b.remaining() >= 12 && b.getInt(4 + 4) != box("hdlr") && b.getInt(4) == box("hdlr")) b.position(0);
        else if (b.remaining() >= 4) b.position(4);
        children(b.slice(), (type, body) -> {
            if (type != box("ilst")) return;
            children(body, (t2, item) -> children(item, (t3, data) -> {
                if (t3 != box("data") || data.remaining() < 8) return;
                byte[] v = new byte[data.remaining() - 8];
                data.position(8);
                data.get(v);
                if (t2 == box("©nam") && title.isEmpty()) title = new String(v, StandardCharsets.UTF_8).trim();
                else if ((t2 == box("©ART") || t2 == box("aART")) && artist.isEmpty()) artist = new String(v, StandardCharsets.UTF_8).trim();
                else if (t2 == box("covr") && cover == null && v.length > 8) cover = v;
            }));
        });
    }
}
